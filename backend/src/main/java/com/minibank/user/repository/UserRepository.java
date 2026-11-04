package com.minibank.user.repository;

import com.minibank.user.domain.User;
import com.minibank.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /** Phone numbers are stored in E.164, so this is an exact match on a normalised value. */
    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    long countByStatus(UserStatus status);

    /** Optional status filter for the administrative user list. */
    static Specification<User> hasStatus(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
