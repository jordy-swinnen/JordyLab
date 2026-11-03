package dev.jordy.jordylab.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The connector's settings (spec 021, research R2 to R7). {@code publicBaseUrl} is the public origin, so the protected
 * resource is {@code <publicBaseUrl>/mcp}; {@code audience} is the value the connector's tokens must carry; and
 * {@code toolCallsPerMinute} is the per-admin limit (FR-020).
 */
@ConfigurationProperties(prefix = "jordylab.mcp")
public record McpProperties(String publicBaseUrl, String audience, int toolCallsPerMinute) {

    public McpProperties {
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:8080" : publicBaseUrl;
        audience = audience == null || audience.isBlank() ? "jordylab-mcp" : audience;
        toolCallsPerMinute = toolCallsPerMinute <= 0 ? 60 : toolCallsPerMinute;
    }
}
