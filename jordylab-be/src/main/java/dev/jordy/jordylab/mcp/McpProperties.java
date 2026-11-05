package dev.jordy.jordylab.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The connector's settings (spec 021, research R2 to R7). {@code publicBaseUrl} is the public origin and the issuer, so the
 * protected resource is {@code <publicBaseUrl>/mcp} and the authorization server advertises itself from the same value.
 * {@code audience} is the value the connector's tokens must carry. {@code toolCallsPerMinute} is the per-admin limit
 * (FR-020). The token lifetimes are the authorization server's (contracts/oauth-authorization-server.md): a short access
 * token and a rotating refresh token, so Claude stays connected across a deploy.
 */
@ConfigurationProperties(prefix = "jordylab.mcp")
public record McpProperties(String publicBaseUrl, String audience, int toolCallsPerMinute,
        Duration accessTokenTtl, Duration refreshTokenTtl) {

    public McpProperties {
        publicBaseUrl = withoutTrailingSlash(
                publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:8080" : publicBaseUrl.trim());
        audience = audience == null || audience.isBlank() ? "jordylab-mcp" : audience;
        toolCallsPerMinute = toolCallsPerMinute <= 0 ? 60 : toolCallsPerMinute;
        accessTokenTtl = accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                ? Duration.ofMinutes(10) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()
                ? Duration.ofDays(30) : refreshTokenTtl;
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
