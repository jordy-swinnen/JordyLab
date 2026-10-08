package dev.jordy.jordylab.shared.ai;

public enum ProviderFailureReason {
    UNREACHABLE,
    TIMEOUT,
    RATE_LIMITED,
    AUTH_FAILED,
    /** The account has no credits left (HTTP 402) — OpenRouter: "Insufficient credits". */
    INSUFFICIENT_CREDITS,
    /** The gateway doesn't know the model id (OpenRouter: 400 "… is not a valid model ID", or 404). */
    MODEL_NOT_FOUND,
    /** No gateway key is configured, so the call (an embedding has no fallback) was never attempted. */
    NOT_CONFIGURED,
    /** The model answered, but not with the structured shape asked for, even after one repair attempt. */
    INVALID_OUTPUT,
    UNKNOWN
}
