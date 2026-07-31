package dev.jordy.jordylab.fna.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class BriefingTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("44444444-0000-0000-0000-000000000004");
    public static final String DEFAULT_CONTENT = "Market briefing content";
    public static final String DEFAULT_MODEL_USED = "claude-sonnet-4-20250514";
    public static final Instant DEFAULT_GENERATED_AT = Instant.parse("2026-03-15T06:30:00Z");

    public static Briefing aDefaultBriefing() {
        return aBriefing().build();
    }

    public static Briefing.BriefingBuilder aBriefing() {
        return Briefing.builder()
                .id(DEFAULT_ID)
                .content(DEFAULT_CONTENT)
                .modelUsed(DEFAULT_MODEL_USED)
                .generatedAt(DEFAULT_GENERATED_AT);
    }
}
