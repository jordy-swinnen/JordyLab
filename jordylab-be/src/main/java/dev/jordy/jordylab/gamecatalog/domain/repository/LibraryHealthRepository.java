package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.VISIBLE;

/**
 * What is still missing from the visible library (spec 013 FR-021): games with no real cover, no description, or no row in
 * the search index, and (spec 014) local-multiplayer games without a player count. The same predicates count them and list
 * them, so the number and the list can never disagree.
 */
public interface LibraryHealthRepository extends Repository<Game, UUID> {

    /** A game shows a placeholder, or has not been looked up yet. */
    String NO_COVER = "g.coverStatus IN ('PENDING', 'PLACEHOLDER', 'LOCAL_FALLBACK_REQUESTED')";
    String NO_DESCRIPTION = "(g.description IS NULL OR g.description = '')";
    String NOT_INDEXED = "NOT EXISTS (SELECT 1 FROM GameEmbedding e WHERE e.id = g.id)";

    /** A visible game flagged local multiplayer: the games a player count is wanted for. */
    String LOCAL_MULTIPLAYER = VISIBLE + " AND g.localMultiplayer = true";
    String NO_PLAYER_COUNT = LOCAL_MULTIPLAYER + " AND g.maxLocalPlayers IS NULL";
    String STEAM_GAME = "g.steamAppId IS NOT NULL";
    String CONSOLE_GAME = "g.steamAppId IS NULL";

    /** An id and a title: enough to list an exception and link to it. */
    interface GameTitle {
        UUID getId();

        String getTitle();
    }

    @Query("SELECT COUNT(g) FROM Game g WHERE " + VISIBLE)
    long countVisible();

    @Query("SELECT COUNT(g) FROM Game g WHERE " + VISIBLE + " AND " + NO_COVER)
    long countWithoutCover();

    @Query("SELECT COUNT(g) FROM Game g WHERE " + VISIBLE + " AND " + NO_DESCRIPTION)
    long countWithoutDescription();

    @Query("SELECT COUNT(g) FROM Game g WHERE " + VISIBLE + " AND " + NOT_INDEXED)
    long countNotIndexed();

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + VISIBLE + " AND " + NO_COVER
            + " ORDER BY LOWER(g.title)")
    List<GameTitle> findWithoutCover(Pageable pageable);

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + VISIBLE + " AND " + NO_DESCRIPTION
            + " ORDER BY LOWER(g.title)")
    List<GameTitle> findWithoutDescription(Pageable pageable);

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + VISIBLE + " AND " + NOT_INDEXED
            + " ORDER BY LOWER(g.title)")
    List<GameTitle> findNotIndexed(Pageable pageable);

    @Query("SELECT COUNT(g) FROM Game g WHERE " + LOCAL_MULTIPLAYER)
    long countLocalMultiplayer();

    @Query("SELECT COUNT(g) FROM Game g WHERE " + LOCAL_MULTIPLAYER + " AND g.maxLocalPlayersSource = :source")
    long countPlayerCountFilledBy(@Param("source") PlayerCountSource source);

    @Query("SELECT COUNT(g) FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + STEAM_GAME)
    long countSteamWithoutPlayerCount();

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + STEAM_GAME
            + " ORDER BY LOWER(g.title)")
    List<GameTitle> findSteamWithoutPlayerCount(Pageable pageable);

    @Query("SELECT COUNT(g) FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + CONSOLE_GAME + " AND g.playerCountGap = :gap")
    long countConsoleWithPlayerCountGap(@Param("gap") PlayerCountGap gap);

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + CONSOLE_GAME
            + " AND g.playerCountGap = :gap ORDER BY LOWER(g.title)")
    List<GameTitle> findConsoleWithPlayerCountGap(@Param("gap") PlayerCountGap gap, Pageable pageable);

    @Query("SELECT COUNT(g) FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + CONSOLE_GAME + " AND g.playerCountGap IS NULL")
    long countConsoleNotLookedUp();

    @Query("SELECT g.id AS id, g.title AS title FROM Game g WHERE " + NO_PLAYER_COUNT + " AND " + CONSOLE_GAME
            + " AND g.playerCountGap IS NULL ORDER BY LOWER(g.title)")
    List<GameTitle> findConsoleNotLookedUp(Pageable pageable);
}
