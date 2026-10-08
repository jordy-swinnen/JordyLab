package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.VISIBLE;

/**
 * What is still missing from the visible library (spec 013 FR-021): games with no real cover, no description, or no row in
 * the search index. The same predicates count them and list them, so the number and the list can never disagree.
 */
public interface LibraryHealthRepository extends Repository<Game, UUID> {

    /** A game shows a placeholder, or has not been looked up yet. */
    String NO_COVER = "g.coverStatus IN ('PENDING', 'PLACEHOLDER', 'LOCAL_FALLBACK_REQUESTED')";
    String NO_DESCRIPTION = "(g.description IS NULL OR g.description = '')";
    String NOT_INDEXED = "NOT EXISTS (SELECT 1 FROM GameEmbedding e WHERE e.id = g.id)";

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
}
