package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.ACTIVE_LIBRARY_ENTRY;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.AVAILABLE_NOW;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.CONSOLE_ENTRY;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.VISIBLE;
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

public interface GameRepository extends JpaRepository<Game, UUID>, GameFilterRepository {

    /** Backlog of games still awaiting AI enrichment; available games first, deterministic data first. */
    @Query("SELECT g FROM Game g WHERE g.enrichmentStatus = :status "
            + "AND (EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g) OR " + CONSOLE_ENTRY + ") "
            + "AND (g.steamAppId IS NULL OR g.metadataStatus <> 'PENDING') "
            + "ORDER BY CASE WHEN " + AVAILABLE_NOW + " THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findEnrichmentBacklog(@Param("status") EnrichmentStatus status, Pageable pageable);

    /** Backlog of Steam games still awaiting deterministic metadata; available games first. */
    @Query("SELECT g FROM Game g WHERE g.metadataStatus = :status AND g.steamAppId IS NOT NULL "
            + "ORDER BY CASE WHEN " + AVAILABLE_NOW + " THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findMetadataBacklog(@Param("status") MetadataStatus status, Pageable pageable);

    List<Game> findByEnrichmentStatus(EnrichmentStatus status);

    /**
     * Visible games the auto-fill worker still has something to do for, games available right now first (spec 013
     * FR-019 to FR-022): Steam facts not fetched yet; facts or artwork never looked up, or looked up long enough ago to
     * try again while still incomplete; a description missing for a game that is installed or on a console. Games already
     * handled in this run are excluded, so a lookup that cannot succeed (a service that is not configured) cannot loop.
     */
    @Query("SELECT g.id FROM Game g WHERE " + VISIBLE + " AND g.id NOT IN :excludedIds AND ("
            + "(g.steamAppId IS NOT NULL AND g.metadataStatus = 'PENDING') "
            + "OR (g.steamAppId IS NULL AND (g.factsCheckedAt IS NULL OR (g.factsCheckedAt < :retryBefore "
            + "AND (g.genres IS NULL OR g.developer IS NULL OR g.releaseYear IS NULL)))) "
            + "OR (g.coverStatus IN ('PENDING', 'PLACEHOLDER') AND (g.artworkCheckedAt IS NULL "
            + "OR g.artworkCheckedAt < :retryBefore)) "
            + "OR (g.description IS NULL AND g.enrichmentStatus = 'PENDING' AND " + AVAILABLE_NOW + ")) "
            + "ORDER BY CASE WHEN " + AVAILABLE_NOW + " THEN 0 ELSE 1 END, g.createdDate")
    List<UUID> findAutoFillBacklog(@Param("retryBefore") java.time.Instant retryBefore,
            @Param("excludedIds") Collection<UUID> excludedIds, Pageable pageable);

    @Query("SELECT COUNT(g) > 0 FROM Game g WHERE g.id = :id AND " + AVAILABLE_NOW)
    boolean isAvailableNow(@Param("id") UUID id);

    /**
     * Games still needing structured multiplayer data. Available games first; games parked at the attempt ceiling drop
     * out until a manual refresh resets them.
     */
    @Query("SELECT g FROM Game g WHERE g.multiplayerSource = 'UNKNOWN' AND g.multiplayerAttempts < :maxAttempts "
            + "ORDER BY CASE WHEN " + AVAILABLE_NOW + " THEN 0 ELSE 1 END, g.createdDate")
    List<Game> findMultiplayerBacklog(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.multiplayerSource = 'UNKNOWN' AND g.multiplayerAttempts < :maxAttempts")
    long countMultiplayerBacklog(@Param("maxAttempts") int maxAttempts);
    List<Game> findByMetadataStatusOrderByCreatedDateAsc(MetadataStatus status, Pageable pageable);

    List<Game> findByMetadataStatus(MetadataStatus status);

    @Query("SELECT COUNT(g) FROM Game g WHERE g.enrichmentStatus IN :statuses "
            + "AND (EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g) OR " + CONSOLE_ENTRY + ") "
            + "AND (g.steamAppId IS NULL OR g.metadataStatus <> 'PENDING')")
    long countEnrichmentBacklog(@Param("statuses") Collection<EnrichmentStatus> statuses);

    long countByMetadataStatusInAndSteamAppIdIsNotNull(Collection<MetadataStatus> statuses);

    Optional<Game> findBySteamAppId(String steamAppId);

    Optional<Game> findByIgdbGameId(String igdbGameId);

    List<Game> findAllByTitleKeyOrderByCreatedDateAsc(String titleKey);

    List<Game> findAllBySteamAppIdIn(Collection<String> steamAppIds);

    /**
     * Race-safe creation of a Steam game: the unique partial index on {@code steam_app_id} makes a
     * concurrent insert a no-op, and the caller re-reads the winner (FR-001, SC-004).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO gamecatalog.game (id, steam_app_id, title, title_key, title_source, "
            + "enrichment_status, metadata_status, cover_status, banner_status, enrichment_attempts, "
            + "metadata_attempts, artwork_fallback_requests, created_at, updated_at) "
            + "VALUES (:id, :steamAppId, :title, :titleKey, :titleSource, 'PENDING', 'PENDING', 'PENDING', "
            + "'PENDING', 0, 0, 0, now(), now()) "
            + "ON CONFLICT (steam_app_id) WHERE steam_app_id IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertSteamGameIfAbsent(@Param("id") UUID id, @Param("steamAppId") String steamAppId,
            @Param("title") String title, @Param("titleKey") String titleKey, @Param("titleSource") String titleSource);

    @Query("SELECT g FROM Game g WHERE g.id = :id AND " + VISIBLE)
    Optional<Game> findVisibleById(@Param("id") UUID id);

    /** Platforms present in visible games: installed copies, the Steam library, and consoles. */
    @Query(value = "SELECT DISTINCT p.platform FROM ("
            + "SELECT gi.platform AS platform FROM gamecatalog.game_installation gi "
            + "JOIN gamecatalog.scan_source s ON s.id = gi.source_id WHERE gi.presence = 'INSTALLED' AND s.enabled = true "
            + "UNION SELECT 'Steam' WHERE EXISTS (SELECT 1 FROM gamecatalog.game_library_entry WHERE removed_at IS NULL) "
            + "UNION SELECT c.platform FROM gamecatalog.console c "
            + "JOIN gamecatalog.console_game_entry e ON e.console_id = c.id) p ORDER BY p.platform", nativeQuery = true)
    List<String> findVisiblePlatforms();

    /** Every visible game, the target of an admin's "refresh game data" run. */
    @Query("SELECT g.id FROM Game g WHERE " + VISIBLE + " ORDER BY LOWER(g.title)")
    List<UUID> findAllVisibleIds();

    /** Visible games whose description is not Steam's own: the only ones an AI run may regenerate (spec 013 FR-059). */
    @Query("SELECT g.id FROM Game g WHERE " + VISIBLE
            + " AND (g.descriptionSource IS NULL OR g.descriptionSource <> 'STEAM') ORDER BY LOWER(g.title)")
    List<UUID> findVisibleIdsWithoutStoreDescription();

    /** Games with an installed copy on the given source, enabled or not: what the source holds. */
    @Query("SELECT COUNT(g) FROM Game g WHERE EXISTS (SELECT 1 FROM GameInstallation si WHERE si.game = g "
            + "AND si.source.id = :sourceId AND si.presence = 'INSTALLED')")
    long countInstalledOnSource(@Param("sourceId") UUID sourceId);

    /**
     * Games that would stop being visible if the given source were turned off: installed on it and in no other visible
     * place (another enabled source, an active Steam library entry, a console). Nothing is deleted by turning a source off.
     */
    @Query("SELECT COUNT(g) FROM Game g WHERE EXISTS (SELECT 1 FROM GameInstallation si WHERE si.game = g "
            + "AND si.source.id = :sourceId AND si.presence = 'INSTALLED') "
            + "AND NOT EXISTS (SELECT 1 FROM GameInstallation oi WHERE oi.game = g AND oi.presence = 'INSTALLED' "
            + "AND oi.source.enabled = true AND oi.source.id <> :sourceId) "
            + "AND NOT " + ACTIVE_LIBRARY_ENTRY + " AND NOT " + CONSOLE_ENTRY)
    long countHiddenIfSourceDisabled(@Param("sourceId") UUID sourceId);
}
