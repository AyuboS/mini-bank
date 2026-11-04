package com.minibank.statement.web;

import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.statement.dto.StatementResponse;
import com.minibank.statement.service.StatementCsvService;
import com.minibank.statement.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Monthly account statements, as JSON or as a spreadsheet.
 *
 * <p>Both endpoints go through the same ownership check every other account read uses, so there is
 * no path here that can produce another customer's statement.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statements")
@Tag(name = "Statements", description = "Monthly statements, downloadable as CSV")
public class StatementController {

    private final StatementService statementService;
    private final StatementCsvService csvService;
    private final Clock clock;

    public StatementController(
            StatementService statementService, StatementCsvService csvService, Clock clock) {
        this.statementService = statementService;
        this.csvService = csvService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(
            summary = "A month of statement",
            description = "Opening balance is derived from the ledger, so the figures always reconcile.")
    @ApiResponse(responseCode = "404", description = "No such account, or not yours")
    public ResponseEntity<StatementResponse> statement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        ZoneId zone = zoneOf(timezone);
        return ResponseEntity.ok(statementService.forMonth(accountId, monthOf(month, zone), zone, caller));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "The same statement as a CSV download")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String timezone) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        ZoneId zone = zoneOf(timezone);
        StatementResponse statement = statementService.forMonth(accountId, monthOf(month, zone), zone, caller);

        byte[] body = csvService.render(statement).getBytes(StandardCharsets.UTF_8);
        String filename = "mbank-%s-%s.csv".formatted(statement.accountNumber(), statement.period());

        return ResponseEntity.ok()
                // `attachment` rather than `inline`: a CSV rendered in a browser tab is useless, and
                // serving user-authored content inline is how a download becomes an XSS vector.
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

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
