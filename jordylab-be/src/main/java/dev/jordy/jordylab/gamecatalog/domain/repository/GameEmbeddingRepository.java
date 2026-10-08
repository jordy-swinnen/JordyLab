package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.GameEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The vector itself is only ever touched with native SQL: Hibernate does not map the {@code vector(1536)} column.
 * {@code vectorLiteral} is pgvector's text form, for example {@code [0.1,0.2,0.3]}.
 */
public interface GameEmbeddingRepository extends JpaRepository<GameEmbedding, UUID> {

    @Modifying
    @Query(value = "INSERT INTO gamecatalog.game_embedding (game_id, model, content_hash, embedding, embedded_at, "
            + "created_at, updated_at) VALUES (:gameId, :model, :contentHash, CAST(:vectorLiteral AS vector), "
            + ":embeddedAt, now(), now()) ON CONFLICT (game_id) DO UPDATE SET model = EXCLUDED.model, "
            + "content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding, "
            + "embedded_at = EXCLUDED.embedded_at, updated_at = now()", nativeQuery = true)
    int upsert(@Param("gameId") UUID gameId, @Param("model") String model, @Param("contentHash") String contentHash,
            @Param("vectorLiteral") String vectorLiteral, @Param("embeddedAt") Instant embeddedAt);

    /** Games that have no embedding yet or whose embedding was made with another model. Hash staleness is checked per game. */
    @Query(value = "SELECT g.id FROM gamecatalog.game g LEFT JOIN gamecatalog.game_embedding e ON e.game_id = g.id "
            + "WHERE e.game_id IS NULL OR e.model <> :model", nativeQuery = true)
    List<UUID> findGameIdsMissingOrEmbeddedWithAnotherModel(@Param("model") String model);

    /** Cosine similarity of the embedded games among {@code ids} to a query vector, made with {@code model} only. */
    @Query(value = "SELECT e.game_id AS gameId, 1 - (e.embedding <=> CAST(:queryVector AS vector)) AS score "
            + "FROM gamecatalog.game_embedding e WHERE e.game_id IN (:ids) AND e.model = :model", nativeQuery = true)
    List<GameCandidateRepository.Similarity> similarityTo(@Param("queryVector") String queryVector,
            @Param("ids") java.util.Collection<UUID> ids, @Param("model") String model);

    @Query("SELECT COUNT(e) FROM GameEmbedding e WHERE e.model = :model")
    long countByModel(@Param("model") String model);
}
