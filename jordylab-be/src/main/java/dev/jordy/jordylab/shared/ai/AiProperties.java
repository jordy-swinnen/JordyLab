package dev.jordy.jordylab.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * {@code jordylab.ai.*}: the OpenRouter gateway (primary), the Anthropic fallback and each feature's default model
 * (006 FR-011–FR-013, research D2).
 */
@ConfigurationProperties(prefix = "jordylab.ai")
public record AiProperties(int healthCheckTtlSeconds, int callTimeoutSeconds, Gateway gateway, Fallback fallback,
        Map<String, Feature> features) {

    public record Gateway(String baseUrl, String apiKey) {

        /** Without a key the gateway is skipped and every call goes straight to the fallback. */
        public boolean configured() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
        }
    }

    public record Fallback(String provider, String model) {
    }

    public record Feature(String model) {
    }

    public String defaultModel(AiFeature feature) {
        Feature configured = features == null ? null : features.get(feature.key());
        if (configured == null || !StringUtils.hasText(configured.model())) {
            throw new IllegalStateException("No default model configured for AI feature " + feature.key());
        }

        return configured.model();
    }
}
