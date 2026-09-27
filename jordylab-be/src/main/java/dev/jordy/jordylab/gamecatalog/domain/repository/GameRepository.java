package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    /** Backlog of games still awaiting AI enrichment; installed games first, deterministic data first. */
    @Query("SELECT g FROM Game g WHERE g.enrichmentStatus = :status "
            + "AND EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g) "
            + "AND (g.steamAppId IS NULL OR g.metadataStatus <> 'PENDING') "
            + "ORDER BY CASE WHEN EXISTS (SELECT 1 FROM GameInstallation gi2 WHERE gi2.game = g "
            + "AND gi2.presence = 'INSTALLED' AND gi2.source.enabled = true) THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findEnrichmentBacklog(@Param("status") EnrichmentStatus status, Pageable pageable);

    /** Backlog of Steam games still awaiting deterministic metadata; installed games first. */
    @Query("SELECT g FROM Game g WHERE g.metadataStatus = :status AND g.steamAppId IS NOT NULL "
            + "ORDER BY CASE WHEN EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g "
            + "AND gi.presence = 'INSTALLED' AND gi.source.enabled = true) THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findMetadataBacklog(@Param("status") MetadataStatus status, Pageable pageable);

    List<Game> findByEnrichmentStatus(EnrichmentStatus status);

    /**
     * Games still needing structured multiplayer data. Installed games first; games parked at the
     * attempt ceiling drop out until a manual refresh resets them.
     */
    @Query("SELECT g FROM Game g WHERE g.multiplayerSource = 'UNKNOWN' AND g.multiplayerAttempts < :maxAttempts "
            + "ORDER BY CASE WHEN EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g "
            + "AND gi.presence = 'INSTALLED' AND gi.source.enabled = true) THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findMultiplayerBacklog(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.multiplayerSource = 'UNKNOWN' AND g.multiplayerAttempts < :maxAttempts")
    long countMultiplayerBacklog(@Param("maxAttempts") int maxAttempts);
    List<Game> findByMetadataStatusOrderByCreatedDateAsc(MetadataStatus status, Pageable pageable);

    List<Game> findByMetadataStatus(MetadataStatus status);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.enrichmentStatus IN :statuses "
            + "AND EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g) "
            + "AND (g.steamAppId IS NULL OR g.metadataStatus <> 'PENDING')")
    long countEnrichmentBacklog(@Param("statuses") Collection<EnrichmentStatus> statuses);

    long countByMetadataStatusInAndSteamAppIdIsNotNull(Collection<MetadataStatus> statuses);

    Optional<Game> findBySteamAppId(String steamAppId);

    List<Game> findAllBySteamAppIdIn(Collection<String> steamAppIds);

    /**
     * Race-safe creation of a Steam game: the unique partial index on {@code steam_app_id} makes a
     * concurrent insert a no-op, and the caller re-reads the winner (FR-001, SC-004).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO gamecatalog.game (id, platform, steam_app_id, title, title_source, "
            + "enrichment_status, metadata_status, cover_status, banner_status, enrichment_attempts, "
            + "metadata_attempts, artwork_fallback_requests, created_at, updated_at) "
            + "VALUES (:id, :platform, :steamAppId, :title, :titleSource, 'PENDING', 'PENDING', 'PENDING', "
            + "'PENDING', 0, 0, 0, now(), now()) "
            + "ON CONFLICT (steam_app_id) WHERE steam_app_id IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertSteamGameIfAbsent(@Param("id") UUID id, @Param("platform") String platform,
            @Param("steamAppId") String steamAppId, @Param("title") String title,
            @Param("titleSource") String titleSource);

    Optional<Game> findByPlatformAndSteamAppId(String platform, String steamAppId);

    @Query("SELECT g FROM Game g WHERE g.platform = :platform AND LOWER(g.title) = LOWER(:title) ORDER BY g.createdDate")
    List<Game> findByPlatformAndLowercaseTitle(@Param("platform") String platform, @Param("title") String title,
            Pageable pageable);

    @Query("SELECT g FROM Game g WHERE "
            + "(EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "OR EXISTS (SELECT 1 FROM GameLibraryEntry e WHERE e.game = g AND e.removedAt IS NULL)) "
            + "AND (CAST(:installStatus AS String) = 'ALL' "
            + "OR (CAST(:installStatus AS String) = 'INSTALLED' AND EXISTS (SELECT 1 FROM GameInstallation gi2 "
            + "WHERE gi2.game = g AND gi2.presence = 'INSTALLED' AND gi2.source.enabled = true)) "
            + "OR (CAST(:installStatus AS String) = 'NOT_INSTALLED' AND NOT EXISTS (SELECT 1 FROM GameInstallation gi3 "
            + "WHERE gi3.game = g AND gi3.presence = 'INSTALLED' AND gi3.source.enabled = true) "
            + "AND EXISTS (SELECT 1 FROM GameLibraryEntry e2 WHERE e2.game = g AND e2.removedAt IS NULL))) "
            + "AND (:platform IS NULL OR g.platform = :platform) "
            + "AND (CAST(:host AS String) IS NULL OR EXISTS (SELECT 1 FROM GameInstallation hi WHERE hi.game = g "
            + "AND hi.presence = 'INSTALLED' AND hi.source.enabled = true "
            + "AND hi.source.hostname = CAST(:host AS String))) "
            + "AND (CAST(:search AS String) IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) "
            + "AND (:librarySources IS NULL OR "
            + "  (EXISTS (SELECT 1 FROM GameLibraryEntry eo WHERE eo.game = g AND eo.removedAt IS NULL "
            + "AND eo.librarySource = 'OWNED') AND 'OWNED' IN :librarySources) "
            + "  OR (NOT EXISTS (SELECT 1 FROM GameLibraryEntry eo2 WHERE eo2.game = g AND eo2.removedAt IS NULL "
            + "AND eo2.librarySource = 'OWNED') AND EXISTS (SELECT 1 FROM GameLibraryEntry ef WHERE ef.game = g "
            + "AND ef.removedAt IS NULL AND ef.librarySource = 'FAMILY') AND 'FAMILY' IN :librarySources) "
            + "  OR (NOT EXISTS (SELECT 1 FROM GameLibraryEntry ea WHERE ea.game = g AND ea.removedAt IS NULL) "
            + "AND 'LOCAL' IN :librarySources)) "
            + "AND (:localMultiplayer IS NULL OR g.localMultiplayer = :localMultiplayer) "
            + "ORDER BY LOWER(g.title)")
    Page<Game> findVisibleGames(@Param("search") String search, @Param("platform") String platform,
            @Param("host") String host, @Param("installStatus") String installStatus,
            @Param("librarySources") Collection<String> librarySources,
            @Param("localMultiplayer") Boolean localMultiplayer, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id = :id AND "
            + "(EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "OR EXISTS (SELECT 1 FROM GameLibraryEntry e WHERE e.game = g AND e.removedAt IS NULL))")
    Optional<Game> findVisibleById(@Param("id") UUID id);

    @Query("SELECT DISTINCT g.platform FROM Game g WHERE "
            + "(EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "OR EXISTS (SELECT 1 FROM GameLibraryEntry e WHERE e.game = g AND e.removedAt IS NULL)) "
            + "ORDER BY g.platform")
    List<String> findVisiblePlatforms();

    @Query("SELECT DISTINCT gi.source.hostname FROM GameInstallation gi WHERE gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true ORDER BY gi.source.hostname")
    List<String> findVisibleHosts();

    @Query("SELECT g FROM Game g WHERE "
            + "(EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "OR EXISTS (SELECT 1 FROM GameLibraryEntry e WHERE e.game = g AND e.removedAt IS NULL)) "
            + "AND (g.enrichmentStatus = 'ENRICHED' OR g.metadataStatus = 'OK') "
            + "AND (CAST(:installStatus AS String) = 'ALL' "
            + "OR (CAST(:installStatus AS String) = 'INSTALLED' AND EXISTS (SELECT 1 FROM GameInstallation gi2 "
            + "WHERE gi2.game = g AND gi2.presence = 'INSTALLED' AND gi2.source.enabled = true)) "
            + "OR (CAST(:installStatus AS String) = 'NOT_INSTALLED' AND NOT EXISTS (SELECT 1 FROM GameInstallation gi3 "
            + "WHERE gi3.game = g AND gi3.presence = 'INSTALLED' AND gi3.source.enabled = true) "
            + "AND EXISTS (SELECT 1 FROM GameLibraryEntry e2 WHERE e2.game = g AND e2.removedAt IS NULL))) "
            + "AND (CAST(:titleSearch AS String) IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:titleSearch AS String), '%'))) "
            + "AND (CAST(:genre AS String) IS NULL OR LOWER(g.genre) = LOWER(CAST(:genre AS String))) "
            + "AND (CAST(:genresSearch AS String) IS NULL OR LOWER(g.genres) LIKE LOWER(CONCAT('%', CAST(:genresSearch AS String), '%'))) "
            + "AND (CAST(:developerSearch AS String) IS NULL OR LOWER(g.developer) LIKE LOWER(CONCAT('%', CAST(:developerSearch AS String), '%'))) "
            + "AND (:releaseYearMin IS NULL OR g.releaseYear >= :releaseYearMin) "
            + "AND (:releaseYearMax IS NULL OR g.releaseYear <= :releaseYearMax) "
            + "AND (:minLocalPlayers IS NULL OR g.maxLocalPlayers >= :minLocalPlayers) "
            + "AND (:onlineMultiplayer IS NULL OR g.onlineMultiplayer = :onlineMultiplayer) "
            + "AND (:singlePlayer IS NULL OR g.singlePlayer = :singlePlayer) "
            + "AND (:platforms IS NULL OR g.platform IN :platforms) "
            + "AND (:hosts IS NULL OR EXISTS (SELECT 1 FROM GameInstallation hi WHERE hi.game = g "
            + "AND hi.presence = 'INSTALLED' AND hi.source.enabled = true AND hi.source.hostname IN :hosts)) "
            + "AND (:librarySources IS NULL OR "
            + "  (EXISTS (SELECT 1 FROM GameLibraryEntry eo WHERE eo.game = g AND eo.removedAt IS NULL "
            + "AND eo.librarySource = 'OWNED') AND 'OWNED' IN :librarySources) "
            + "  OR (NOT EXISTS (SELECT 1 FROM GameLibraryEntry eo2 WHERE eo2.game = g AND eo2.removedAt IS NULL "
            + "AND eo2.librarySource = 'OWNED') AND EXISTS (SELECT 1 FROM GameLibraryEntry ef WHERE ef.game = g "
            + "AND ef.removedAt IS NULL AND ef.librarySource = 'FAMILY') AND 'FAMILY' IN :librarySources) "
            + "  OR (NOT EXISTS (SELECT 1 FROM GameLibraryEntry ea WHERE ea.game = g AND ea.removedAt IS NULL) "
            + "AND 'LOCAL' IN :librarySources)) "
            + "AND (:localMultiplayer IS NULL OR g.localMultiplayer = :localMultiplayer)")
    List<Game> findForChatFilter(@Param("titleSearch") String titleSearch, @Param("genre") String genre,
            @Param("genresSearch") String genresSearch, @Param("developerSearch") String developerSearch,
            @Param("releaseYearMin") Integer releaseYearMin, @Param("releaseYearMax") Integer releaseYearMax,
            @Param("minLocalPlayers") Integer minLocalPlayers, @Param("onlineMultiplayer") Boolean onlineMultiplayer,
            @Param("singlePlayer") Boolean singlePlayer, @Param("platforms") List<String> platforms,
            @Param("hosts") List<String> hosts, @Param("installStatus") String installStatus,
            @Param("librarySources") List<String> librarySources,
            @Param("localMultiplayer") Boolean localMultiplayer, Pageable pageable);
}
