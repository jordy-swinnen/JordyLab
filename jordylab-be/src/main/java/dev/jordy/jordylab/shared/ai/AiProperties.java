package dev.jordy.jordylab.shared.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

/**
 * {@code jordylab.ai.*}: the OpenRouter gateway (primary), the Anthropic fallback and each feature's default model
 * (006 FR-011–FR-013, research D2).
 */
@ConfigurationProperties(prefix = "jordylab.ai")
public record AiProperties(int healthCheckTtlSeconds, int callTimeoutSeconds, Gateway gateway, Fallback fallback,
        Map<String, Feature> features, Embedding embedding) {

    public record Gateway(String baseUrl, String apiKey) {

        /** Without a key the gateway is skipped and every call goes straight to the fallback. */
        public boolean configured() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
        }
    }

    public record Fallback(String provider, String model) {
    }

    /** {@code temperature} is optional: unset leaves the provider default, set it where determinism matters. */
    public record Feature(String model, Double temperature) {
    }

    /** The embedding model is configuration, not a selectable feature: changing it re-embeds every game (research A3). */
    public record Embedding(String model) {
    }

    public String embeddingModel() {
        if (embedding == null || !StringUtils.hasText(embedding.model())) {
            throw new IllegalStateException("No embedding model configured: set jordylab.ai.embedding.model");
        }

        return embedding.model();
    }

    public Optional<Double> temperature(AiFeature feature) {
        Feature configured = features == null ? null : features.get(feature.key());

        return configured == null ? Optional.empty() : Optional.ofNullable(configured.temperature());
    }

    public String defaultModel(AiFeature feature) {
        if (feature == AiFeature.GAMECATALOG_EMBEDDING) {
            return embeddingModel();
        }
        Feature configured = features == null ? null : features.get(feature.key());
        if (configured == null || !StringUtils.hasText(configured.model())) {
            throw new IllegalStateException("No default model configured for AI feature " + feature.key());
        }

        return configured.model();
    }
}
