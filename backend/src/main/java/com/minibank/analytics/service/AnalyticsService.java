package com.minibank.analytics.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.repository.BankAccountRepository;
import com.minibank.analytics.dto.InsightResponse;
import com.minibank.analytics.dto.MonthlySummary;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.money.Money;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spending analytics, computed from settled transfers and nothing else.
 *
 * <h2>The rule this class is built around</h2>
 *
 * <p>Every number returned here is arithmetic over rows that exist. There is no estimation, no
 * projection, no filling of gaps and no "typical customer" baseline. When the data cannot support a
 * statement — a month with no history to compare against, a category with nothing in it — the
 * statement is not made. An analytics screen earns its place by being checkable; one that guesses
 * teaches the customer to ignore it.
 *
 * <h2>Which side is which</h2>
 *
 * <p>Direction is decided by account ownership, not by transfer type: money leaving to an account
 * the customer owns is not spending, it is movement. That single rule is what keeps a savings
 * contribution out of the spending total.
 */
@Service
public class AnalyticsService {

    /** Percentage move below which a month-on-month comparison is noise rather than news. */
    private static final int MEANINGFUL_CHANGE_PERCENT = 5;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TransferRepository transfers;
    private final BankAccountRepository accounts;
    private final Clock clock;

    public AnalyticsService(TransferRepository transfers, BankAccountRepository accounts, Clock clock) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * A month of the customer's money.
     *
     * @param zone the customer's timezone; a month boundary is a local concept, and computing it in
     *     UTC would move a late-evening payment on the 31st into the next month for anyone east of
     *     Greenwich
     */
    @Transactional(readOnly = true)
    public MonthlySummary summarise(UUID userId, YearMonth month, CurrencyCode currency, ZoneId zone) {
        Set<UUID> owned = ownedAccountIds(userId);
        if (owned.isEmpty()) {
            return empty(month, currency);
        }

        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Transfer> settled = transfers.findSettledForAnalytics(owned, currency, from, to);

        BigDecimal income = Money.zero();
        BigDecimal spending = Money.zero();
        BigDecimal internal = Money.zero();

        Map<TransactionCategory, BigDecimal> categoryTotals = new HashMap<>();
        Map<TransactionCategory, Integer> categoryCounts = new HashMap<>();
        Map<LocalDate, BigDecimal[]> byDay = new HashMap<>();
        Transfer largest = null;

        for (Transfer transfer : settled) {
            Direction direction = directionOf(transfer, owned);
            LocalDate day = transfer.getCompletedAt().atZone(zone).toLocalDate();
            BigDecimal[] slot = byDay.computeIfAbsent(day, ignored -> new BigDecimal[] {Money.zero(), Money.zero()});

            if (direction == Direction.OUTGOING) {
                spending = spending.add(transfer.getAmount());
                slot[0] = slot[0].add(transfer.getAmount());
                categoryTotals.merge(transfer.getCategory(), transfer.getAmount(), BigDecimal::add);
                categoryCounts.merge(transfer.getCategory(), 1, Integer::sum);
                if (largest == null || transfer.getAmount().compareTo(largest.getAmount()) > 0) {
                    largest = transfer;
                }
            } else if (direction == Direction.INCOMING) {
                income = income.add(transfer.getAmount());
                slot[1] = slot[1].add(transfer.getAmount());
            } else {
                internal = internal.add(transfer.getAmount());
            }
        }

        return new MonthlySummary(
                month.toString(),
                currency,
                income,
                spending,
                income.subtract(spending),
                internal,
                settled.size(),
                categoryBreakdown(categoryTotals, categoryCounts, spending),
                dailySeries(month, byDay),
                largestOf(largest, owned));
    }

