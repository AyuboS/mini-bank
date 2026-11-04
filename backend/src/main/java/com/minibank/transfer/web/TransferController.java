package com.minibank.transfer.web;

import com.minibank.common.error.ApiError;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import com.minibank.ledger.dto.LedgerEntryResponse;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.dto.TransactionResponse;
import com.minibank.transfer.dto.TransferRequest;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.service.TransferQueryService;
import com.minibank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** Money movement and transaction history. */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Transfers", description = "Creating transfers and reading transaction history")
public class TransferController {

    /** Properties a client may sort transaction history by. */
    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "completedAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TransferService transferService;
    private final TransferQueryService transferQueryService;

    public TransferController(TransferService transferService, TransferQueryService transferQueryService) {
        this.transferService = transferService;
        this.transferQueryService = transferQueryService;
    }

    @PostMapping("/transfers")
    @Operation(
            summary = "Create a transfer",
            description =
                    """
                    Moves money between two MBank accounts in a single database transaction.

                    An `Idempotency-Key` header is required. Replaying the same key with the same body
                    returns the original outcome without moving money again; replaying it with a
                    different body is rejected.

                    Returns **201** when the transfer settled, and **202** when the risk engine held it
                    for manual review — in that case the transfer exists in `PENDING` and no money has
                    moved yet.
                    """)
    @ApiResponse(responseCode = "201", description = "Transfer settled")
    @ApiResponse(responseCode = "202", description = "Held for manual review; no money has moved")
    @ApiResponse(
            responseCode = "409",
            description = "A request with this idempotency key is still in progress",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "422",
            description = "Insufficient funds, limit exceeded, currency mismatch, idempotency key "
                    + "reused with a different payload, or blocked by the risk engine",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TransferResponse> createTransfer(
            @Parameter(
                            description = "Unique key identifying this request; reuse it to retry safely",
                            required = true,
                            example = "8f14e45f-ea4c-4f2b-9d1e-3a7c2b6d9e01")
                    @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Size(min = 8, max = 100)
                    String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        TransferService.TransferOutcome outcome = transferService.create(request, caller, idempotencyKey);
        return ResponseEntity.status(outcome.status())
                .header("Idempotent-Replay", String.valueOf(outcome.replayed()))
                .body(outcome.transfer());
    }

    @GetMapping("/transfers/{id}")
    @Operation(
            summary = "Get a transfer",
            description = "Visible to the participants of the transfer and to administrators.")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(
                TransferResponse.from(transferQueryService.getAccessibleTransfer(id, caller)));
    }

    @GetMapping("/transfers/{id}/ledger")
    @Operation(
            summary = "Get the ledger entries of a transfer",
            description = "The debit and credit postings that back the transfer. A settled transfer "
                    + "always has exactly two, and they always sum to zero.")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerEntries(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(transferQueryService.ledgerEntriesFor(id, caller).stream()
                .map(LedgerEntryResponse::from)
                .toList());
    }

    @GetMapping("/transactions")
    @Operation(
            summary = "List my transactions",
            description = "Transfers involving any of the caller's accounts, newest first. "
                    + "Pagination is applied in the database; sorting is restricted to "
                    + "createdAt, amount, status and completedAt.")
    public ResponseEntity<PageResponse<TransactionResponse>> listTransactions(
            @RequestParam(required = false) TransferStatus status,
            @Parameter(description = "Inclusive lower bound on creation time") @RequestParam(required = false)
                    Instant from,
            @Parameter(description = "Inclusive upper bound on creation time") @RequestParam(required = false)
                    Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(name = "q", required = false) @Size(max = 80) String query,
            @PageableDefault(size = 20) Pageable pageable) {

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Set<UUID> visibleAccounts = transferQueryService.visibleAccountIds(caller);
        var filter = new TransferQueryService.TransferFilter(
                status, from, to, minAmount, maxAmount, category, query);
        var page = transferQueryService.searchForCaller(
                caller, filter, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(
                PageResponse.from(page, transfer -> TransactionResponse.from(transfer, visibleAccounts)));
    }

    @PatchMapping("/{id}/category")
    @Operation(
            summary = "Change what a transaction was for",
            description =
                    "Either party to the payment may set this, and both then see it: a category "
                            + "describes the payment, not one side's view of it.")
    public ResponseEntity<TransactionResponse> recategorise(
            @PathVariable UUID id, @Valid @RequestBody RecategoriseRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Set<UUID> visibleAccounts = transferQueryService.visibleAccountIds(caller);
        return ResponseEntity.ok(TransactionResponse.from(
                transferQueryService.recategorise(id, request.category(), caller), visibleAccounts));
    }

    /** The only mutable field on a settled transfer. */
    @io.swagger.v3.oas.annotations.media.Schema(name = "RecategoriseRequest")
    public record RecategoriseRequest(
            @jakarta.validation.constraints.NotNull
                    @io.swagger.v3.oas.annotations.media.Schema(example = "GROCERIES")
                    TransactionCategory category) {}
}
