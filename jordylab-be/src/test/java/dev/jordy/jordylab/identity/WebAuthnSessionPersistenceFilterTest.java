package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The passkey session fix (spec 019 D16): Spring Security's WebAuthn filter authenticates with its own principal and stores
 * the context request-only. The filter after it must translate the principal to {@link AuthenticatedUser} and save the
 * context to the session, or the next request is anonymous and every controller loses the user.
 */
class WebAuthnSessionPersistenceFilterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final SecurityContextRepository repository = mock(SecurityContextRepository.class);
    private final IdentityUserDetailsService userDetailsService = mock(IdentityUserDetailsService.class);
    private final IdentitySecurityConfiguration.WebAuthnSessionPersistenceFilter filter =
            new IdentitySecurityConfiguration.WebAuthnSessionPersistenceFilter(repository, userDetailsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void translatesTheWebAuthnPrincipalAndSavesTheContext() throws Exception {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "jordy", "admin", java.util.Set.of());
        when(userDetailsService.loadUserByUsername("jordy")).thenReturn(authenticatedUser);
        SecurityContext webAuthnContext = SecurityContextHolder.createEmptyContext();
        webAuthnContext.setAuthentication(webAuthnAuthentication("jordy"));
        SecurityContextHolder.setContext(webAuthnContext);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/webauthn");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        ArgumentCaptor<SecurityContext> saved = ArgumentCaptor.forClass(SecurityContext.class);
        verify(repository).saveContext(saved.capture(), org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response));
        assertSoftly(softly -> {
            softly.assertThat(saved.getValue().getAuthentication().getPrincipal())
                    .isInstanceOf(AuthenticatedUser.class);
            softly.assertThat(((AuthenticatedUser) saved.getValue().getAuthentication().getPrincipal()).username())
                    .isEqualTo("jordy");
            softly.assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isInstanceOf(AuthenticatedUser.class);
        });
    }

    @Test
    void doesNothingForAnonymousOrPasswordAuthentications() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        SecurityContextHolder.setContext(context);

        filter.doFilter(new MockHttpServletRequest("POST", "/login/webauthn"), new MockHttpServletResponse(),
                (req, res) -> {
                });

        context.setAuthentication(new UsernamePasswordAuthenticationToken("jordy", null, List.of()));
        SecurityContextHolder.setContext(context);
        filter.doFilter(new MockHttpServletRequest("POST", "/login/webauthn"), new MockHttpServletResponse(),
                (req, res) -> {
                });

        verify(repository, never()).saveContext(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsEveryOtherPath() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(webAuthnAuthentication("jordy"));
        SecurityContextHolder.setContext(context);

        filter.doFilter(new MockHttpServletRequest("GET", "/login/webauthn"), new MockHttpServletResponse(),
                (req, res) -> {
                });

        verify(repository, never()).saveContext(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static WebAuthnAuthentication webAuthnAuthentication(String username) {
        PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(USER_ID.toString().getBytes(StandardCharsets.UTF_8)))
                .name(username)
                .displayName(username)
                .build();

        return new WebAuthnAuthentication(entity, List.of(new SimpleGrantedAuthority("ROLE_admin")));
    }
}