    /**
     * Observations about the current month, each one checkable against the summary above it.
     *
     * <p>Returns fewer insights — or none — when the history is too thin to support them. That is
     * the intended behaviour, not a gap: a new customer with two weeks of data has nothing true to
     * say about their spending trend, and saying it anyway would be an invention.
     */
    @Transactional(readOnly = true)
    public List<InsightResponse> insights(UUID userId, CurrencyCode currency, ZoneId zone) {
        YearMonth thisMonth = YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        MonthlySummary current = summarise(userId, thisMonth, currency, zone);
        MonthlySummary previous = summarise(userId, thisMonth.minusMonths(1), currency, zone);

        List<InsightResponse> insights = new ArrayList<>();

        if (current.transactionCount() == 0) {
            return List.of();
        }

        // 1. The month's net position. Stated as the arithmetic it is.
        if (current.net().signum() > 0) {
            insights.add(InsightResponse.net("NET_POSITIVE", "POSITIVE", current.net(), current.spending()));
        } else if (current.net().signum() < 0) {
            insights.add(InsightResponse.net("NET_NEGATIVE", "ATTENTION", current.net().abs(), current.spending()));
        }

        // 2. Where the money actually went.
        current.byCategory().stream()
                .filter(total -> total.category() != null)
                .findFirst()
                .ifPresent(top -> insights.add(
                        InsightResponse.topCategory(top.category(), top.amount(), top.share())));

        // 3. Month on month, per category — only where both months have something
        //    to compare. A category with no history last month produces no
        //    comparison rather than an invented baseline.
        if (previous.transactionCount() > 0) {
            Map<TransactionCategory, BigDecimal> before = previous.byCategory().stream()
                    .filter(total -> total.category() != null)
                    .collect(Collectors.toMap(
                            MonthlySummary.CategoryTotal::category, MonthlySummary.CategoryTotal::amount));

            for (MonthlySummary.CategoryTotal now : current.byCategory()) {
                if (now.category() == null) {
                    continue;
                }
                BigDecimal then = before.get(now.category());
                if (then == null || then.signum() == 0) {
                    continue;
                }
                int change = percentChange(then, now.amount());
                if (Math.abs(change) < MEANINGFUL_CHANGE_PERCENT) {
                    continue;
                }
                insights.add(InsightResponse.change(now.category(), now.amount(), then, change));
                // Two comparisons is a summary; six is a spreadsheet nobody reads.
                if (insights.size() >= 5) {
                    break;
                }
            }
        }

        return List.copyOf(insights);
    }

    // -------------------------------------------------------------------------------------------

    private Set<UUID> ownedAccountIds(UUID userId) {
        return accounts.findAllByOwnerId(userId).stream()
                .map(BankAccount::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Direction directionOf(Transfer transfer, Set<UUID> owned) {
        boolean fromMine = owned.contains(transfer.getSourceAccount().getId());
        boolean toMine = owned.contains(transfer.getDestinationAccount().getId());
        if (fromMine && toMine) {
            return Direction.INTERNAL;
        }
        return fromMine ? Direction.OUTGOING : Direction.INCOMING;
    }

    private static List<MonthlySummary.CategoryTotal> categoryBreakdown(
            Map<TransactionCategory, BigDecimal> totals, Map<TransactionCategory, Integer> counts, BigDecimal spending) {

        return totals.entrySet().stream()
                .sorted(Map.Entry.<TransactionCategory, BigDecimal>comparingByValue().reversed())
                .map(entry -> new MonthlySummary.CategoryTotal(
                        entry.getKey(),
                        entry.getValue(),
                        share(entry.getValue(), spending),
                        counts.getOrDefault(entry.getKey(), 0)))
                .toList();
    }

    private static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /**
     * One point per day of the month, including the empty ones.
     *
     * <p>Emitting only the days with activity would leave the client to reconstruct the gaps, and a
     * chart drawn from a sparse series compresses quiet weeks into nothing and misreports the shape
     * of the month.
     */
    private static List<MonthlySummary.DailyPoint> dailySeries(YearMonth month, Map<LocalDate, BigDecimal[]> byDay) {
        Map<String, MonthlySummary.DailyPoint> ordered = new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            BigDecimal[] slot = byDay.getOrDefault(date, new BigDecimal[] {Money.zero(), Money.zero()});
            ordered.put(date.format(DAY), new MonthlySummary.DailyPoint(date.format(DAY), slot[0], slot[1]));
        }
        return List.copyOf(ordered.values());
    }

    private static MonthlySummary.LargestTransaction largestOf(Transfer largest, Set<UUID> owned) {
        return Optional.ofNullable(largest)
                .map(transfer -> new MonthlySummary.LargestTransaction(
                        transfer.getId(),
                        transfer.getReference(),
                        transfer.getDescription(),
                        transfer.getCategory(),
                        transfer.getAmount(),
                        owned.contains(transfer.getSourceAccount().getId())
                                ? transfer.getDestinationAccount().getAccountNumber()
                                : transfer.getSourceAccount().getAccountNumber(),
                        transfer.getCompletedAt()))
                .orElse(null);
    }

    /** Signed percentage change from {@code before} to {@code after}, rounded to a whole percent. */
    private static int percentChange(BigDecimal before, BigDecimal after) {
        return after.subtract(before)
                .multiply(BigDecimal.valueOf(100))
                .divide(before, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static MonthlySummary empty(YearMonth month, CurrencyCode currency) {
        return new MonthlySummary(
                month.toString(),
                currency,
                Money.zero(),
                Money.zero(),
                Money.zero(),
                Money.zero(),
                0,
                List.of(),
                dailySeries(month, Map.of()),
                null);
    }

    /** Which side of a movement the customer is on. */
    private enum Direction {
        OUTGOING,
        INCOMING,
        /** Between two accounts the customer owns. Neither income nor spending. */
        INTERNAL
    }
}
