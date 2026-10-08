package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamRateLimitedException;
import dev.jordy.jordylab.gamecatalog.service.SteamMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Step 1 of the auto-fill (spec 013 FR-019): the deterministic facts of a game. A Steam game asks the Steam store; every
 * other game asks IGDB, found by title on the game's platform. The lookup runs without a transaction and the result is
 * applied afterwards, fill-only, on a fresh copy of the game. A game IGDB has never heard of is marked as looked up, so it
 * is tried again only after the retry period and only while its facts are still incomplete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactsStep {

    private final SteamAppDetailsClient steamClient;
    private final SteamMetadataService steamMetadataService;
    private final IgdbClient igdbClient;
    private final GameRepository gameRepository;
    private final GameCatalogProperties properties;

    private long lastSteamCallNanos;

    /**
     * What the lookup found. {@code attempted} is false when nothing could be asked (IGDB not configured, Steam paused by
     * its rate limit), in which case nothing is recorded and the game stays eligible.
     */
    public record Fetched(Optional<SteamAppDetailsClient.SteamMetadata> steam, Optional<IgdbClient.IgdbFacts> igdb,
            boolean attempted, boolean steamRateLimited) {

        public static Fetched none() {
            return new Fetched(Optional.empty(), Optional.empty(), false, false);
        }
    }

    public boolean needed(Game game, Instant retryBefore) {
        if (StringUtils.hasText(game.getSteamAppId())) {
            return game.getMetadataStatus() == MetadataStatus.PENDING;
        }
        if (game.getFactsCheckedAt() == null) {
            return true;
        }

        return game.getFactsCheckedAt().isBefore(retryBefore)
                && (game.getGenres() == null || game.getDeveloper() == null || game.getReleaseYear() == null);
    }

    public Fetched fetch(Game game, List<String> platforms, boolean steamPaused) {
        if (StringUtils.hasText(game.getSteamAppId())) {
            if (steamPaused) {
                return Fetched.none();
            }
            try {
                paceSteamCalls();

                return new Fetched(steamClient.fetch(game.getSteamAppId()), Optional.empty(), true, false);
            } catch (SteamRateLimitedException rateLimited) {
                log.warn("Steam store rate limit hit while looking up '{}'", game.getTitle());

                return new Fetched(Optional.empty(), Optional.empty(), false, true);
            }
        }
        if (!igdbClient.isConfigured()) {
            return Fetched.none();
        }
        Long platformId = platforms.stream()
                .map(platform -> PlatformCatalog.entryFor(platform).igdbPlatformId())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        Optional<IgdbClient.IgdbFacts> facts = igdbClient.findGame(game.getTitle(), platformId)
                .flatMap(found -> igdbClient.fetchFacts(found.id()));

        return new Fetched(Optional.empty(), facts, true, false);
    }

    /** Keeps the Steam store calls apart by the configured interval, so a large library does not trip its rate limit. */
    private synchronized void paceSteamCalls() {
        long interval = properties.library().storeMinIntervalMs() * 1_000_000L;
        long wait = lastSteamCallNanos + interval - System.nanoTime();
        if (lastSteamCallNanos != 0 && wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastSteamCallNanos = System.nanoTime();
    }

    public void apply(Game game, Fetched fetched, Instant now) {
        if (!fetched.attempted()) {
            return;
        }
        if (StringUtils.hasText(game.getSteamAppId())) {
            steamMetadataService.apply(game, fetched.steam());

            return;
        }
        game.recordFactsChecked(now);
        fetched.igdb().ifPresent(facts -> applyIgdb(game, facts));
        game.markMetadataFetched();
    }

    private void applyIgdb(Game game, IgdbClient.IgdbFacts facts) {
        game.applyDeterministicMetadata(facts.genres().isEmpty() ? null : String.join(", ", facts.genres()),
                facts.developer(), facts.publisher(), facts.releaseYear());
        if (game.getIgdbGameId() == null && gameRepository.findByIgdbGameId(String.valueOf(facts.igdbGameId())).isEmpty()) {
            game.setIgdbGameId(String.valueOf(facts.igdbGameId()));
        }
        IgdbClient.MultiplayerMode mode = facts.multiplayerMode();
        if (mode != null && game.getMultiplayerSource() == MultiplayerSource.UNKNOWN) {
            game.applyDeterministicMultiplayerFlags(null, mode.onlineMultiplayer());
            game.applyDeterministicMultiplayer(mode.localMultiplayer(), mode.splitScreen(), mode.maxLocalPlayers(),
                    MultiplayerSource.IGDB);
        }
    }
}
