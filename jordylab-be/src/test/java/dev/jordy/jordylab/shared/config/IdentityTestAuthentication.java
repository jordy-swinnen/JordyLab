package dev.jordy.jordylab.shared.config;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.ManagedApp;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Session principals for {@code @WebMvcTest} slices after the identity rewrite (spec 019): the controllers resolve the current user
 * through {@code CurrentUserResolver}, whose principal is an {@link AuthenticatedUser}. The ids are fixed UUIDs so
 * tests can assert the subject the services receive.
 */
public final class IdentityTestAuthentication {

    public static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final String ADMIN_SUBJECT = ADMIN_ID.toString();
    public static final UUID GUEST_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final String GUEST_SUBJECT = GUEST_ID.toString();
    public static final UUID PENDING_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final String PENDING_SUBJECT = PENDING_ID.toString();

    private IdentityTestAuthentication() {
    }

    public static RequestPostProcessor asAdmin() {
        return authentication(token(new AuthenticatedUser(ADMIN_ID, "admin", "admin", Set.of()), "ROLE_admin"));
    }

    public static RequestPostProcessor asGuest() {
        return authentication(token(new AuthenticatedUser(GUEST_ID, "guest", "guest", Set.of(ManagedApp.GAME_CATALOG)),
                "ROLE_guest", "ROLE_app-gamecatalog"));
    }

    public static RequestPostProcessor asGuestWithoutCatalog() {
        return authentication(token(new AuthenticatedUser(GUEST_ID, "guest", "guest", Set.of()), "ROLE_guest"));
    }

    public static RequestPostProcessor asPending() {
        return authentication(token(new AuthenticatedUser(PENDING_ID, "pending", null, Set.of())));
    }

    private static UsernamePasswordAuthenticationToken token(AuthenticatedUser user, String... authorities) {
        List<GrantedAuthority> granted = new ArrayList<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }

        return new UsernamePasswordAuthenticationToken(user, null, granted);
    }
}
