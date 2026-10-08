package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamMetadataService {

    private final GameRepository gameRepository;
    private final SteamAppDetailsClient steamAppDetailsClient;
    private final GameCatalogProperties properties;

    /**
     * Fetches deterministic metadata for up to {@code maxGames} PENDING Steam games, unpaced. No transaction spans the
     * store calls (spec 013 FR-017): each game is saved on its own once its facts are in.
     */
    public int fetchPending(int maxGames) {
        return fetchPending(maxGames, 0L);
    }

    /**
     * Fetches deterministic metadata for up to {@code maxGames} PENDING Steam games, pacing calls
     * {@code minIntervalMs} apart and pausing the batch on HTTP 429 without recording a failure.
     * Used by the library sync so a large first sync respects Steam's store rate limit.
     */
    public int fetchPending(int maxGames, long minIntervalMs) {
        List<Game> pending = gameRepository.findMetadataBacklog(MetadataStatus.PENDING, PageRequest.of(0, maxGames));
        if (pending.isEmpty()) {
            return 0;
        }
        log.info("Fetching deterministic metadata for {} Steam game(s)", pending.size());
        int processed = 0;
        for (Game game : pending) {
            if (processed > 0 && minIntervalMs > 0) {
                pause(minIntervalMs);
            }
            try {
                fetchOne(game);
            } catch (SteamRateLimitedException rateLimited) {
                log.warn("Steam store rate limit hit; pausing metadata batch after {} game(s)", processed);
                break;
            }
            processed++;
        }

        return processed;
    }

    /** Force re-fetches one Steam game's metadata, clearing its failure counter first. */
    public void refresh(Game game) {
        game.resetMetadataForRetry();
        fetchOne(game);
    }

    private void fetchOne(Game game) {
        apply(game, steamAppDetailsClient.fetch(game.getSteamAppId()));
        gameRepository.save(game);
    }

    /** Applies what Steam answered (or its silence) to a game: facts fill only what is still empty, a miss counts an attempt. */
    public void apply(Game game, Optional<SteamAppDetailsClient.SteamMetadata> metadata) {
        if (metadata.isEmpty()) {
            log.warn("Steam metadata fetch failed for '{}' (appid {})", game.getTitle(), game.getSteamAppId());
            game.recordMetadataFailure(properties.metadata().maxAttempts());

            return;
        }

        SteamAppDetailsClient.SteamMetadata facts = metadata.get();
        if (!facts.isGame()) {
            // A tool/runtime that slipped past the deny-list: keep it out of retry loops.
            log.info("Marking '{}' (appid {}) as non-game; no metadata applied", game.getTitle(), game.getSteamAppId());
            game.markMetadataFetched();

            return;
        }
        game.applyDeterministicMetadata(facts.genres(), facts.developer(), facts.publisher(), facts.releaseYear());
        SteamAppDetailsClient.MultiplayerFacts multiplayer = facts.multiplayer();
        if (multiplayer != null && multiplayer.categoriesPresent()) {
            game.applyDeterministicMultiplayerFlags(multiplayer.singlePlayer(), multiplayer.onlineMultiplayer());
            // Steam categories carry no player counts, so maxLocalPlayers stays untouched.
            game.applyDeterministicMultiplayer(multiplayer.localMultiplayer(), multiplayer.splitScreen(), null,
                    MultiplayerSource.STEAM);
        }
        game.applyDeterministicDescription(facts.shortDescription());
        game.markMetadataFetched();
    }

    private void pause(long minIntervalMs) {
        try {
            Thread.sleep(minIntervalMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing Steam metadata calls", interrupted);
        }
    }
}
