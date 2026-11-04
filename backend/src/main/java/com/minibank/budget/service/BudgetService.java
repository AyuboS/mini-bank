package com.minibank.budget.service;

import com.minibank.analytics.dto.MonthlySummary;
import com.minibank.analytics.service.AnalyticsService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.budget.domain.Budget;
import com.minibank.budget.dto.BudgetRequest;
import com.minibank.budget.dto.BudgetResponse;
import com.minibank.budget.repository.BudgetRepository;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.money.Money;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly spending budgets.
 *
 * <p>A budget is a number the customer chose; everything else about it — spent, remaining, whether
 * it is close to the cap — is derived from {@link AnalyticsService} at read time. Reusing the
 * analytics is what guarantees the budget page and the analytics page cannot disagree: they are the
 * same arithmetic, run once.
 */
@Service
public class BudgetService {

    /** Where "you are getting close" starts. Below this the customer does not want to be told. */
    private static final int NEARING_PERCENT = 80;

    private final BudgetRepository budgets;
    private final AnalyticsService analytics;
    private final UserRepository users;
    private final AuditService auditService;
    private final Clock clock;

    public BudgetService(
            BudgetRepository budgets,
            AnalyticsService analytics,
            UserRepository users,
            AuditService auditService,
            Clock clock) {
        this.budgets = budgets;
        this.analytics = analytics;
        this.users = users;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Every budget with this month's progress against it.
     *
     * <p>One analytics pass serves the whole list. Asking per budget would run the same aggregation
     * once per category for no additional information.
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> listWithProgress(UUID ownerId, CurrencyCode currency, ZoneId zone) {
        List<Budget> owned = budgets.findAllOwnedBy(ownerId, currency);
        if (owned.isEmpty()) {
            return List.of();
        }

        YearMonth month = YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        Map<TransactionCategory, BigDecimal> spentByCategory = spentByCategory(ownerId, currency, month, zone);

        return owned.stream()
                .map(budget -> toResponse(budget, spentByCategory.getOrDefault(budget.getCategory(), Money.zero())))
                .toList();
    }

    /**
     * Creates the budget for a category, or moves the existing one.
     *
     * <p>Upsert rather than separate create and update endpoints: the unique constraint on (user,
     * category, currency) means "create" would fail half the time for a client that cannot know
     * whether one already exists, and setting a cap is idempotent by nature.
     */
    @Transactional
    public BudgetResponse set(BudgetRequest request, AuthenticatedUser caller, ZoneId zone) {
        User owner = users.findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        BigDecimal amount = Money.requireValidAmount(request.amount(), request.currency());

        Budget budget = budgets
                .findByOwnerIdAndCategoryAndCurrency(owner.getId(), request.category(), request.currency())
                .orElseGet(() -> Budget.of(owner, request.category(), amount, request.currency()));
        budget.setAmount(amount);
        Budget saved = budgets.save(budget);

        auditService.record(AuditEvent.success(AuditAction.BUDGET_SET)
                .by(owner)
                .on("Budget", saved.getId())
                .with("category", request.category())
                .with("amount", amount));

        YearMonth month = YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        BigDecimal spent = spentByCategory(owner.getId(), request.currency(), month, zone)
                .getOrDefault(request.category(), Money.zero());
        return toResponse(saved, spent);
    }

    @Transactional
    public void delete(UUID budgetId, AuthenticatedUser caller) {
        Budget budget = budgets
                .findByIdAndOwnerId(budgetId, caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUDGET_NOT_FOUND));
        budgets.delete(budget);
        auditService.record(AuditEvent.success(AuditAction.BUDGET_DELETED)
                .by(caller.getId(), caller.getUsername())
                .on("Budget", budgetId)
                .with("category", budget.getCategory()));
    }

    private Map<TransactionCategory, BigDecimal> spentByCategory(
            UUID ownerId, CurrencyCode currency, YearMonth month, ZoneId zone) {
        MonthlySummary summary = analytics.summarise(ownerId, month, currency, zone);
        Map<TransactionCategory, BigDecimal> spent = new HashMap<>();
        for (MonthlySummary.CategoryTotal total : summary.byCategory()) {
            if (total.category() != null) {
                spent.put(total.category(), total.amount());
            }
        }
        return spent;
    }

    private static BudgetResponse toResponse(Budget budget, BigDecimal spent) {
        BigDecimal cap = budget.getAmount();
        BigDecimal remaining = cap.subtract(spent);
        // An overspend leaves nothing, not a negative allowance. "−€40 remaining" reads as a debt
        // the customer owes the budget, which is not what a budget is.
        if (remaining.signum() < 0) {
            remaining = Money.zero();
        }

        int raw = cap.signum() == 0
                ? 0
                : spent.multiply(BigDecimal.valueOf(100)).divide(cap, 0, RoundingMode.HALF_UP).intValue();
        int capped = Math.min(100, Math.max(0, raw));

        String state;
        if (raw > 100) {
            state = "OVER";
        } else if (raw >= NEARING_PERCENT) {
            state = "NEARING";
        } else {
            state = "OK";
        }

        return new BudgetResponse(
                budget.getId(),
                budget.getCategory(),
                budget.getCurrency(),
                cap,
                spent,
                remaining,
                capped,
                raw,
                state);
    }
}
