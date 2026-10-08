package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class GameEmbeddingTestBuilder {

    public static final UUID DEFAULT_GAME_ID = UUID.fromString("11223344-5566-4778-8990-aabbccddeeff");
    public static final String DEFAULT_MODEL = "openai/text-embedding-3-small";
    public static final String DEFAULT_CONTENT_HASH = "sha256:0f1e2d3c";
    public static final Instant DEFAULT_EMBEDDED_AT = Instant.parse("2026-10-07T10:00:00Z");

    public static GameEmbedding aDefaultGameEmbedding() {
        return aGameEmbedding().build();
    }

    public static GameEmbedding.GameEmbeddingBuilder aGameEmbedding() {
        return GameEmbedding.builder()
                .id(DEFAULT_GAME_ID)
                .model(DEFAULT_MODEL)
                .contentHash(DEFAULT_CONTENT_HASH)
                .embeddedAt(DEFAULT_EMBEDDED_AT);
    }
}
