package dev.jordy.jordylab.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * {@code langfuse.*}: where AI traces are sent (Langfuse takes OpenTelemetry at {@code /api/public/otel}). Without both
 * keys nothing is exported (the exporter bean's condition in {@code LangfuseTracingConfiguration}). {@code captureContent} puts prompts and answers on the traces; it stays off unless asked
 * for, because those texts hold the library, the portfolio and the user's questions.
 */
@ConfigurationProperties(prefix = "langfuse")
public record LangfuseProperties(String publicKey, String secretKey, String baseUrl, boolean captureContent) {

    private static final String DEFAULT_BASE_URL = "https://cloud.langfuse.com";

    public String tracesEndpoint() {
        String base = StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;

        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/api/public/otel/v1/traces";
    }
}
