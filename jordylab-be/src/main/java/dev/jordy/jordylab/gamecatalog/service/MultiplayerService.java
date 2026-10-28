package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Derives structured local-multiplayer data from deterministic sources — Steam store categories
 * for Steam games, IGDB for ROMs and as a fallback when Steam has no category data. Never infers
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
    private final GameCatalogProperties properties;

    /** Derives multiplayer data for up to {@code maxGames} backlog games. Invoked inline — no scheduler. */
    public int derivePending(int maxGames) {
        int maxAttempts = properties.metadata().maxAttempts();
        List<Game> backlog = gameRepository.findMultiplayerBacklog(maxAttempts, PageRequest.of(0, maxGames));
        if (backlog.isEmpty()) {
            return 0;
        }
        log.info("Deriving multiplayer data for {} game(s)", backlog.size());
        Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle = new HashMap<>();
        int processed = 0;
        for (Game game : backlog) {
            boolean resolved;
            try {
                resolved = deriveOne(game, igdbModeByTitle, false);
            } catch (SteamRateLimitedException rateLimited) {
                log.warn("Steam store rate limit hit; pausing multiplayer batch after {} game(s)", processed);
                break;
            }
            if (!resolved) {
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
        if (!deriveOne(game, new HashMap<>(), true)) {
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
        if (!deriveOne(game, new HashMap<>(), false)) {
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
    private boolean deriveOne(Game game, Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle,
            boolean forceSteamCheck) {
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
            return true;
        }

        return fillFromIgdb(game, igdbModeByTitle);
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
        Optional<IgdbClient.MultiplayerMode> mode = igdbModeByTitle.computeIfAbsent(game.getTitle(),
                igdbClient::resolveMultiplayerMode);
        if (mode.isEmpty()) {
            return false;
        }
        game.applyDeterministicMultiplayerFlags(null, mode.get().onlineMultiplayer());
        game.applyFillOnlyMultiplayer(mode.get().localMultiplayer(), mode.get().splitScreen(),
                mode.get().maxLocalPlayers(), MultiplayerSource.IGDB);

        return !needsMultiplayerData(game);
    }
}
