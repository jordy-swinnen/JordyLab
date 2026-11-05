package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * The authorization request rules (spec 021 FR-002, FR-004; contracts/oauth-authorization-server.md).
 *
 * <p>The standard rules run first (client, redirect URI, scopes and the PKCE challenge). Then two connector rules apply:
 * only {@code S256} is accepted, and the signed-in account must still be an approved admin, read from the identity module
 * rather than trusted from the session. A guest who signs in is refused here, before any code exists.
 */
@Component
@RequiredArgsConstructor
public class McpAuthorizationRequestValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final String S256 = "S256";

    private final McpAccountStatusValidator accountStatusValidator;

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest = context.getAuthentication();
        if (!(authorizationRequest.getPrincipal() instanceof Authentication authentication)
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser account)) {
            // No session yet: the authorization filter in front of the endpoint sends the browser to /login (T074).
            return;
        }
        if (!S256.equals(authorizationRequest.getAdditionalParameters().get("code_challenge_method"))) {
            throw failure("invalid_request", "The connector accepts PKCE with S256 only.", authorizationRequest);
        }
        if (!accountStatusValidator.isActiveAdmin(account.id())) {
            throw failure("access_denied", "The connector is available to JordyLab admins only.", authorizationRequest);
        }
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException failure(String code, String description,
            OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(code, description, null), authorizationRequest);
    }
}
