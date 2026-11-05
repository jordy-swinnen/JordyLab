package dev.jordy.jordylab.mcp.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Authenticates the connector's public client on its refresh request (spec 021, contracts/oauth-authorization-server.md).
 *
 * <p>The library's public-client converter only recognises the authorization-code request, which carries the PKCE
 * verifier; the refresh request carries no verifier, so without this converter the token endpoint refuses it with
 * {@code 401}. The client id and the absence of a secret are the whole public-client authentication; the refresh token
 * itself is the credential, and Spring Authorization Server's own provider still validates the client and the token.
 */
public final class McpRefreshClientAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())
                || !"/oauth2/token".equals(request.getRequestURI())
                || !AuthorizationGrantType.REFRESH_TOKEN.getValue()
                        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId)
                || StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
            return null;
        }

        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null,
                Map.of(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue()));
    }
}
