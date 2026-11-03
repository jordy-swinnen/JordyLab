package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.rest.controller.MachineTokenAuthenticationFilter;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * The platform's security configuration after Keycloak (spec 019 D5, contracts/access-matrix.md).
 *
 * <p>Two chains: a stateless machine-token chain for the scanner ingest endpoints (the token filter arrives with US5)
 * and the browser session chain that carries the same URL matrix as before — {@code admin} for FNA, Settings and
 * identity administration; {@code admin} or the per-person grant {@code app-gamecatalog} for Game Catalog reads and
 * chat; deny-by-default everywhere else. Sessions are server-side (Spring Session JDBC, US3), CSRF is on for the
 * session chain with the SPA cookie/header names Angular already sends, and the machine-token chain is exempt.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentitySecurityConfiguration {

    @Bean
    public MachineTokenAuthenticationFilter machineTokenAuthenticationFilter(MachineTokenService machineTokenService) {
        return new MachineTokenAuthenticationFilter(machineTokenService);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain machineTokenFilterChain(HttpSecurity http,
            MachineTokenAuthenticationFilter machineTokenAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/api/gamecatalog/ingest/scan", "/api/gamecatalog/ingest/check")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/gamecatalog/ingest/check",
                                "/api/gamecatalog/ingest/scan")
                        .hasRole("gamecatalog-scanner")
                        .anyRequest().denyAll())
                .addFilterBefore(machineTokenAuthenticationFilter, AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain sessionFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            IdentityUserDetailsService identityUserDetailsService, IdentityProperties identityProperties)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Let Spring's error dispatch through (spec 011): otherwise every exception becomes a bare 403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("admin")
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/identity/auth/**").permitAll()
                        // E2E-only helper (TestSignInController, @Profile("e2e")); 404 in every other profile.
                        .requestMatchers("/api/identity/test/**").permitAll()
                        // Spring Security's WebAuthn ceremony endpoints (spec 019 D16): options and the assertion
                        // are public; enrolling a passkey requires a signed-in session.
                        .requestMatchers("/webauthn/authenticate/options", "/login/webauthn").permitAll()
                        .requestMatchers("/webauthn/register/options", "/webauthn/register").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/identity/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/identity/me").authenticated()
                        .requestMatchers("/api/identity/passkeys/**").authenticated()
                        .requestMatchers("/api/identity/users/**", "/api/identity/machines/**").hasRole("admin")
                        .requestMatchers("/api/gamecatalog/ingest/client").hasRole("admin")
                        .requestMatchers("/api/fna/**", "/api/settings/**").hasRole("admin")
                        .requestMatchers(HttpMethod.GET, "/api/gamecatalog/games/**",
                                "/api/gamecatalog/platforms", "/api/gamecatalog/places")
                        .hasAnyRole("admin", "app-gamecatalog")
                        .requestMatchers(HttpMethod.PUT, "/api/gamecatalog/games/*/mark",
                                "/api/gamecatalog/games/*/installations/*/rom-status")
                        .hasAnyRole("admin", "app-gamecatalog")
                        .requestMatchers("/api/gamecatalog/libbot/**").hasAnyRole("admin", "app-gamecatalog")
                        .requestMatchers("/api/gamecatalog/**").hasRole("admin")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().denyAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // The e2e-only sign-in helper is called by the Playwright setup, not a browser form.
                        .ignoringRequestMatchers("/api/identity/test/**"))
                .webAuthn(webAuthn -> webAuthn
                        .rpId(identityProperties.webauthn().rpId())
                        .rpName(identityProperties.webauthn().rpName())
                        .allowedOrigins(identityProperties.webauthn().allowedOrigins().toArray(String[]::new))
                        .disableDefaultRegistrationPage(true))
                // Spring Security's WebAuthn filter saves the context request-only; persist it so the passkey
                // sign-in survives the next request (spec 019 D16).
                .addFilterAfter(new WebAuthnSessionPersistenceFilter(identityUserDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The documented Spring Security SPA handler: render the XOR-masked token into the response (which writes the
     * {@code XSRF-TOKEN} cookie) but resolve plain values from the header, because Angular sends the raw cookie value.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            if (StringUtils.hasText(headerValue)) {
                return this.plain.resolveCsrfTokenValue(request, csrfToken);
            }

            return this.xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /**
     * Spring Security's WebAuthn filter authenticates with a {@link WebAuthnAuthentication} whose principal is a
     * {@code PublicKeyCredentialUserEntity}, and it saves the context request-only. This filter turns that into the
     * platform's {@link AuthenticatedUser} (so every controller's {@code CurrentUserResolver} works) and persists the
     * context in the session (spec 019 D16).
     */
    static final class WebAuthnSessionPersistenceFilter extends OncePerRequestFilter {

        private final SecurityContextRepository securityContextRepository;
        private final IdentityUserDetailsService identityUserDetailsService;

        WebAuthnSessionPersistenceFilter(IdentityUserDetailsService identityUserDetailsService) {
            this(new HttpSessionSecurityContextRepository(), identityUserDetailsService);
        }

        WebAuthnSessionPersistenceFilter(SecurityContextRepository securityContextRepository,
                IdentityUserDetailsService identityUserDetailsService) {
            this.securityContextRepository = securityContextRepository;
            this.identityUserDetailsService = identityUserDetailsService;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !"POST".equals(request.getMethod()) || !"/login/webauthn".equals(request.getRequestURI());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            chain.doFilter(request, response);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof WebAuthnAuthentication webAuthn)) {
                return;
            }
            UserDetails userDetails = identityUserDetailsService.loadUserByUsername(webAuthn.getName());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null,
                    userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
    }
}
