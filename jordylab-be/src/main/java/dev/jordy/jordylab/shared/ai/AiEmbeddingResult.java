package dev.jordy.jordylab.shared.ai;

import java.util.List;

/**
 * Outcome of {@link ResilientAiService#embed}: one vector per input text, in input order. Embeddings have no fallback
 * provider (the index must come from one model), so a failure is final until the next attempt.
 */
public record AiEmbeddingResult(boolean success, List<float[]> vectors, String provider, String model,
        String answeredModel, Integer inputTokens, ProviderFailureReason failureReason) {

    public static AiEmbeddingResult failure(String provider, String model, ProviderFailureReason reason) {
        return new AiEmbeddingResult(false, List.of(), provider, model, null, null, reason);
    }
}
