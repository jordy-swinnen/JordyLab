package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class BriefingDtoTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000011");
    public static final Instant DEFAULT_GENERATED_AT = Instant.parse("2026-11-01T06:30:00Z");
    public static final String DEFAULT_CONTENT = "Hold the core positions; trim the banks.";
    public static final String DEFAULT_MODEL_USED = "anthropic/claude-sonnet-5";

    public static BriefingDto aDefaultBriefingDto() {
        return new BriefingDto(DEFAULT_ID, DEFAULT_GENERATED_AT, DEFAULT_CONTENT, DEFAULT_MODEL_USED);
    }
}
