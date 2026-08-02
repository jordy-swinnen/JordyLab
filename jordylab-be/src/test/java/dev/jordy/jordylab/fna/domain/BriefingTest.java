package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BriefingTest {

    @Test
    void buildBriefing() {
        Instant generatedAt = Instant.parse("2026-03-15T06:30:00Z");
        Briefing briefing = Briefing.builder()
                .content("Market briefing content")
                .modelUsed("claude-sonnet-5")
                .generatedAt(generatedAt)
                .build();

        assertThat(briefing.getId()).isNotNull();
        assertThat(briefing.getContent()).isEqualTo("Market briefing content");
        assertThat(briefing.getModelUsed()).isEqualTo("claude-sonnet-5");
        assertThat(briefing.getGeneratedAt()).isEqualTo(generatedAt);
    }

    @Test
    void buildWithoutContent() {
        assertThatThrownBy(() -> Briefing.builder()
                .modelUsed("claude-sonnet-5")
                .generatedAt(Instant.parse("2026-03-15T06:30:00Z"))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankContent() {
        assertThatThrownBy(() -> Briefing.builder()
                .content("  ")
                .modelUsed("claude-sonnet-5")
                .generatedAt(Instant.parse("2026-03-15T06:30:00Z"))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutModelUsed() {
        assertThatThrownBy(() -> Briefing.builder()
                .content("Market briefing content")
                .generatedAt(Instant.parse("2026-03-15T06:30:00Z"))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankModelUsed() {
        assertThatThrownBy(() -> Briefing.builder()
                .content("Market briefing content")
                .modelUsed(" ")
                .generatedAt(Instant.parse("2026-03-15T06:30:00Z"))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutGeneratedAt() {
        assertThatThrownBy(() -> Briefing.builder()
                .content("Market briefing content")
                .modelUsed("claude-sonnet-5")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Briefing.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
