package dev.jordy.jordylab.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Platform-wide Spring Security configuration. Validates Keycloak-issued JWTs and maps
 * the realm's role claims ({@code realm_access.roles}) to Spring Security
 * {@code ROLE_*} authorities.
 *
 * <p>Authorization is deny-by-default and follows the access matrix: FNA and Settings
 * require {@code admin}; Game Catalog reads and chat are open to {@code admin} or
 * {@code guest}; Game Catalog writes, sources, library sync and the client download
 * require {@code admin}; the scan client keeps its {@code gamecatalog-scanner} device
 * role. Mobile release/download-link requests are open to {@code admin} or {@code guest};
 * publishing a release is restricted to the {@code mobile-release-publisher} service
 * account (spec 007 FR-004); the signed-download stream and
 * {@code /.well-known/assetlinks.json} are public — they are gated by their own signed
 * token or serve no sensitive data (spec 007 research D8, D13). Anything not listed is
 * refused.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jordylab.cors.allowed-origins:http://localhost:4200,http://localhost:4300,http://localhost:4400}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/metrics/**").hasRole("admin")
                .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/.well-known/assetlinks.json").permitAll()
                    .requestMatchers("/api/gamecatalog/ingest/scan", "/api/gamecatalog/ingest/check")
                    .hasRole("gamecatalog-scanner")
                    .requestMatchers("/api/gamecatalog/ingest/client").hasRole("admin")
                    .requestMatchers("/api/fna/**", "/api/settings/**").hasRole("admin")
                    .requestMatchers("/api/mobile/download/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mobile/releases").hasRole("mobile-release-publisher")
                    .requestMatchers(HttpMethod.GET, "/api/mobile/releases/latest")
                    .hasAnyRole("admin", "guest")
                    .requestMatchers(HttpMethod.POST, "/api/mobile/releases/*/download-link")
                    .hasAnyRole("admin", "guest")
                    .requestMatchers(HttpMethod.GET, "/api/gamecatalog/games/**",
                            "/api/gamecatalog/platforms", "/api/gamecatalog/hosts")
                    .hasAnyRole("admin", "guest")
                    .requestMatchers(HttpMethod.POST, "/api/gamecatalog/chat")
                    .hasAnyRole("admin", "guest")
                    .requestMatchers("/api/gamecatalog/**").hasRole("admin")
                    .requestMatchers("/api/**").denyAll()
                .anyRequest().denyAll())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Maps the Keycloak {@code realm_access.roles} claim to Spring Security
     * authorities with the {@code ROLE_} prefix, so {@code hasRole("foo")} matches
     * users in the realm role {@code foo}.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    private static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> roleList) {
                Collection<GrantedAuthority> authorities = new ArrayList<>(roleList.size());
                for (Object role : roleList) {
                    if (role instanceof String roleName) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                    }
                }
                return authorities;
            }
        }
        return List.of();
    }
}
