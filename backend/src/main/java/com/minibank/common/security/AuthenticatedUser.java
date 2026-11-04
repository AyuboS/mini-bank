package com.minibank.common.security;

import com.minibank.user.domain.User;
import com.minibank.user.domain.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal placed in the security context.
 *
 * <p>Deliberately does not wrap the JPA {@link User} entity: keeping a detached entity in the
 * security context invites lazy-loading surprises and accidental exposure of the password hash. The
 * principal carries only the identifiers authorization decisions actually need.
 */
public final class AuthenticatedUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final UserRole role;
    private final boolean active;

    public AuthenticatedUser(UUID id, String username, UserRole role, boolean active) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.active = active;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(), user.getUsername(), user.getRole(), user.getStatus().canAuthenticate());
    }

    public UUID getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /** Never populated: authentication happens against the database or a signed token, not here. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
