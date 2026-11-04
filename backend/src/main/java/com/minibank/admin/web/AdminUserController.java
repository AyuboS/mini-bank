package com.minibank.admin.web;

import com.minibank.admin.dto.AdminUserResponse;
import com.minibank.admin.dto.ChangeUserStatusRequest;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import com.minibank.limit.dto.EffectiveLimits;
import com.minibank.limit.dto.UpdateTransferLimitsRequest;
import com.minibank.limit.service.TransferLimitService;
import com.minibank.user.domain.User;
import com.minibank.user.domain.UserStatus;
import com.minibank.user.repository.UserRepository;
import com.minibank.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer administration.
 *
 * <p>{@code @PreAuthorize} duplicates the URL-level rule in {@code SecurityConfig} on purpose:
 * if a future refactor moves or renames these routes, the annotation still holds, so the endpoint
 * cannot quietly become reachable by a customer.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: users", description = "Customer administration. Requires the ADMIN role.")
public class AdminUserController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "username", "email", "status", "lastLoginAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final UserRepository userRepository;
    private final UserService userService;
    private final TransferLimitService transferLimitService;

    public AdminUserController(
            UserRepository userRepository, UserService userService, TransferLimitService transferLimitService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.transferLimitService = transferLimitService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Optionally filtered by status.")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Specification<User> spec = Specification.allOf(UserRepository.hasStatus(status));
        var page = userRepository.findAll(spec, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(PageResponse.from(page, AdminUserResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(AdminUserResponse.from(userService.getById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Change a user's status",
            description = "Suspending or locking a user also revokes every active session.")
    public ResponseEntity<AdminUserResponse> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest request) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        User updated = userService.changeStatus(id, request.status(), admin.getId(), admin.getUsername());
        return ResponseEntity.ok(AdminUserResponse.from(updated));
    }

    @GetMapping("/{id}/limits")
    @Operation(summary = "Get a user's effective transfer limits")
    public ResponseEntity<EffectiveLimits> getLimits(@PathVariable UUID id) {
        return ResponseEntity.ok(transferLimitService.effectiveLimitsFor(id));
    }

    @PutMapping("/{id}/limits")
    @Operation(
            summary = "Set a user's transfer limits",
            description = "Null fields fall back to the platform defaults.")
    public ResponseEntity<EffectiveLimits> updateLimits(
            @PathVariable UUID id, @Valid @RequestBody UpdateTransferLimitsRequest request) {
        AuthenticatedUser admin = SecurityUtils.requireCurrentUser();
        User target = userService.getById(id);
        return ResponseEntity.ok(
                transferLimitService.updateOverride(target, request, admin.getId(), admin.getUsername()));
    }
}
