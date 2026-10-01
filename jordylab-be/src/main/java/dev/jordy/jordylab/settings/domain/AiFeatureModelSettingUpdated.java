package dev.jordy.jordylab.settings.domain;

/** A feature's saved model changed — the resolver drops its cached model for that feature. */
public record AiFeatureModelSettingUpdated(String featureKey, String modelId) {
}
