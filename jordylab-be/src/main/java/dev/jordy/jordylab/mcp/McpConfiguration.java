package dev.jordy.jordylab.mcp;

import dev.jordy.jordylab.mcp.service.McpAuthenticationEntryPoint;
import dev.jordy.jordylab.mcp.service.McpJwtDecoderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The connector's own security chain (research R2, R4). It runs before the platform chain and covers only {@code /mcp} and
 * the protected resource metadata. The connector token must carry the connector audience (R3) and the admin role
 * (FR-003). Realm roles map through the platform's JWT converter, so the role check is the same as in the web app.
 *
 * <p>The metadata document is Spring Security's own, configured here: it answers both well-known paths, and a second
 * handler would shadow the first, so the connector defines no controller of its own.
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpConfiguration {

    private static final String MCP_PATTERN = "/mcp/**";
    private static final String METADATA_PATTERN = "/.well-known/oauth-protected-resource/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http,
            McpProperties mcpProperties,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            McpJwtDecoderFactory decoderFactory,
            McpAuthenticationEntryPoint authenticationEntryPoint,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        String resource = mcpProperties.publicBaseUrl() + "/mcp";
        http.securityMatcher("/mcp", MCP_PATTERN, "/.well-known/oauth-protected-resource", METADATA_PATTERN)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/.well-known/oauth-protected-resource", METADATA_PATTERN).permitAll()
                        .requestMatchers("/mcp", MCP_PATTERN).hasRole("admin")
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder -> builder
                                .resource(resource)
                                .authorizationServer(issuerUri)
                                .scope("openid")
                                .bearerMethods(methods -> methods.clear())
                                .bearerMethod("header")
                                .resourceName("JordyLab")
                                .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt
                                .decoder(decoderFactory.create())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
