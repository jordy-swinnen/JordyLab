package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibraryHealthRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryHealthResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlayerCountReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shows the admin what the auto-fill has not managed to fill in yet (spec 013 FR-021), so a gap is visible, not silent;
 * since spec 014 that includes where the local player counts come from and why the missing ones are missing.
 */
@Service
@RequiredArgsConstructor
public class LibraryHealthService {

    static final int MAX_LISTED_EXCEPTIONS = 200;

    /** What can be missing. */
    public enum Kind {
        COVER,
        DESCRIPTION,
        INDEX,
        PLAYER_COUNT
    }

    private final LibraryHealthRepository healthRepository;
    private final TheGamesDbClient theGamesDbClient;

    @Transactional(readOnly = true)
    public LibraryHealthResponse health() {
        return new LibraryHealthResponse(healthRepository.countVisible(), healthRepository.countWithoutCover(),
                healthRepository.countWithoutDescription(), healthRepository.countNotIndexed(), playerCounts());
    }

    @Transactional(readOnly = true)
    public HealthExceptionsResponse exceptions(Kind kind) {
        return exceptions(kind, null);
    }

    /** {@code reason} narrows the player-count list to one reason; the other kinds have no reasons and ignore it. */
    @Transactional(readOnly = true)
    public HealthExceptionsResponse exceptions(Kind kind, PlayerCountReason reason) {
        if (kind == Kind.PLAYER_COUNT) {
            return playerCountExceptions(reason);
        }
        PageRequest firstPage = PageRequest.of(0, MAX_LISTED_EXCEPTIONS);
        List<LibraryHealthRepository.GameTitle> games = switch (kind) {
            case COVER -> healthRepository.findWithoutCover(firstPage);
            case DESCRIPTION -> healthRepository.findWithoutDescription(firstPage);
            case INDEX -> healthRepository.findNotIndexed(firstPage);
            case PLAYER_COUNT -> List.of();
        };
        long total = switch (kind) {
            case COVER -> healthRepository.countWithoutCover();
            case DESCRIPTION -> healthRepository.countWithoutDescription();
            case INDEX -> healthRepository.countNotIndexed();
            case PLAYER_COUNT -> 0L;
        };

        return new HealthExceptionsResponse(kind.name(), total, games.stream()
                .map(game -> new HealthExceptionsResponse.HealthException(game.getId(), game.getTitle(), null)).toList());
    }

    private LibraryHealthResponse.PlayerCounts playerCounts() {
        Map<PlayerCountSource, Long> filled = new EnumMap<>(PlayerCountSource.class);
        for (PlayerCountSource source : PlayerCountSource.values()) {
            filled.put(source, healthRepository.countPlayerCountFilledBy(source));
        }
        Map<PlayerCountReason, Long> unknownByReason = new EnumMap<>(PlayerCountReason.class);
        for (PlayerCountReason reason : PlayerCountReason.values()) {
            unknownByReason.put(reason, countOf(reason));
        }
        long unknown = unknownByReason.values().stream().mapToLong(Long::longValue).sum();

        return new LibraryHealthResponse.PlayerCounts(healthRepository.countLocalMultiplayer(), filled, unknown,
                healthRepository.countFlagUnknown(), unknownByReason, sourceState());
    }

    private LibraryHealthResponse.SourceState sourceState() {
        TheGamesDbClient.ClientState state = theGamesDbClient.state();

        return new LibraryHealthResponse.SourceState(state.configured(), state.pausedUntil(),
                state.pauseReason() == null ? null : state.pauseReason().name(), state.remainingAllowance());
    }

    private HealthExceptionsResponse playerCountExceptions(PlayerCountReason onlyReason) {
        List<PlayerCountReason> reasons = onlyReason == null ? List.of(PlayerCountReason.values()) : List.of(onlyReason);
        List<HealthExceptionsResponse.HealthException> listed = new ArrayList<>();
        long total = 0;
        for (PlayerCountReason reason : reasons) {
            total += countOf(reason);
            int room = MAX_LISTED_EXCEPTIONS - listed.size();
            if (room > 0) {
                findOf(reason, PageRequest.of(0, room)).forEach(game -> listed.add(
                        new HealthExceptionsResponse.HealthException(game.getId(), game.getTitle(), reason)));
            }
        }

        return new HealthExceptionsResponse(Kind.PLAYER_COUNT.name(), total, listed);
    }

    private long countOf(PlayerCountReason reason) {
        return switch (reason) {
            case STEAM_NO_SOURCE -> healthRepository.countSteamWithoutPlayerCount();
            case ENTRIES_DISAGREE -> healthRepository.countConsoleWithPlayerCountGap(PlayerCountGap.ENTRIES_DISAGREE);
            case NO_MATCH -> healthRepository.countConsoleWithPlayerCountGap(PlayerCountGap.NO_MATCH);
            case COUNT_BELOW_TWO -> healthRepository.countConsoleWithPlayerCountGap(PlayerCountGap.COUNT_BELOW_TWO);
            case CONSOLE_NOT_LISTED -> healthRepository.countConsoleWithPlayerCountGap(PlayerCountGap.CONSOLE_NOT_LISTED);
            case NOT_LOOKED_UP_YET -> healthRepository.countConsoleNotLookedUp();
        };
    }

    private List<LibraryHealthRepository.GameTitle> findOf(PlayerCountReason reason, PageRequest page) {
        return switch (reason) {
            case STEAM_NO_SOURCE -> healthRepository.findSteamWithoutPlayerCount(page);
            case ENTRIES_DISAGREE -> healthRepository.findConsoleWithPlayerCountGap(PlayerCountGap.ENTRIES_DISAGREE, page);
            case NO_MATCH -> healthRepository.findConsoleWithPlayerCountGap(PlayerCountGap.NO_MATCH, page);
            case COUNT_BELOW_TWO -> healthRepository.findConsoleWithPlayerCountGap(PlayerCountGap.COUNT_BELOW_TWO, page);
            case CONSOLE_NOT_LISTED -> healthRepository.findConsoleWithPlayerCountGap(PlayerCountGap.CONSOLE_NOT_LISTED, page);
            case NOT_LOOKED_UP_YET -> healthRepository.findConsoleNotLookedUp(page);
        };
    }
}
