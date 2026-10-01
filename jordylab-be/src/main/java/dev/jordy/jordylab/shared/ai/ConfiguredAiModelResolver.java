package dev.jordy.jordylab.shared.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Default {@link AiModelResolver}: the model from {@code jordylab.ai.features.<key>.model}. */
@Component
@RequiredArgsConstructor
class ConfiguredAiModelResolver implements AiModelResolver {

    private final AiProperties properties;

    @Override
    public String resolveModel(AiFeature feature) {
        return properties.defaultModel(feature);
    }
}
