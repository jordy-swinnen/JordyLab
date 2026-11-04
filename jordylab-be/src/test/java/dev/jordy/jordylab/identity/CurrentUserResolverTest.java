package dev.jordy.jordylab.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserResolverTest {

    private static final UUID USER_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    private static final AuthenticatedUser USER = new AuthenticatedUser(USER_ID, "jordy", "guest",
            Set.of(ManagedApp.GAME_CATALOG));

    private final CurrentUserResolver resolver = new CurrentUserResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheAuthenticatedUserForASessionPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(resolver.currentUser()).contains(USER);
    }

    @Test
    void returnsEmptyWithoutAuthentication() {
        assertThat(resolver.currentUser()).isEmpty();
    }

    @Test
    void returnsEmptyForAnAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(resolver.currentUser()).isEmpty();
    }

    @Test
    void returnsEmptyForANonUserPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("machine-principal", null,
                        AuthorityUtils.createAuthorityList("ROLE_gamecatalog-scanner")));

        assertThat(resolver.currentUser()).isEmpty();
    }
}
