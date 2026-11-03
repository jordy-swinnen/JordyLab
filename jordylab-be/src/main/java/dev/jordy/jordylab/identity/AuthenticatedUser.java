package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.shared.security.UserIdAware;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in person as every other module sees them (spec 019 FR-009, D6) — the narrow public API of the identity
 * module. {@code id} is the same UUID the owner columns already hold (the Keycloak subject for migrated users).
 *
 * <p>Implements {@link Principal} so Spring Session stores the username as the session's principal name, {@link
 * UserDetails} so Spring Security's WebAuthn provider builds the session principal from this same type (spec 019 D16),
 * and {@link Serializable} because Spring Session JDBC serializes the security context.
 */
public record AuthenticatedUser(UUID id, String username, String role, Set<ManagedApp> appGrants)
        implements UserIdAware, Serializable, Principal, UserDetails {

    /** The session's principal name: short, stable and equal to the username (Spring Session stores it, max 100). */
    @Override
    public String getName() {
        return username;
    }

    @Override
    public String userId() {
        return id.toString();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (ManagedApp app : appGrants) {
            app.grantRole().ifPresent(grant -> authorities.add(new SimpleGrantedAuthority("ROLE_" + grant)));
        }

        return authorities;
    }

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
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
