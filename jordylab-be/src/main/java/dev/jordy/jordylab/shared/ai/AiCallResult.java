package dev.jordy.jordylab.shared.ai;

public record AiCallResult(
        boolean success,
        String module,
        String provider,
        String model,
        String content,
        ProviderFailureReason failureReason
) {
    public static AiCallResult success(String module, String provider, String model, String content) {
        return new AiCallResult(true, module, provider, model, content, null);
    }

    public static AiCallResult failure(String module, String provider, String model, ProviderFailureReason reason) {
        return new AiCallResult(false, module, provider, model, null, reason);
    }
}
