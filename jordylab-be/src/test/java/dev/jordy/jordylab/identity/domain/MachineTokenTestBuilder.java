package dev.jordy.jordylab.identity.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

import static dev.jordy.jordylab.identity.domain.MachineTokenScope.INGEST;

@UtilityClass
class MachineTokenTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    public static final UUID DEFAULT_CREATED_BY = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    public static final String DEFAULT_NAME = "JordyBox";
    public static final String DEFAULT_TOKEN_HASH =
            "5f4dcc3b5aa765d61d8327deb882cf99e8c1a7b0d9f2e3a4b5c6d7e8f9a0b1c2";
    public static final Instant DEFAULT_LAST_USED_AT = Instant.parse("2026-11-03T08:00:00Z");

    public static MachineToken aDefaultMachineToken() {
        return aMachineToken().build();
    }

    public static MachineToken.MachineTokenBuilder aMachineToken() {
        return MachineToken.builder()
                .id(DEFAULT_ID)
                .name(DEFAULT_NAME)
                .tokenHash(DEFAULT_TOKEN_HASH)
                .scope(INGEST)
                .createdBy(DEFAULT_CREATED_BY);
    }
}
