package com.minibank.analytics.dto;

import com.minibank.common.money.CurrencyCode;
import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * One period of a customer's money, in the shape the analytics screen needs.
 *
 * <h2>What counts as what</h2>
 *
 * <p>The three totals are defined by where the money went, not by its category:
 *
 * <ul>
 *   <li><b>income</b> — settled money arriving from an account the customer does not own
 *   <li><b>spending</b> — settled money leaving to an account the customer does not own
 *   <li><b>internalMovement</b> — money moved between the customer's own accounts, savings goals
 *       included, counted in neither of the above
 * </ul>
 *
 * <p>That last exclusion is the one that makes the page trustworthy. Counting a €500 contribution to
 * a savings goal as €500 of spending would tell somebody they overspent in the month they saved the
 * most.
 */
@Schema(name = "MonthlySummary")
public record MonthlySummary(
        @Schema(example = "2026-08") String period,
        CurrencyCode currency,
        @Schema(example = "3200.0000") BigDecimal income,
        @Schema(example = "1240.0000") BigDecimal spending,
        @Schema(description = "income − spending. Negative when more went out than came in.", example = "1960.0000")
                BigDecimal net,
        @Schema(description = "Between the customer's own accounts. In neither income nor spending.")
                BigDecimal internalMovement,
        @Schema(example = "42") int transactionCount,
        @Schema(description = "Spending only, largest first. Null category means uncategorised.")
                List<CategoryTotal> byCategory,
        @Schema(description = "Daily spending and income across the period, oldest first")
                List<DailyPoint> series,
        @Schema(description = "The single biggest outgoing payment, if there was one")
                LargestTransaction largest) {

    /** One slice of the spending breakdown. */
    @Schema(name = "CategoryTotal")
    public record CategoryTotal(
            @Schema(description = "Null when the payment was never categorised") TransactionCategory category,
            BigDecimal amount,
            @Schema(description = "Share of total spending, 0–100, rounded to one decimal", example = "18.4")
                    BigDecimal share,
            int transactionCount) {}

    /** One day on the chart. Days with no activity are present with zeros, so the axis is even. */
    @Schema(name = "DailyPoint")
    public record DailyPoint(
            @Schema(example = "2026-08-14") String date, BigDecimal spending, BigDecimal income) {}

    @Schema(name = "LargestTransaction")
    public record LargestTransaction(
            java.util.UUID transferId,
            String reference,
            String description,
            TransactionCategory category,
            BigDecimal amount,
            String counterpartyAccountNumber,
            java.time.Instant completedAt) {}
}
