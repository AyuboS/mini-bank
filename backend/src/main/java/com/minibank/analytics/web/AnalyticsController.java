package com.minibank.analytics.web;

import com.minibank.analytics.dto.InsightResponse;
import com.minibank.analytics.dto.MonthlySummary;
import com.minibank.analytics.service.AnalyticsService;
import com.minibank.common.money.CurrencyCode;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spending analytics for the authenticated customer.
 *
 * <p>Every figure is derived from the caller's own settled transfers. There is no endpoint here that
 * can read another customer's data: the user id comes from the token, never from a parameter.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Income, spending and insights over the caller's own transactions")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final Clock clock;

    public AnalyticsController(AnalyticsService analyticsService, Clock clock) {
        this.analyticsService = analyticsService;
        this.clock = clock;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "A month of income, spending and category breakdown",
            description = "Movement between your own accounts is counted as neither income nor spending.")
    public ResponseEntity<MonthlySummary> summary(
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "EUR") CurrencyCode currency,
            @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        ZoneId zone = zoneOf(timezone);
        return ResponseEntity.ok(analyticsService.summarise(caller.getId(), monthOf(month, zone), currency, zone));
    }

    @GetMapping("/insights")
    @Operation(
            summary = "Observations about this month",
            description =
                    "Each one is arithmetic over real transactions. Returns an empty list when there is "
                            + "not enough history to say anything true.")
    public ResponseEntity<List<InsightResponse>> insights(
            @RequestParam(defaultValue = "EUR") CurrencyCode currency,
            @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        ZoneId zone = zoneOf(timezone);
        return ResponseEntity.ok(analyticsService.insights(caller.getId(), currency, zone));
    }

    /**
     * The caller's timezone, falling back to UTC.
     *
     * <p>An unrecognised zone falls back rather than failing the request: a wrong month boundary is
     * a small inaccuracy the customer can see, while a 400 on a dashboard load is a broken page.
     */
    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private YearMonth monthOf(String month, ZoneId zone) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ignored) {
            return YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        }
    }
}
