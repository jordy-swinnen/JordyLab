package dev.jordy.jordylab.settings.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class AiFeatureLastRunTest {

    @Test
    void buildAiFeatureLastRun() {
        AiFeatureLastRun run = AiFeatureLastRunTestBuilder.aDefaultAiFeatureLastRun();

        assertSoftly(softly -> {
            softly.assertThat(run.getFeatureKey()).isEqualTo(AiFeatureLastRunTestBuilder.DEFAULT_FEATURE_KEY);
            softly.assertThat(run.getProvider()).isEqualTo(AiFeatureLastRunTestBuilder.DEFAULT_PROVIDER);
            softly.assertThat(run.getModel()).isEqualTo(AiFeatureLastRunTestBuilder.DEFAULT_MODEL);
            softly.assertThat(run.isFallbackUsed()).isFalse();
            softly.assertThat(run.getOutcome()).isEqualTo(AiRunOutcome.SUCCESS);
            softly.assertThat(run.getFailureReason()).isNull();
            softly.assertThat(run.getRanAt()).isEqualTo(AiFeatureLastRunTestBuilder.DEFAULT_RAN_AT);
        });
    }

    @Test
    void aSuccessfulRunNeverKeepsAFailureReason() {
        AiFeatureLastRun run = AiFeatureLastRunTestBuilder.anAiFeatureLastRun().failureReason("TIMEOUT").build();

        assertSoftly(softly -> softly.assertThat(run.getFailureReason()).isNull());
    }

    @Test
    void buildWithoutOutcome() {
        assertThatThrownBy(() -> AiFeatureLastRunTestBuilder.anAiFeatureLastRun().outcome(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordRunOverwritesTheLastRun() {
        AiFeatureLastRun run = AiFeatureLastRunTestBuilder.aDefaultAiFeatureLastRun();
        Instant later = Instant.parse("2026-10-01T13:00:00Z");

        run.recordRun("anthropic", "claude-sonnet-5", true, AiRunOutcome.FAILURE, "AUTH_FAILED", later);

        assertSoftly(softly -> {
            softly.assertThat(run.getProvider()).isEqualTo("anthropic");
            softly.assertThat(run.getModel()).isEqualTo("claude-sonnet-5");
            softly.assertThat(run.isFallbackUsed()).isTrue();
            softly.assertThat(run.getOutcome()).isEqualTo(AiRunOutcome.FAILURE);
            softly.assertThat(run.getFailureReason()).isEqualTo("AUTH_FAILED");
            softly.assertThat(run.getRanAt()).isEqualTo(later);
        });
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(AiFeatureLastRun.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
