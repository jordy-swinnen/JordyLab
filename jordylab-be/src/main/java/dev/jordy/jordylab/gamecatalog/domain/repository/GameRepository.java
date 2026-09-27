package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    List<Game> findByEnrichmentStatusOrderByCreatedDateAsc(EnrichmentStatus status, Pageable pageable);

    List<Game> findByEnrichmentStatus(EnrichmentStatus status);

    List<Game> findByMetadataStatusOrderByCreatedDateAsc(MetadataStatus status, Pageable pageable);

    List<Game> findByMetadataStatusAndSteamAppIdIsNotNull(MetadataStatus status, Pageable pageable);

    List<Game> findByMetadataStatus(MetadataStatus status);

    long countByEnrichmentStatusIn(Collection<EnrichmentStatus> statuses);

    long countByMetadataStatusInAndSteamAppIdIsNotNull(Collection<MetadataStatus> statuses);

    Optional<Game> findByPlatformAndSteamAppId(String platform, String steamAppId);

    @Query("SELECT g FROM Game g WHERE g.platform = :platform AND LOWER(g.title) = LOWER(:title) ORDER BY g.createdDate")
    List<Game> findByPlatformAndLowercaseTitle(@Param("platform") String platform, @Param("title") String title,
            Pageable pageable);

    @Query("SELECT g FROM Game g WHERE "
            + "EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "AND (:platform IS NULL OR g.platform = :platform) "
            + "AND (CAST(:host AS String) IS NULL OR EXISTS (SELECT 1 FROM GameInstallation hi WHERE hi.game = g "
            + "AND hi.presence = 'INSTALLED' AND hi.source.enabled = true "
            + "AND hi.source.hostname = CAST(:host AS String))) "
            + "AND (CAST(:search AS String) IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) "
            + "ORDER BY LOWER(g.title)")
    Page<Game> findVisibleGames(@Param("search") String search, @Param("platform") String platform,
            @Param("host") String host, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id = :id AND "
            + "EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true)")
    Optional<Game> findVisibleById(@Param("id") UUID id);

    @Query("SELECT DISTINCT g.platform FROM Game g WHERE "
            + "EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) ORDER BY g.platform")
    List<String> findVisiblePlatforms();

    @Query("SELECT DISTINCT gi.source.hostname FROM GameInstallation gi WHERE gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true ORDER BY gi.source.hostname")
    List<String> findVisibleHosts();

    @Query("SELECT g FROM Game g WHERE "
            + "EXISTS (SELECT 1 FROM GameInstallation gi WHERE gi.game = g AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true) "
            + "AND g.enrichmentStatus = 'ENRICHED' "
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
            + "AND hi.presence = 'INSTALLED' AND hi.source.enabled = true AND hi.source.hostname IN :hosts))")
    List<Game> findForChatFilter(@Param("titleSearch") String titleSearch, @Param("genre") String genre,
            @Param("genresSearch") String genresSearch, @Param("developerSearch") String developerSearch,
            @Param("releaseYearMin") Integer releaseYearMin, @Param("releaseYearMax") Integer releaseYearMax,
            @Param("minLocalPlayers") Integer minLocalPlayers, @Param("onlineMultiplayer") Boolean onlineMultiplayer,
            @Param("singlePlayer") Boolean singlePlayer, @Param("platforms") List<String> platforms,
            @Param("hosts") List<String> hosts, Pageable pageable);
}
