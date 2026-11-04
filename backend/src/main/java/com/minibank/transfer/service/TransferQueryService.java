package com.minibank.transfer.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.ledger.domain.LedgerEntry;
import com.minibank.ledger.repository.LedgerEntryRepository;
import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.repository.TransferRepository;
import com.minibank.transfer.repository.TransferSpecifications;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the transfer module.
 *
 * <p>Visibility is scoped in the query itself rather than filtered after loading: a customer's
 * search is restricted to their own account ids before it reaches the database, so there is no point
 * at which another customer's rows are in memory waiting to be filtered out by a check somebody
 * might forget to write.
 */
@Service
public class TransferQueryService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountService accountService;

    public TransferQueryService(
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountService accountService) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountService = accountService;
    }

    /** Every account id the caller can see, used to scope their transaction history. */
    @Transactional(readOnly = true)
    public Set<UUID> visibleAccountIds(AuthenticatedUser caller) {
        return accountService.listOwnedAccounts(caller.getId()).stream()
                .map(BankAccount::getId)
                .collect(Collectors.toSet());
    }

    /** The caller's transaction history across all of their accounts. */
    @Transactional(readOnly = true)
    public Page<Transfer> searchForCaller(AuthenticatedUser caller, TransferFilter filter, Pageable pageable) {
        Specification<Transfer> spec = Specification.allOf(
                TransferSpecifications.involvingAnyAccount(visibleAccountIds(caller)), filter.toSpecification());
        return transferRepository.findAll(spec, pageable);
    }

    /** History for one account, after confirming the caller may see it. */
    @Transactional(readOnly = true)
    public Page<Transfer> searchForAccount(
            UUID accountId, AuthenticatedUser caller, TransferFilter filter, Pageable pageable) {
        BankAccount account = accountService.getAccessibleAccount(accountId, caller);
        Specification<Transfer> spec = Specification.allOf(
                TransferSpecifications.involvingAccount(account.getId()), filter.toSpecification());
        return transferRepository.findAll(spec, pageable);
    }

    /** Administrative search across every transfer in the bank. */
    @Transactional(readOnly = true)
    public Page<Transfer> searchAll(TransferFilter filter, Pageable pageable) {
        return transferRepository.findAll(Specification.allOf(filter.toSpecification()), pageable);
    }

    /**
     * Loads a transfer the caller is entitled to see.
     *
     * <p>A customer may see a transfer only if one of their own accounts is on either side of it.
     */
    @Transactional(readOnly = true)
    public Transfer getAccessibleTransfer(UUID transferId, AuthenticatedUser caller) {
        Transfer transfer = transferRepository
                .findWithAccountsById(transferId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND));
        if (caller.isAdmin()) {
            return transfer;
        }
        Set<UUID> visible = visibleAccountIds(caller);
        boolean involved = visible.contains(transfer.getSourceAccount().getId())
                || visible.contains(transfer.getDestinationAccount().getId());
        if (!involved) {
            throw new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND);
        }
        return transfer;
    }

    /** The ledger entries backing a transfer, for the transfer's participants and administrators. */
    @Transactional(readOnly = true)
    public List<LedgerEntry> ledgerEntriesFor(UUID transferId, AuthenticatedUser caller) {
        getAccessibleTransfer(transferId, caller);
        return ledgerEntryRepository.findByTransferId(transferId);
    }

    /**
     * Changes the category on a transfer the caller took part in.
     *
     * <p>Either party may recategorise, and both then see the new label — a category describes the
     * payment, not one side's opinion of it, and two conflicting labels for the same movement would
     * make the analytics on either side impossible to reconcile with the other.
     *
     * <p>Nothing else about a settled transfer is mutable here. The amount, the accounts and the
     * status are the record of what happened; only the label the customer applies to it can change.
     */
    @Transactional
    public Transfer recategorise(UUID transferId, TransactionCategory category, AuthenticatedUser caller) {
        Transfer transfer = getAccessibleTransfer(transferId, caller);
        transfer.setCategory(category);
        transferRepository.save(transfer);
        return transfer;
    }

    /** Optional filters for a transfer search. */
    public record TransferFilter(
            TransferStatus status,
            Instant from,
            Instant to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionCategory category,
            String query) {

        public static TransferFilter none() {
            return new TransferFilter(null, null, null, null, null, null, null);
        }

        /** The original five-field form, kept so existing callers do not have to restate two nulls. */
        public static TransferFilter of(
                TransferStatus status, Instant from, Instant to, BigDecimal minAmount, BigDecimal maxAmount) {
            return new TransferFilter(status, from, to, minAmount, maxAmount, null, null);
        }

        Specification<Transfer> toSpecification() {
            return Specification.allOf(
                    TransferSpecifications.hasStatus(status),
                    TransferSpecifications.createdFrom(from),
                    TransferSpecifications.createdTo(to),
                    TransferSpecifications.amountAtLeast(minAmount),
                    TransferSpecifications.amountAtMost(maxAmount),
                    TransferSpecifications.hasCategory(category),
                    TransferSpecifications.matchesText(query));
        }
    }
}
