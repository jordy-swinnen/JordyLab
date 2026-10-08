package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameMarkRepository extends JpaRepository<GameMark, UUID> {

    /** One row of vote totals: how many users chose {@code mark} for {@code gameId}. */
    interface VoteTotal {
        UUID getGameId();

        MarkType getMark();

        long getTotal();
    }

    Optional<GameMark> findByGameIdAndUserSubject(UUID gameId, String userSubject);

    /** One game and the mark a person gave it. */
    interface OwnMark {
        UUID getGameId();

        MarkType getMark();
    }

    @Query("SELECT m.game.id AS gameId, m.mark AS mark FROM GameMark m WHERE m.userSubject = :userSubject")
    List<OwnMark> findOwnMarks(@Param("userSubject") String userSubject);

    List<GameMark> findAllByGameIdInAndUserSubject(Collection<UUID> gameIds, String userSubject);

    @Query("SELECT m.game.id AS gameId, m.mark AS mark, COUNT(m) AS total FROM GameMark m "
            + "WHERE m.game.id IN :gameIds GROUP BY m.game.id, m.mark")
    List<VoteTotal> countVotes(@Param("gameIds") Collection<UUID> gameIds);

    @Modifying
    @Query("DELETE FROM GameMark m WHERE m.userSubject = :userSubject")
    int deleteAllByUserSubject(@Param("userSubject") String userSubject);

    @Modifying
    @Query("DELETE FROM GameMark m WHERE m.game.id = :gameId")
    int deleteAllByGameId(@Param("gameId") UUID gameId);

    /**
     * Sets this user's mark in one statement, so two taps at the same moment end as one row holding the last mark rather
     * than a unique-index failure.
     */
    @Modifying
    @Query(value = "INSERT INTO gamecatalog.game_mark (id, game_id, user_subject, mark, created_at, updated_at) "
            + "VALUES (:id, :gameId, :userSubject, :mark, now(), now()) "
            + "ON CONFLICT (game_id, user_subject) DO UPDATE SET mark = EXCLUDED.mark, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("gameId") UUID gameId, @Param("userSubject") String userSubject,
            @Param("mark") String mark);

    @Modifying
    @Query("DELETE FROM GameMark m WHERE m.game.id = :gameId AND m.userSubject = :userSubject")
    int deleteByGameIdAndUserSubject(@Param("gameId") UUID gameId, @Param("userSubject") String userSubject);
}
