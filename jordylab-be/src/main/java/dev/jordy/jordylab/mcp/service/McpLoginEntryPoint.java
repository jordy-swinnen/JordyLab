package dev.jordy.jordylab.mcp.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends an unauthenticated authorization request to the SPA's existing login page (contracts/oauth-authorization-server.md,
 * login bridge). The exact request, path and query, travels in {@code returnUrl}, so after sign-in the browser loads the
 * authorization request again with the session cookie and Spring Authorization Server issues the code.
 *
 * <p>The login page loads a {@code /oauth2/} return path with a full page load (spec change S1): the path belongs to the
 * backend, not to the Angular router.
 */
public class McpLoginEntryPoint implements AuthenticationEntryPoint {

    private static final String LOGIN_PATH = "/login";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        String query = request.getQueryString();
        String requested = request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
        String returnUrl = URLEncoder.encode(requested, StandardCharsets.UTF_8);
        response.sendRedirect(LOGIN_PATH + "?returnUrl=" + returnUrl);
    }
}
