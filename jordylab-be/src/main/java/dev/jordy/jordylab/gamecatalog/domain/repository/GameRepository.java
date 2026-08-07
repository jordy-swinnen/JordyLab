package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findBySourceIdAndExternalRef(UUID sourceId, String externalRef);

    List<Game> findAllBySourceId(UUID sourceId);

    List<Game> findByEnrichmentStatusOrderByFirstSeenAtAsc(EnrichmentStatus status, Pageable pageable);

    List<Game> findByEnrichmentStatus(EnrichmentStatus status);

    List<Game> findByPresenceAndUninstalledAtBefore(Presence presence, Instant cutoff);

    @Query("SELECT g FROM Game g WHERE g.presence = 'INSTALLED' AND g.source.enabled = true "
            + "AND (:platform IS NULL OR g.platform = :platform) "
            + "AND (CAST(:search AS String) IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) "
            + "ORDER BY LOWER(g.title)")
    Page<Game> findVisibleGames(@Param("search") String search, @Param("platform") String platform, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id = :id AND g.presence = 'INSTALLED' AND g.source.enabled = true")
    Optional<Game> findVisibleById(@Param("id") UUID id);

    @Query("SELECT DISTINCT g.platform FROM Game g WHERE g.presence = 'INSTALLED' AND g.source.enabled = true "
            + "ORDER BY g.platform")
    List<String> findVisiblePlatforms();

    @Query("SELECT COUNT(g) FROM Game g WHERE g.source.id = :sourceId AND g.presence = 'INSTALLED'")
    long countInstalledBySourceId(@Param("sourceId") UUID sourceId);

    @Query("SELECT g FROM Game g WHERE g.presence = 'INSTALLED' AND g.source.enabled = true "
            + "AND g.enrichmentStatus = 'ENRICHED' "
            + "AND (CAST(:titleSearch AS String) IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:titleSearch AS String), '%'))) "
            + "AND (CAST(:genre AS String) IS NULL OR LOWER(g.genre) = LOWER(CAST(:genre AS String))) "
            + "AND (:minLocalPlayers IS NULL OR g.maxLocalPlayers >= :minLocalPlayers) "
            + "AND (:onlineMultiplayer IS NULL OR g.onlineMultiplayer = :onlineMultiplayer) "
            + "AND (:singlePlayer IS NULL OR g.singlePlayer = :singlePlayer) "
            + "AND (:platforms IS NULL OR g.platform IN :platforms)")
    List<Game> findForChatFilter(@Param("titleSearch") String titleSearch, @Param("genre") String genre,
            @Param("minLocalPlayers") Integer minLocalPlayers, @Param("onlineMultiplayer") Boolean onlineMultiplayer,
            @Param("singlePlayer") Boolean singlePlayer, @Param("platforms") List<String> platforms,
            Pageable pageable);
}
