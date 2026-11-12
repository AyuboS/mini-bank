package com.minibank.account.web;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceResponse;
import com.minibank.account.dto.CreateAccountRequest;
import com.minibank.account.service.AccountService;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.dto.TopUpRequest;
import com.minibank.transfer.dto.TransactionResponse;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.service.TopUpService;
import com.minibank.transfer.service.TransferService;
import com.minibank.transfer.service.TransferQueryService;
import com.minibank.user.service.UserService;
import com.minibank.account.dto.PayeeLookupResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's bank accounts.
 *
 * <p>Every route that takes an account id resolves it through {@link
 * AccountService#getAccessibleAccount}, which returns {@code ACCOUNT_NOT_FOUND} for an account the
 * caller does not own. The controller never compares ids itself.
 */
@RestController
@Validated
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Bank accounts, balances and per-account history")
public class AccountController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "completedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AccountService accountService;
    private final UserService userService;
    private final TransferQueryService transferQueryService;
    private final TopUpService topUpService;
    private final Clock clock;

    public AccountController(
            AccountService accountService,
            UserService userService,
            TransferQueryService transferQueryService,
            TopUpService topUpService,
            Clock clock) {
        this.accountService = accountService;
        this.userService = userService;
        this.transferQueryService = transferQueryService;
        this.topUpService = topUpService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "List my accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(accountService.listOwnedAccounts(caller.getId()).stream()
                .map(AccountResponse::from)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Open an additional account", description = "Opens an empty account in the chosen currency.")
    @ApiResponse(responseCode = "201", description = "Account opened")
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody CreateAccountRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        BankAccount account = accountService.openAccount(userService.getById(caller.getId()), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my accounts")
    @ApiResponse(responseCode = "404", description = "No such account, or it belongs to somebody else")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(AccountResponse.from(accountService.getAccessibleAccount(id, caller)));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get an account balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        BankAccount account = accountService.getAccessibleAccount(id, caller);
        return ResponseEntity.ok(BalanceResponse.from(account, clock.instant()));
    }

    @PostMapping("/{id}/top-up")
    @Operation(
            summary = "Simulated top-up of my own account",
            description = "Adds play money to one of the caller's active spending accounts, funded by the "
                    + "bank's system account so the ledger stays balanced. Requires an Idempotency-Key. "
                    + "Capped per top-up and per day (minibank.top-up.*). No real money moves.")
    @ApiResponse(responseCode = "201", description = "Topped up")
    @ApiResponse(responseCode = "404", description = "No such account, or it belongs to somebody else")
    @ApiResponse(responseCode = "422", description = "Over a cap, or not a spending account")
    public ResponseEntity<TransferResponse> topUp(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        TransferService.TransferOutcome outcome = topUpService.topUp(id, request, caller, idempotencyKey);
        return ResponseEntity.status(outcome.status())
                .header("Idempotent-Replay", String.valueOf(outcome.replayed()))
                .body(outcome.transfer());
    }

    @GetMapping("/lookup")
    @Operation(
            summary = "Who an account number belongs to",
            description =
                    "Confirmation of payee, so a mistyped digit is caught before money moves. Returns a "
                            + "first name and last initial only.")
    @ApiResponse(responseCode = "404", description = "No MBank account with that number")
    public ResponseEntity<PayeeLookupResponse> lookup(
            @RequestParam
                    @Pattern(regexp = "^MB-[0-9]{12}$", message = "must be a valid MBank account number")
                    String accountNumber) {
        // Authentication is required by the security chain; the caller's identity
        // is not used beyond that, because a payee's name is the same whoever asks.
        SecurityUtils.requireCurrentUser();
        // Loaded with its owner: the holder name is read after the transaction has closed.
        BankAccount account = accountService.findAllByAccountNumbers(List.of(accountNumber)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND));
        return ResponseEntity.ok(new PayeeLookupResponse(
                account.getAccountNumber(), abbreviatedHolderName(account), account.getCurrency(), "MBank"));
    }

    /**
     * "Jane Smith" becomes "Jane S."
     *
     * <p>Enough for a payer to recognise who they are about to pay, without
     * turning the endpoint into a directory that yields full names for any
     * account number somebody cares to try.
     */
    private static String abbreviatedHolderName(BankAccount account) {
        var owner = account.getOwner();
        if (owner == null) {
            // A system account. Named rather than blank, since it is not a person.
            return "MBank";
        }
        String lastName = owner.getLastName();
        String initial = lastName == null || lastName.isBlank() ? "" : " " + lastName.charAt(0) + ".";
        return owner.getFirstName() + initial;
    }

    @GetMapping("/{id}/transactions")
    @Operation(
            summary = "List the transactions of one account",
            description = "Transfers where this account is either the source or the destination.")
    public ResponseEntity<PageResponse<TransactionResponse>> getAccountTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) TransferStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        var filter = TransferQueryService.TransferFilter.of(status, null, null, null, null);
        var page = transferQueryService.searchForAccount(
                id, caller, filter, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(PageResponse.from(page, transfer -> TransactionResponse.from(transfer, Set.of(id))));
    }
}
