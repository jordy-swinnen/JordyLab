package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.mcp.McpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the connector's JWT decoder: the platform's issuer checks plus the connector audience (research R2, R3). It is
 * a separate decoder, so the platform chain's tokens are not changed.
 *
 * <p>The decoder is built on first use, not at startup. Building it reads the issuer's configuration, so an eager build
 * would stop the application from starting while Keycloak is unreachable. A failed build is retried on the next call.
 */
@Component
@RequiredArgsConstructor
public class McpJwtDecoderFactory {

    private final McpProperties mcpProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    String issuerUri;

    public JwtDecoder create() {
        AtomicReference<JwtDecoder> built = new AtomicReference<>();
        return token -> {
            JwtDecoder decoder = built.get();
            if (decoder == null) {
                decoder = build();
                built.set(decoder);
            }
            return decoder.decode(token);
        };
    }

    private JwtDecoder build() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new McpAudienceValidator(mcpProperties.audience())));
        return decoder;
    }
}
