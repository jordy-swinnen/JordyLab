package dev.jordy.jordylab.mcp;

import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The signed-in admin's Keycloak subject for the current connector call (research R7). It is the identifier the audit
 * line names and the rate limit keys on; it is not a name or an e-mail address.
 */
@UtilityClass
public class McpAdminIdentity {

    public static String currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getSubject();
        }
        if (authentication instanceof AbstractAuthenticationToken) {
            throw new IllegalStateException("The connector call carries no JWT subject");
        }
        throw new IllegalStateException("The connector call is not authenticated");
    }
}
