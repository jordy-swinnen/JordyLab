package dev.jordy.jordylab.mcp.service;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Authenticates the connector's public client on its refresh request (spec 021, contracts/oauth-authorization-server.md).
 *
 * <p>The library's public-client provider enforces the PKCE verifier on every {@code NONE} client authentication; a
 * refresh request has no verifier, so it would be refused. The refresh token is the credential here, and the client is
 * still checked against its registration ({@code NONE} must be allowed). The authorization-code request keeps the
 * library's PKCE path, because this provider only handles the {@code refresh_token} grant.
 */
public final class McpRefreshClientAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;

    public McpRefreshClientAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof OAuth2ClientAuthenticationToken clientAuthentication)
                || !ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())
                || !AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(
                        clientAuthentication.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        String clientId = (String) clientAuthentication.getPrincipal();
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null
                || !registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                    "The client is not registered for the connector.", null));
        }

        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
