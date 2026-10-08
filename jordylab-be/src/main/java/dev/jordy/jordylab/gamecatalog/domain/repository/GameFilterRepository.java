package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** The library grid's dynamic query (spec 013 US7), kept apart from the fixed queries of {@link GameRepository}. */
public interface GameFilterRepository {

    /** Visible games matching every active filter, in the filter's order. */
    Page<Game> findFiltered(GameFilter filter, Pageable pageable);

    /**
     * Visible games that match everything except the player count and whose local player count is unknown, so could
     * still fit: how many were left out of a "{@code minLocalPlayers}" result (spec 013 FR-004).
     */
    long countWithUnknownPlayerCount(GameFilter filter);
}
