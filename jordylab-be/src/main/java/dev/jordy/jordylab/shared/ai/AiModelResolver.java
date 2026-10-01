package dev.jordy.jordylab.shared.ai;

/**
 * Port: which gateway model a feature uses (006 research D4). {@code shared} ships a config-backed default; the
 * {@code settings} module provides the {@code @Primary} one backed by the AI Models page, so the module arrow stays
 * {@code settings → shared}.
 */
public interface AiModelResolver {

    /** The gateway (OpenRouter) model id for the feature, e.g. {@code anthropic/claude-haiku-4.5}. */
    String resolveModel(AiFeature feature);
}
