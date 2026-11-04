package com.minibank.transfer.repository;

import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.Transfer;
import com.minibank.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable predicates for transfer search.
 *
 * <p>Each factory returns {@code null} when its filter is absent, which Spring Data treats as "no
 * restriction". Composing with {@code Specification.allOf} therefore yields SQL containing only the
 * predicates the caller actually supplied.
 */
public final class TransferSpecifications {

    private TransferSpecifications() {}

    /** Restricts to transfers where one of the given accounts is either side of the movement. */
    public static Specification<Transfer> involvingAnyAccount(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            // No accounts means no visible transfers; an always-false predicate is safer than
            // returning null, which would silently widen the query to every transfer in the bank.
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.or(
                root.get("sourceAccount").get("id").in(accountIds),
                root.get("destinationAccount").get("id").in(accountIds));
    }

    public static Specification<Transfer> involvingAccount(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("sourceAccount").get("id"), accountId),
                cb.equal(root.get("destinationAccount").get("id"), accountId));
    }

    public static Specification<Transfer> hasStatus(TransferStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transfer> createdFrom(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Transfer> createdTo(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Transfer> amountAtLeast(BigDecimal min) {
        if (min == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Transfer> amountAtMost(BigDecimal max) {
        if (max == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    public static Specification<Transfer> hasCategory(TransactionCategory category) {
        if (category == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /**
     * Free-text search over the description and the reference.
     *
     * <p>Case-insensitive and unanchored, which is what a customer typing "netflix" into a search
     * box expects. Deliberately limited to those two fields: matching on account numbers would turn
     * the transaction search into a way of confirming whether a given account exists.
     */
    public static Specification<Transfer> matchesText(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        return (root, criteriaQuery, cb) -> cb.or(
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("reference")), pattern));
    }

    public static Specification<Transfer> initiatedBy(UUID userId) {
        if (userId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("initiatedBy").get("id"), userId);
    }
}
