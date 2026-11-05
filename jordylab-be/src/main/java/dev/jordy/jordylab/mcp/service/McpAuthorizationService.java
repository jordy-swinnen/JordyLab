package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.security.Principal;
import java.util.function.Consumer;

/**
 * Stores a principal the JDBC row mapper can deserialize (spec 021, research R6). Spring Authorization Server writes the
 * signed-in {@link Authentication} into the authorization's attributes, and the JDBC service serializes it with a
 * restrictive polymorphic-type allowlist that refuses the identity module's {@link AuthenticatedUser}. The wrapper swaps
 * that principal for the account's id and the same authorities, which the allowlist accepts; the remaining identity
 * fields are not needed after the authorization request.
 */
public final class McpAuthorizationService implements OAuth2AuthorizationService {

    private static final String PRINCIPAL_ATTRIBUTE = Principal.class.getName();

    private final OAuth2AuthorizationService delegate;

    public McpAuthorizationService(OAuth2AuthorizationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(withStorablePrincipal(authorization));
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private static OAuth2Authorization withStorablePrincipal(OAuth2Authorization authorization) {
        Object principal = authorization.getAttribute(PRINCIPAL_ATTRIBUTE);
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedUser account)) {
            return authorization;
        }
        Authentication storable = new UsernamePasswordAuthenticationToken(account.id().toString(), null,
                authentication.getAuthorities());
        Consumer<java.util.Map<String, Object>> attributes = values -> values.put(PRINCIPAL_ATTRIBUTE, storable);

        return OAuth2Authorization.from(authorization).attributes(attributes).build();
    }
}
