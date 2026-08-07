package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import lombok.Getter;

@Getter
public class BriefingGenerationException extends RuntimeException {

    private final ProviderFailureReason failureReason;

    public BriefingGenerationException(ProviderFailureReason failureReason) {
        super("Briefing generation failed: " + failureReason);
        this.failureReason = failureReason;
    }
}
