package com.minibank.account.repository;

import com.minibank.account.domain.AccountStatus;
import com.minibank.account.domain.BankAccount;
import com.minibank.common.money.CurrencyCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT a FROM BankAccount a WHERE a.owner.id = :ownerId ORDER BY a.createdAt")
    List<BankAccount> findAllByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT a FROM BankAccount a JOIN FETCH a.owner WHERE a.accountType = 'CUSTOMER'")
    Page<BankAccount> findAllCustomerAccounts(Pageable pageable);

    Optional<BankAccount> findByAccountTypeAndCurrency(
            com.minibank.account.domain.AccountType accountType, CurrencyCode currency);

    /**
     * Loads an account with a {@code SELECT ... FOR UPDATE} row lock.
     *
     * <p>This is the mechanism that prevents two concurrent transfers from spending the same money:
     * the second transaction blocks here until the first has committed, and therefore reads the
     * balance the first one left behind rather than a stale copy. Callers that lock more than one
     * account must do so in a deterministic order to avoid deadlock; see {@code
     * TransferExecutor#lockAccountsInDeterministicOrder}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Loads several accounts by number, with their owners, in one query.
     *
     * <p>Exists to keep the beneficiary listing off the N+1 path: resolving each payee's holder name
     * individually would issue two queries per row.
     */
    @Query("SELECT a FROM BankAccount a LEFT JOIN FETCH a.owner WHERE a.accountNumber IN :accountNumbers")
    List<BankAccount> findAllByAccountNumberInWithOwner(
            @Param("accountNumbers") java.util.Collection<String> accountNumbers);

    long countByStatus(AccountStatus status);
}
