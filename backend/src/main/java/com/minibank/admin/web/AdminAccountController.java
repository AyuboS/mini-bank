package com.minibank.admin.web;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.service.AccountService;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import com.minibank.transfer.dto.DepositRequest;
import com.minibank.transfer.dto.TransferResponse;
import com.minibank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account administration: freezing, unfreezing and funding customer accounts. */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: accounts", description = "Account administration. Requires the ADMIN role.")
public class AdminAccountController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "balance", "status", "currency");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AccountService accountService;
    private final TransferService transferService;

    public AdminAccountController(AccountService accountService, TransferService transferService) {
        this.accountService = accountService;
        this.transferService = transferService;
    }

    @GetMapping
    @Operation(summary = "List customer accounts")
    public ResponseEntity<PageResponse<AccountResponse>> listAccounts(@PageableDefault(size = 20) Pageable pageable) {
        var page = accountService.listAllCustomerAccounts(SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(PageResponse.from(page, AccountResponse::from));
    }

    @PatchMapping("/{id}/freeze")
    @Operation(
            summary = "Freeze an account",
            description = "A frozen account can neither send nor receive money. The balance is untouched.")
    public ResponseEntity<AccountResponse> freeze(@PathVariable UUID id) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(AccountResponse.from(accountService.freeze(id, admin)));
    }

    @PatchMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze an account")
    public ResponseEntity<AccountResponse> unfreeze(@PathVariable UUID id) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(AccountResponse.from(accountService.unfreeze(id, admin)));
    }

    @PostMapping("/{id}/deposit")
    @Operation(
            summary = "Deposit into a customer account",
            description = "Moves money from the bank's system funding account for that currency, so the "
                    + "ledger stays balanced. Not subject to customer transfer limits or risk rules.")
    public ResponseEntity<TransferResponse> deposit(
            @PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(transferService.deposit(id, request, admin));
    }
}
