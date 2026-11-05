package dev.jordy.jordylab.mcp.service;

import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues the refresh token the connector's public client needs (spec 021 FR-002, contracts/oauth-authorization-server.md).
 *
 * <p>Spring Authorization Server's own {@code OAuth2RefreshTokenGenerator} returns no refresh token when a public client
 * uses the authorization-code grant. Claude's remote connector is a public client by design (no secret is copied into it),
 * so without this generator a connection would end when the 10-minute access token expires. The token is the same kind the
 * library issues elsewhere: a random value with the client's configured lifetime, rotated on every refresh. The client is
 * PKCE-bound and every call is checked against the account, which bounds what a leaked token can do.
 */
public final class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final Clock clock;
    private final StringKeyGenerator keyGenerator = KeyGenerators.string();

    public PublicClientRefreshTokenGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())
                || !AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())) {
            return null;
        }
        Instant issuedAt = clock.instant();
        Duration timeToLive = context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive();

        return new OAuth2RefreshToken(keyGenerator.generateKey(), issuedAt, issuedAt.plus(timeToLive));
    }
}
