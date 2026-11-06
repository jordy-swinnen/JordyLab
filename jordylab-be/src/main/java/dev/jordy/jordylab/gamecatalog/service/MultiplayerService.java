package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.PlatformEntry;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Derives structured local-multiplayer data from deterministic sources — Steam store categories
 * for Steam games, IGDB for ROMs and as a fallback when Steam has no category data, and TheGamesDB as the last
 * source for the player count of console games flagged local multiplayer (spec 014). Never infers
 * from the LLM: a game with no data stays {@link MultiplayerSource#UNKNOWN} and is parked after
 * the configured attempt ceiling (re-checkable via a manual refresh).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiplayerService {

    private final GameRepository gameRepository;
    private final SteamAppDetailsClient steamAppDetailsClient;
    private final IgdbClient igdbClient;
    private final TheGamesDbClient theGamesDbClient;
    private final GamePlatformService platformService;
    private final GameCatalogProperties properties;

    /** How a derivation ended: complete, not complete (counts as an attempt), or postponed (counts as nothing). */
    private enum Outcome {
        RESOLVED,
        UNRESOLVED,
        DEFERRED
    }

    /** Lookups already made in one batch, so a title asked twice costs one call. */
    private record BatchCache(Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle,
            Map<String, TheGamesDbClient.PlayerCountLookup> playerCountByTitleAndConsole) {

        static BatchCache fresh() {
            return new BatchCache(new HashMap<>(), new HashMap<>());
        }
    }

    /** Derives multiplayer data for up to {@code maxGames} backlog games. Invoked inline — no scheduler. */
    public int derivePending(int maxGames) {
        int maxAttempts = properties.metadata().maxAttempts();
        List<Game> backlog = gameRepository.findMultiplayerBacklog(maxAttempts, PageRequest.of(0, maxGames));
        if (backlog.isEmpty()) {
            return 0;
        }
        log.info("Deriving multiplayer data for {} game(s)", backlog.size());
        BatchCache cache = BatchCache.fresh();
        int processed = 0;
        for (Game game : backlog) {
            Outcome outcome;
            try {
                outcome = deriveOne(game, cache, false);
            } catch (SteamRateLimitedException rateLimited) {
                log.warn("Steam store rate limit hit; pausing multiplayer batch after {} game(s)", processed);
                break;
            }
            if (outcome == Outcome.UNRESOLVED) {
                game.recordMultiplayerFailure();
            }
            gameRepository.save(game);
            processed++;
        }

        return processed;
    }

    /** Force re-derives one game's multiplayer data, clearing its attempt counter first. */
    public void refresh(Game game) {
        game.resetMultiplayerForRetry();
        if (deriveOne(game, BatchCache.fresh(), true) == Outcome.UNRESOLVED) {
            game.recordMultiplayerFailure();
        }
        gameRepository.save(game);
    }

    /**
     * Re-derives one game as part of a bulk run: the attempt counter is reset (the admin asked for a full look) but Steam
     * is not fetched again, because the facts pass of the same run just did.
     */
    public void rederive(UUID gameId) {
        Optional<Game> snapshot = gameRepository.findById(gameId);
        if (snapshot.isEmpty()) {
            return;
        }
        Game game = snapshot.get();
        game.resetMultiplayerForRetry();
        if (deriveOne(game, BatchCache.fresh(), false) == Outcome.UNRESOLVED) {
            game.recordMultiplayerFailure();
        }
        gameRepository.save(game);
    }

    /**
     * {@code forceSteamCheck} distinguishes an explicit manual refresh (always re-checks Steam,
     * since the caller asked for a fresh look) from the automatic backlog pass, where a Steam
     * game only reaches here after {@code SteamMetadataService} already checked its categories
     * for the exact same app ID during the metadata pass that always runs just before this one —
     * re-fetching there would be a guaranteed-redundant Steam store call.
     */
    private Outcome deriveOne(Game game, BatchCache cache, boolean forceSteamCheck) {
        boolean steamAlreadyChecked = !forceSteamCheck && game.getMetadataStatus() != MetadataStatus.PENDING;
        if (StringUtils.hasText(game.getSteamAppId()) && !steamAlreadyChecked) {
            Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch(game.getSteamAppId());
            if (metadata.isPresent()) {
                SteamAppDetailsClient.MultiplayerFacts facts = metadata.get().multiplayer();
                if (facts != null && facts.categoriesPresent()) {
                    game.applyDeterministicMultiplayerFlags(facts.singlePlayer(), facts.onlineMultiplayer());
                    // Steam categories carry no player counts, so maxLocalPlayers stays untouched.
                    game.applyDeterministicMultiplayer(facts.localMultiplayer(), facts.splitScreen(), null,
                            MultiplayerSource.STEAM);
                }
            }
            // No Steam category data (or the fetch failed) → fill what is missing from IGDB.
        }
        if (!needsMultiplayerData(game)) {
            return Outcome.RESOLVED;
        }
        if (fillFromIgdb(game, cache.igdbModeByTitle())) {
            return Outcome.RESOLVED;
        }

        return fillFromTheGamesDb(game, cache.playerCountByTitleAndConsole());
    }

    /** True while a game that can have local multiplayer is still missing flags or its player count. */
    private boolean needsMultiplayerData(Game game) {
        return game.getMaxLocalPlayers() == null
                && (game.getLocalMultiplayer() == null || game.getLocalMultiplayer());
    }

    /**
     * Fills whatever is still unknown from IGDB (flags and player counts) without overwriting facts a deterministic
     * source already set. Resolves only when the game is complete afterwards: a mode without player counts still counts
     * as an attempt, so games no source has a count for park instead of being looked up forever.
     */
    private boolean fillFromIgdb(Game game, Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle) {
        if (!igdbClient.isConfigured()) {
            return false;
        }
        Optional<IgdbClient.MultiplayerMode> knownId = modeByIgdbId(game);
        Optional<IgdbClient.MultiplayerMode> mode = knownId.isPresent() ? knownId
                : modeByTitle(game, igdbModeByTitle);
        if (mode.isEmpty()) {
            return false;
        }
        game.applyDeterministicMultiplayerFlags(null, mode.get().onlineMultiplayer());
        game.applyFillOnlyMultiplayer(mode.get().localMultiplayer(), mode.get().splitScreen(),
                mode.get().maxLocalPlayers(), MultiplayerSource.IGDB);

        return !needsMultiplayerData(game);
    }

    /**
     * A console game added through IGDB already carries its IGDB id; use it instead of searching by title again. A
     * missing or malformed id falls through to the title search.
     */
    private Optional<IgdbClient.MultiplayerMode> modeByIgdbId(Game game) {
        if (!StringUtils.hasText(game.getIgdbGameId())) {
            return Optional.empty();
        }
        try {
            long igdbGameId = Long.parseLong(game.getIgdbGameId());
            return Optional.ofNullable(igdbClient.fetchMultiplayerModes(List.of(igdbGameId)).get(igdbGameId));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }

    /**
     * The title search, qualified by the game's platforms when its local-multiplayer flag is still unknown: the same
     * title can exist on several platforms with different modes, so a match on the game's own platform is tried first.
     * A flagged game only needs its count, and the title alone is enough there.
     */
    private Optional<IgdbClient.MultiplayerMode> modeByTitle(Game game,
            Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle) {
        List<Long> platformIgdbIds = game.getLocalMultiplayer() == null ? igdbPlatformIds(game) : List.of();
        String key = platformIgdbIds.isEmpty() ? game.getTitleKey() : game.getTitleKey() + "|" + platformIgdbIds;
        if (platformIgdbIds.isEmpty()) {
            return igdbModeByTitle.computeIfAbsent(key, ignored -> igdbClient.resolveMultiplayerMode(game.getTitle()));
        }

        return igdbModeByTitle.computeIfAbsent(key,
                ignored -> igdbClient.resolveMultiplayerMode(game.getTitle(), platformIgdbIds));
    }

    private List<Long> igdbPlatformIds(Game game) {
        List<String> platforms = platformService.platformsOf(game.getId());
        if (platforms == null) {
            return List.of();
        }

        return platforms.stream()
                .map(PlatformCatalog::find)
                .flatMap(Optional::stream)
                .map(PlatformEntry::igdbPlatformId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * The last source, for the player count of a console game that is flagged local multiplayer and still has none
     * (spec 014, clarification 2026-11-01: a game whose flag is unknown is never asked, because the source's number
     * does not say whether it counts local or online players). One lookup per console the game lives on, cached for
     * the batch. A count is stored only when every console agrees; a conflict, a missing match or a count under two
     * leaves the game unknown with a reason. A paused source postpones the game without counting an attempt; an
     * unconfigured one changes nothing compared with before the source existed.
     */
    private Outcome fillFromTheGamesDb(Game game, Map<String, TheGamesDbClient.PlayerCountLookup> cache) {
        boolean eligible = !StringUtils.hasText(game.getSteamAppId())
                && Boolean.TRUE.equals(game.getLocalMultiplayer()) && game.getMaxLocalPlayers() == null;
        if (!eligible || !theGamesDbClient.isConfigured()) {
            return Outcome.UNRESOLVED;
        }
        List<String> consoles = platformService.platformsOf(game.getId());
        if (consoles.isEmpty()) {
            return Outcome.UNRESOLVED;
        }
        Set<Integer> counts = new LinkedHashSet<>();
        PlayerCountGap firstGap = null;
        boolean deferred = false;
        for (String console : consoles) {
            TheGamesDbClient.PlayerCountLookup lookup = cache.computeIfAbsent(game.getTitleKey() + "|" + console,
                    key -> theGamesDbClient.lookup(game.getTitle(), game.getTitleKey(), console));
            switch (lookup.outcome()) {
                case FOUND -> counts.add(lookup.maxLocalPlayers());
                case DEFERRED -> deferred = true;
                case GAP -> firstGap = firstGap == null ? lookup.gap() : firstGap;
            }
        }
        if (counts.size() > 1) {
            game.recordPlayerCountGap(PlayerCountGap.ENTRIES_DISAGREE);

            return Outcome.UNRESOLVED;
        }
        if (deferred) {
            return Outcome.DEFERRED;
        }
        if (counts.size() == 1) {
            game.applyPlayerCount(counts.iterator().next(), PlayerCountSource.THEGAMESDB);

            return Outcome.RESOLVED;
        }
        game.recordPlayerCountGap(firstGap);

        return Outcome.UNRESOLVED;
    }
}
