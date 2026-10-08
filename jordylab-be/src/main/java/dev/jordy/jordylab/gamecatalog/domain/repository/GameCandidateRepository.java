package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.ACTIVE_LIBRARY_ENTRY;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.AVAILABLE_NOW;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.VISIBLE;

/**
 * LibBot's candidate queries (spec 013 research A5). They reuse the one visibility rule, so a disabled source hides its
 * games from LibBot exactly as it does from the grid. Only the columns needed to judge a game's facts are read; text such
 * as descriptions is loaded later, for the few games that are actually shown to the model.
 */
public interface GameCandidateRepository extends Repository<Game, UUID> {

    /** The facts of one visible game, enough for {@code FactMatcher} to judge it. */
    interface CandidateRow {
        UUID getId();

        String getTitle();

        String getGenres();

        String getDeveloper();

        Integer getReleaseYear();

        Integer getMaxLocalPlayers();

        Boolean getLocalMultiplayer();

        Boolean getSinglePlayer();

        Boolean getOnlineMultiplayer();
    }

    /** An emulated, installed copy on an enabled source: which machine holds it and whether its ROM launches. */
    interface EmulatedCopy {
        UUID getGameId();

        String getMachine();

        RomStatus getRomStatus();
    }

    /** A game's cosine similarity to a question, 1 meaning identical direction. */
    interface Similarity {
        UUID getGameId();

        double getScore();
    }

    /**
     * Visible games that satisfy the structural requirements (install status, platform, place). Facts about the games
     * themselves are judged in Java, where unknown stays distinct from failing. {@code platforms} and {@code places}
     * must never be empty (pass a placeholder) with the matching {@code any...} flag true; places are lower case.
     */
    @Query("SELECT g.id AS id, g.title AS title, COALESCE(g.genres, g.genre) AS genres, g.developer AS developer, "
            + "g.releaseYear AS releaseYear, g.maxLocalPlayers AS maxLocalPlayers, "
            + "g.localMultiplayer AS localMultiplayer, g.singlePlayer AS singlePlayer, "
            + "g.onlineMultiplayer AS onlineMultiplayer FROM Game g WHERE " + VISIBLE
            + " AND (CAST(:installStatus AS String) = 'ALL' "
            + "OR (CAST(:installStatus AS String) = 'INSTALLED' AND " + AVAILABLE_NOW + ") "
            + "OR (CAST(:installStatus AS String) = 'NOT_INSTALLED' AND NOT " + AVAILABLE_NOW + ")) "
            + "AND (:anyPlatform = true "
            + "OR EXISTS (SELECT 1 FROM GameInstallation pgi WHERE pgi.game = g AND pgi.platform IN :platforms "
            + "AND pgi.presence = 'INSTALLED' AND pgi.source.enabled = true) "
            + "OR ('Steam' IN :platforms AND " + ACTIVE_LIBRARY_ENTRY + ") "
            + "OR EXISTS (SELECT 1 FROM ConsoleGameEntry pce WHERE pce.game = g AND pce.console.platform IN :platforms)) "
            + "AND (:anyPlace = true "
            + "OR EXISTS (SELECT 1 FROM GameInstallation hgi WHERE hgi.game = g AND hgi.presence = 'INSTALLED' "
            + "AND hgi.source.enabled = true "
            + "AND LOWER(COALESCE(hgi.source.host.displayName, hgi.source.host.hostname)) IN :places) "
            + "OR EXISTS (SELECT 1 FROM ConsoleGameEntry hce WHERE hce.game = g AND LOWER(hce.console.name) IN :places)) "
            + "ORDER BY LOWER(g.title)")
    List<CandidateRow> findStructural(@Param("installStatus") String installStatus,
            @Param("anyPlatform") boolean anyPlatform, @Param("platforms") Collection<String> platforms,
            @Param("anyPlace") boolean anyPlace, @Param("places") Collection<String> places);

    /** Labels of every place a visible game can be: hosts holding installed copies, and consoles holding games. */
    @Query("SELECT DISTINCT COALESCE(h.displayName, h.hostname) FROM GameInstallation gi JOIN gi.source.host h "
            + "WHERE gi.presence = 'INSTALLED' AND gi.source.enabled = true "
            + "UNION SELECT DISTINCT c.name FROM ConsoleGameEntry e JOIN e.console c")
    List<String> findVisiblePlaceLabels();

    /** The largest group any visible game is known to support on one screen, or null when none is known. */
    @Query("SELECT MAX(g.maxLocalPlayers) FROM Game g WHERE " + VISIBLE)
    Integer findLargestKnownLocalGroup();

    @Query("SELECT COUNT(g) FROM Game g WHERE " + VISIBLE)
    long countVisible();

    /** Visible games among {@code ids}, in no particular order. */
    @Query("SELECT g FROM Game g WHERE g.id IN :ids AND " + VISIBLE)
    List<Game> findVisibleByIdIn(@Param("ids") Collection<UUID> ids);

    /** Word-based fallback when no vector search is possible; {@code tsQuery} is built from sanitised words joined by {@code |}. */
    @Query(value = "SELECT g.id AS gameId, ts_rank(to_tsvector('simple', coalesce(g.title, '') || ' ' "
            + "|| coalesce(g.genres, '') || ' ' || coalesce(g.genre, '') || ' ' || coalesce(g.developer, '') || ' ' "
            + "|| coalesce(g.description, '')), to_tsquery('simple', :tsQuery)) AS score FROM gamecatalog.game g "
            + "WHERE g.id IN (:ids) AND to_tsvector('simple', coalesce(g.title, '') || ' ' || coalesce(g.genres, '') "
            + "|| ' ' || coalesce(g.genre, '') || ' ' || coalesce(g.developer, '') || ' ' "
            + "|| coalesce(g.description, '')) @@ to_tsquery('simple', :tsQuery)", nativeQuery = true)
    List<Similarity> rankByWords(@Param("ids") Collection<UUID> ids, @Param("tsQuery") String tsQuery);

    /** The emulated copies among {@code ids} that are installed on an enabled source. */
    @Query("SELECT gi.game.id AS gameId, COALESCE(h.displayName, h.hostname) AS machine, gi.romStatus AS romStatus "
            + "FROM GameInstallation gi JOIN gi.source.host h WHERE gi.game.id IN :ids AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true AND gi.source.sourceType = 'EMUDECK'")
    List<EmulatedCopy> findEmulatedCopies(@Param("ids") Collection<UUID> ids);

    /** Games among {@code ids} that can be played without an emulator: a Steam copy installed on an enabled source. */
    @Query("SELECT DISTINCT gi.game.id FROM GameInstallation gi WHERE gi.game.id IN :ids AND gi.presence = 'INSTALLED' "
            + "AND gi.source.enabled = true AND gi.source.sourceType <> 'EMUDECK'")
    List<UUID> findIdsWithNativeCopy(@Param("ids") Collection<UUID> ids);

    /** Games among {@code ids} that are on a console. */
    @Query("SELECT DISTINCT e.game.id FROM ConsoleGameEntry e WHERE e.game.id IN :ids")
    List<UUID> findIdsOnConsole(@Param("ids") Collection<UUID> ids);
}
