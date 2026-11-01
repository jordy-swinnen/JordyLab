package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;

import java.time.Instant;
import java.util.Map;

/**
 * How complete the visible library is (spec 013 FR-021). The percentages are of {@code totalGames}. {@code playerCounts}
 * says where the local player counts come from and why the missing ones are missing (spec 014).
 */
public record LibraryHealthResponse(long totalGames, long gamesWithoutCover, long gamesWithoutDescription,
        long gamesPendingIndex, PlayerCounts playerCounts) {

    /**
     * The visible games flagged local multiplayer: how many there are, how many each source filled, and how many have
     * no count, by reason. {@code unknown} is the sum of {@code unknownByReason}.
     */
    public record PlayerCounts(long localMultiplayerGames, Map<PlayerCountSource, Long> filled, long unknown,
            Map<PlayerCountReason, Long> unknownByReason, SourceState theGamesDb) {
    }

    /** The second source's state: configured, paused until when and why, and the last monthly allowance seen. */
    public record SourceState(boolean configured, Instant pausedUntil, String pauseReason, Integer remainingAllowance) {
    }
}
