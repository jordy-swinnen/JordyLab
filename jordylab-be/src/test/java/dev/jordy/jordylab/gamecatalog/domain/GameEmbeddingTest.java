package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameEmbeddingTest {

    @Test
    void buildGameEmbedding() {
        GameEmbedding embedding = GameEmbeddingTestBuilder.aDefaultGameEmbedding();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(embedding.getId()).isEqualTo(GameEmbeddingTestBuilder.DEFAULT_GAME_ID);
            softly.assertThat(embedding.getModel()).isEqualTo(GameEmbeddingTestBuilder.DEFAULT_MODEL);
            softly.assertThat(embedding.getContentHash()).isEqualTo(GameEmbeddingTestBuilder.DEFAULT_CONTENT_HASH);
            softly.assertThat(embedding.getEmbeddedAt()).isEqualTo(GameEmbeddingTestBuilder.DEFAULT_EMBEDDED_AT);
        });
    }

    @Test
    void buildWithoutGameId() {
        assertThatThrownBy(() -> GameEmbeddingTestBuilder.aGameEmbedding().id(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutModel() {
        assertThatThrownBy(() -> GameEmbeddingTestBuilder.aGameEmbedding().model(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutContentHash() {
        assertThatThrownBy(() -> GameEmbeddingTestBuilder.aGameEmbedding().contentHash(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutEmbeddedAt() {
        assertThatThrownBy(() -> GameEmbeddingTestBuilder.aGameEmbedding().embeddedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isNotStaleWhenModelAndHashMatch() {
        assertThat(GameEmbeddingTestBuilder.aDefaultGameEmbedding().isStale(
                GameEmbeddingTestBuilder.DEFAULT_MODEL, GameEmbeddingTestBuilder.DEFAULT_CONTENT_HASH)).isFalse();
    }

    @Test
    void isStaleWhenTheModelChanged() {
        assertThat(GameEmbeddingTestBuilder.aDefaultGameEmbedding().isStale("another/model",
                GameEmbeddingTestBuilder.DEFAULT_CONTENT_HASH)).isTrue();
    }

    @Test
    void isStaleWhenTheDocumentTextChanged() {
        assertThat(GameEmbeddingTestBuilder.aDefaultGameEmbedding().isStale(GameEmbeddingTestBuilder.DEFAULT_MODEL,
                "sha256:changed")).isTrue();
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GameEmbedding.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
