package dev.jordy.jordylab.settings.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class AiFeatureLastRunTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("6d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a");
    public static final String DEFAULT_FEATURE_KEY = "gamecatalog.enrichment";
    public static final String DEFAULT_PROVIDER = "openrouter";
    public static final String DEFAULT_MODEL = "anthropic/claude-haiku-4.5";
    public static final Instant DEFAULT_RAN_AT = Instant.parse("2026-10-01T12:00:00Z");

    public static AiFeatureLastRun aDefaultAiFeatureLastRun() {
        return anAiFeatureLastRun().build();
    }

    public static AiFeatureLastRun.AiFeatureLastRunBuilder anAiFeatureLastRun() {
        return AiFeatureLastRun.builder()
                .id(DEFAULT_ID)
                .featureKey(DEFAULT_FEATURE_KEY)
                .provider(DEFAULT_PROVIDER)
                .model(DEFAULT_MODEL)
                .fallbackUsed(false)
                .outcome(AiRunOutcome.SUCCESS)
                .ranAt(DEFAULT_RAN_AT);
    }
}
