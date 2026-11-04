package com.minibank.analytics.dto;

import com.minibank.transfer.domain.TransactionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One observation derived from the customer's own settled transactions.
 *
 * <h2>Numbers, not sentences</h2>
 *
 * <p>This carries the figures an insight is about and leaves the wording to the
 * client. The earlier version returned a finished English sentence with the
 * amounts already formatted, which was wrong twice over: the server has no idea
 * what locale the reader uses, so its "€5307.92" appeared beside the client's
 * own "€6,650.50" on the same screen; and every string was then stranded in Java
 * where no translation could reach it.
 *
 * <h2>What it does not carry</h2>
 *
 * <p>Nothing estimated, projected, or compared against other customers. Each
 * {@code kind} is a statement the service can back with arithmetic over rows
 * that exist, and when the data cannot support one, no insight is produced at
 * all rather than a hedged one.
 */
@Schema(name = "Insight")
public record InsightResponse(
        @Schema(
                        description = "NET_POSITIVE, NET_NEGATIVE, TOP_CATEGORY, SPENDING_UP or SPENDING_DOWN",
                        example = "SPENDING_DOWN")
                String kind,
        @Schema(description = "POSITIVE, NEUTRAL or ATTENTION — how the client should colour it")
                String tone,
        @Schema(description = "The category the observation is about, where it is about one")
                TransactionCategory category,
        @Schema(description = "The figure the observation is about", example = "142.3000")
                BigDecimal amount,
        @Schema(description = "What it is being compared against, where there is a comparison")
                BigDecimal comparisonAmount,
        @Schema(description = "Signed month-on-month change, whole percent", example = "-18")
                Integer percentChange,
        @Schema(description = "Share of total spending, 0–100, one decimal", example = "64.2")
                BigDecimal share) {

    /** An observation about the month as a whole. */
    public static InsightResponse net(String kind, String tone, BigDecimal amount, BigDecimal comparison) {
        return new InsightResponse(kind, tone, null, amount, comparison, null, null);
    }

    /** The category the most money went to. */
    public static InsightResponse topCategory(TransactionCategory category, BigDecimal amount, BigDecimal share) {
        return new InsightResponse("TOP_CATEGORY", "NEUTRAL", category, amount, null, null, share);
    }

    /** A category compared against the same category last month. */
    public static InsightResponse change(
            TransactionCategory category, BigDecimal now, BigDecimal before, int percentChange) {
        return new InsightResponse(
                percentChange < 0 ? "SPENDING_DOWN" : "SPENDING_UP",
                percentChange < 0 ? "POSITIVE" : "ATTENTION",
                category,
                now,
                before,
                percentChange,
                null);
    }
}
