package dev.jordy.jordylab.mcp;

import dev.jordy.jordylab.mcp.service.McpAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The connector's own security chain (research R2, R4). It runs before the platform's session chain and covers only
 * {@code /mcp} and the protected resource metadata. The token must carry the connector audience (R3) and the admin role
 * (FR-003), and every tool call re-checks the account through the advice (FR-004).
 *
 * <p>The metadata document is Spring Security's own: it answers both well-known paths, and a second handler would shadow
 * the first, so the connector defines no controller of its own. Its {@code authorization_servers} entry is the backend's
 * own issuer, the same value the authorization server advertises (contracts/mcp-endpoint.md).
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpConfiguration {

    private static final String MCP_PATTERN = "/mcp/**";
    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource";
    private static final String METADATA_PATTERN = METADATA_PATH + "/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http,
            McpProperties mcpProperties,
            JwtDecoder jwtDecoder,
            McpAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        String resource = mcpProperties.publicBaseUrl() + "/mcp";
        http.securityMatcher("/mcp", MCP_PATTERN, METADATA_PATH, METADATA_PATTERN)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(METADATA_PATH, METADATA_PATTERN).permitAll()
                        .requestMatchers("/mcp", MCP_PATTERN).hasRole("admin")
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder -> builder
                                .resource(resource)
                                .authorizationServer(mcpProperties.publicBaseUrl())
                                .scope("jordylab.mcp")
                                .bearerMethods(methods -> methods.clear())
                                .bearerMethod("header")
                                .resourceName("JordyLab")
                                .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(connectorJwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * The authorization server puts the account's role key in {@code roles} (contracts/oauth-authorization-server.md);
     * the chain reads it as {@code ROLE_admin}, so a guest's token is refused with {@code 403} even if it was somehow issued.
     */
    @Bean
    public JwtAuthenticationConverter connectorJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }
}
