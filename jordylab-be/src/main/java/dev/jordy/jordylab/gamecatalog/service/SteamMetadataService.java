package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    @Transactional
    public void fetchPendingMetadata() {
        List<Game> pending = gameRepository.findByMetadataStatusAndSteamAppIdIsNotNull(MetadataStatus.PENDING,
                PageRequest.of(0, properties.metadata().batchSize()));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Fetching deterministic metadata for {} Steam game(s)", pending.size());
        pending.forEach(this::fetchOne);
    }

    @Scheduled(cron = "0 30 5 * * *")
    @Transactional
    public void resetFailedMetadata() {
        List<Game> failed = gameRepository.findByMetadataStatus(MetadataStatus.FAILED);
        if (failed.isEmpty()) {
            return;
        }
        failed.forEach(Game::resetMetadataForRetry);
        log.info("Reset {} FAILED metadata fetch(es) for retry", failed.size());
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
