package dev.jordy.jordylab.shared.ai;

/**
 * Result of {@link ResilientAiService#callStructured}: the parsed and validated {@code value}, or null when no provider
 * produced a usable answer (then {@code call().failureReason()} says why, {@code INVALID_OUTPUT} when a model answered
 * but never in the requested shape). {@code call()} carries provider, requested and answering model and token usage.
 */
public record StructuredOutput<T>(T value, AiCallResult call) {

    public boolean success() {
        return value != null;
    }
}
