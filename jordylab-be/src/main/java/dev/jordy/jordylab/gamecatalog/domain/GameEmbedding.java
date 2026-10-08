package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Bookkeeping for one game's row in the semantic index (spec 013 research A4). The id is the game id. The
 * {@code embedding vector(1536)} column is deliberately not mapped: vectors are written and searched with native SQL
 * ({@code GameEmbeddingRepository}), so Hibernate never has to understand the pgvector type.
 */
@Entity
@Table(schema = "gamecatalog", name = "game_embedding")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameEmbedding extends BaseEntity<GameEmbedding> {

    @Id
    @Column(name = "game_id")
    private UUID id;

    private String model;

    private String contentHash;

    private Instant embeddedAt;

    /** Stale when the configured model or the game's document text changed since this row was written. */
    public boolean isStale(String currentModel, String currentContentHash) {
        return !model.equals(currentModel) || !contentHash.equals(currentContentHash);
    }

    public static class GameEmbeddingBuilder {
        public GameEmbedding build() {
            Preconditions.checkArgument(id != null, "game id is required");
            Preconditions.checkArgument(StringUtils.hasText(model), "model is required");
            Preconditions.checkArgument(StringUtils.hasText(contentHash), "contentHash is required");
            Preconditions.checkArgument(embeddedAt != null, "embeddedAt is required");

            return new GameEmbedding(id, model, contentHash, embeddedAt);
        }
    }
}
