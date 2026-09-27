package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Fetches deterministic metadata for up to {@code maxGames} PENDING Steam games. Invoked inline
     * by the scan flow and by the manual bulk refresh — there is no scheduler.
     */
    @Transactional
    public int fetchPending(int maxGames) {
        List<Game> pending = gameRepository.findByMetadataStatusAndSteamAppIdIsNotNull(MetadataStatus.PENDING,
                PageRequest.of(0, maxGames));
        if (pending.isEmpty()) {
            return 0;
        }
        log.info("Fetching deterministic metadata for {} Steam game(s)", pending.size());
        pending.forEach(this::fetchOne);

        return pending.size();
    }

    /** Force re-fetches one Steam game's metadata, clearing its failure counter first. */
    @Transactional
    public void refresh(Game game) {
        game.resetMetadataForRetry();
        fetchOne(game);
    }

    private void fetchOne(Game game) {
        Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch(game.getSteamAppId());
        if (metadata.isEmpty()) {
            log.warn("Steam metadata fetch failed for '{}' (appid {})", game.getTitle(), game.getSteamAppId());
            game.recordMetadataFailure(properties.metadata().maxAttempts());

            return;
        }

        SteamAppDetailsClient.SteamMetadata facts = metadata.get();
        game.applyDeterministicMetadata(facts.genres(), facts.developer(), facts.publisher(), facts.releaseYear());
    }
}
