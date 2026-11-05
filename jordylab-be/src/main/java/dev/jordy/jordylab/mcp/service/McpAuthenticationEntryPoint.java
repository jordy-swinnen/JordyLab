package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.mcp.McpProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers a call without a valid connector token with {@code 401} and the pointer to the protected resource metadata
 * (contracts/mcp-endpoint.md, section 2a). Claude starts sign-in only on this header and status.
 */
@Component
@RequiredArgsConstructor
public class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource/mcp";

    private final McpProperties mcpProperties;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String metadataUrl = mcpProperties.publicBaseUrl() + METADATA_PATH;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer resource_metadata=\"" + metadataUrl + "\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
