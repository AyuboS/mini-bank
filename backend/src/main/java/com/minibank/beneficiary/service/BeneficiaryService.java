package com.minibank.beneficiary.service;

import com.minibank.account.domain.BankAccount;
import com.minibank.account.service.AccountService;
import com.minibank.audit.domain.AuditAction;
import com.minibank.audit.service.AuditEvent;
import com.minibank.audit.service.AuditService;
import com.minibank.beneficiary.domain.Beneficiary;
import com.minibank.beneficiary.domain.BeneficiaryStatus;
import com.minibank.beneficiary.dto.BeneficiaryRequest;
import com.minibank.beneficiary.dto.BeneficiaryResponse;
import com.minibank.beneficiary.repository.BeneficiaryRepository;
import com.minibank.common.error.BusinessRuleException;
import com.minibank.common.error.ErrorCode;
import com.minibank.common.error.NotFoundException;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.user.domain.User;
import com.minibank.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer's saved payees. */
@Service
public class BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public BeneficiaryService(
            BeneficiaryRepository beneficiaryRepository,
            AccountService accountService,
            UserRepository userRepository,
            AuditService auditService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Saves a payee after confirming the account number exists.
     *
     * <p>Validating up front means the customer learns about a typo now, rather than when a payment
     * fails. It does leak one bit — whether an account number is in use — which is why only the
     * abbreviated holder name is returned, and why this endpoint is rate limited.
     */
    @Transactional
    public BeneficiaryResponse create(AuthenticatedUser caller, BeneficiaryRequest request) {
        BankAccount target = accountService.getByAccountNumber(request.accountNumber());
        if (target.isSystemAccount()) {
            throw new NotFoundException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        beneficiaryRepository
                .findByOwnerIdAndBeneficiaryAccountNumberAndStatus(
                        caller.getId(), request.accountNumber(), BeneficiaryStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new BusinessRuleException(ErrorCode.BENEFICIARY_ALREADY_EXISTS);
                });

        User owner = userRepository
                .findById(caller.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        Beneficiary beneficiary = beneficiaryRepository.save(
                Beneficiary.create(owner, request.accountNumber(), request.nickname().trim()));

        auditService.record(AuditEvent.success(AuditAction.BENEFICIARY_CREATED)
                .by(owner)
                .on("Beneficiary", beneficiary.getId())
                .with("accountNumber", request.accountNumber()));
        log.info("User {} saved beneficiary {}", caller.getId(), request.accountNumber());
        return BeneficiaryResponse.from(beneficiary, abbreviatedHolderName(target));
    }

    /**
     * Lists a customer's payees.
     *
     * <p>The holder names are resolved with a single batched query rather than one lookup per payee:
     * the obvious loop would issue two queries per row, which is the classic N+1 and shows up as a
     * listing that gets slower the more payees somebody saves.
     */
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(UUID ownerId) {
        List<Beneficiary> beneficiaries = beneficiaryRepository.findActiveByOwner(ownerId);
        if (beneficiaries.isEmpty()) {
            return List.of();
        }

        Map<String, String> holderNames = accountService
                .findAllByAccountNumbers(beneficiaries.stream()
                        .map(Beneficiary::getBeneficiaryAccountNumber)
                        .collect(java.util.stream.Collectors.toSet()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        BankAccount::getAccountNumber,
                        BeneficiaryService::abbreviatedHolderName,
                        (first, second) -> first));

        return beneficiaries.stream()
                .map(beneficiary -> BeneficiaryResponse.from(
                        beneficiary, holderNames.get(beneficiary.getBeneficiaryAccountNumber())))
                .toList();
    }

    /**
     * Pins or unpins a payee.
     *
     * <p>A toggle rather than a pair of set/unset endpoints: the client already knows the current
     * state from the list it is rendering, and two endpoints would double the surface for one bit.
     */
    @Transactional
    public BeneficiaryResponse toggleFavourite(UUID beneficiaryId, AuthenticatedUser caller) {
        Beneficiary beneficiary = beneficiaryRepository
                .findById(beneficiaryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BENEFICIARY_NOT_FOUND));
        if (!beneficiary.isOwnedBy(caller.getId()) || beneficiary.getStatus() == BeneficiaryStatus.DELETED) {
            throw new NotFoundException(ErrorCode.BENEFICIARY_NOT_FOUND);
        }
        beneficiary.toggleFavourite();

        String holderName = accountService
                .findAllByAccountNumbers(java.util.Set.of(beneficiary.getBeneficiaryAccountNumber()))
                .stream()
                .findFirst()
                .map(BeneficiaryService::abbreviatedHolderName)
                .orElse(null);
        return BeneficiaryResponse.from(beneficiary, holderName);
    }

    /** Soft deletes a payee the caller owns. */
    @Transactional
    public void delete(UUID beneficiaryId, AuthenticatedUser caller) {
        Beneficiary beneficiary = beneficiaryRepository
                .findById(beneficiaryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BENEFICIARY_NOT_FOUND));
        if (!beneficiary.isOwnedBy(caller.getId())) {
            // Not FORBIDDEN: acknowledging the id exists would confirm another customer's data.
            throw new NotFoundException(ErrorCode.BENEFICIARY_NOT_FOUND);
        }
        if (beneficiary.getStatus() == BeneficiaryStatus.DELETED) {
            throw new NotFoundException(ErrorCode.BENEFICIARY_NOT_FOUND);
        }
        beneficiary.softDelete();
        auditService.record(AuditEvent.success(AuditAction.BENEFICIARY_DELETED)
                .by(caller.getId(), caller.getUsername())
                .on("Beneficiary", beneficiaryId));
    }

    /**
     * "Jane Smith" becomes "Jane S." — enough for the payer to recognise who they are paying, without
     * turning the beneficiary endpoint into a lookup service for full names.
     */
    private static String abbreviatedHolderName(BankAccount account) {
        User owner = account.getOwner();
        if (owner == null) {
            // A system account, or a payee whose account has since been closed.
            return "";
        }
        String lastName = owner.getLastName();
        String initial = lastName == null || lastName.isBlank() ? "" : " " + lastName.charAt(0) + ".";
        return owner.getFirstName() + initial;
    }
}
