package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    @Transactional
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
                resolved = deriveOne(game, igdbModeByTitle);
            } catch (SteamRateLimitedException rateLimited) {
                log.warn("Steam store rate limit hit; pausing multiplayer batch after {} game(s)", processed);
                break;
            }
            if (!resolved) {
                game.recordMultiplayerFailure();
            }
            processed++;
        }

        return processed;
    }

    /** Force re-derives one game's multiplayer data, clearing its attempt counter first. */
    @Transactional
    public void refresh(Game game) {
        game.resetMultiplayerForRetry();
        if (!deriveOne(game, new HashMap<>())) {
            game.recordMultiplayerFailure();
        }
    }

    private boolean deriveOne(Game game, Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle) {
        if (StringUtils.hasText(game.getSteamAppId())) {
            Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch(game.getSteamAppId());
            if (metadata.isPresent()) {
                SteamAppDetailsClient.MultiplayerFacts facts = metadata.get().multiplayer();
                if (facts != null && facts.categoriesPresent()) {
                    game.applyDeterministicMultiplayerFlags(facts.singlePlayer(), facts.onlineMultiplayer());
                    // Steam categories carry no player counts, so maxLocalPlayers stays untouched.
                    game.applyDeterministicMultiplayer(facts.localMultiplayer(), facts.splitScreen(), null,
                            MultiplayerSource.STEAM);

                    return true;
                }
            }
            // No Steam category data (or the fetch failed) → fall back to IGDB by title.
        }

        return deriveFromIgdb(game, igdbModeByTitle);
    }

    private boolean deriveFromIgdb(Game game, Map<String, Optional<IgdbClient.MultiplayerMode>> igdbModeByTitle) {
        if (!igdbClient.isConfigured()) {
            return false;
        }
        Optional<IgdbClient.MultiplayerMode> mode = igdbModeByTitle.computeIfAbsent(game.getTitle(),
                igdbClient::resolveMultiplayerMode);
        if (mode.isEmpty()) {
            return false;
        }
        game.applyDeterministicMultiplayerFlags(null, mode.get().onlineMultiplayer());
        game.applyDeterministicMultiplayer(mode.get().localMultiplayer(), mode.get().splitScreen(),
                mode.get().maxLocalPlayers(), MultiplayerSource.IGDB);

        return true;
    }
}
