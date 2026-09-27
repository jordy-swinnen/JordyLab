package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshAllResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshCountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * On-demand catalog data population: the per-game manual refreshes and the bulk "refresh pending"
 * drain. The same underlying passes run inline on every applied scan; nothing here is scheduled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogRefreshService {

    private static final List<EnrichmentStatus> ENRICHMENT_RETRYABLE =
            List.of(EnrichmentStatus.PENDING, EnrichmentStatus.FAILED);
    private static final List<MetadataStatus> METADATA_RETRYABLE =
            List.of(MetadataStatus.PENDING, MetadataStatus.FAILED);

    private final GameRepository gameRepository;
    private final GameQueryService gameQueryService;
    private final EnrichmentService enrichmentService;
    private final SteamMetadataService steamMetadataService;
    private final GameCatalogProperties properties;

    @Transactional
    public Optional<GameDetailResponse> refreshMetadata(UUID gameId) {
        Optional<Game> game = gameRepository.findVisibleById(gameId);
        if (game.isEmpty()) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(game.get().getSteamAppId())) {
            throw new MetadataNotSupportedException("deterministic metadata is only available for Steam games");
        }
        steamMetadataService.refresh(game.get());

        return gameQueryService.getGameDetail(gameId);
    }

    @Transactional
    public Optional<GameDetailResponse> refreshEnrichment(UUID gameId) {
        Optional<Game> game = gameRepository.findVisibleById(gameId);
        if (game.isEmpty()) {
            return Optional.empty();
        }
        enrichmentService.refresh(game.get());

        return gameQueryService.getGameDetail(gameId);
    }

    @Transactional
    public RefreshAllResponse refreshPending() {
        int metadataProcessed = steamMetadataService.fetchPending(properties.metadata().batchSize());
        int enrichmentProcessed = enrichmentService.enrichPending(properties.enrichment().batchSize());

        return new RefreshAllResponse(
                new RefreshCountResponse(metadataProcessed,
                        gameRepository.countByMetadataStatusInAndSteamAppIdIsNotNull(METADATA_RETRYABLE)),
                new RefreshCountResponse(enrichmentProcessed,
                        gameRepository.countByEnrichmentStatusIn(ENRICHMENT_RETRYABLE)));
    }
}
