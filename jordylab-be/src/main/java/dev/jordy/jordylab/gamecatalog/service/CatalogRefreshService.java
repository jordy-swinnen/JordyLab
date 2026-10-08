package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * The admin's per-game manual refreshes (facts, description, multiplayer). Bulk refreshes are {@code RefreshRunService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogRefreshService {

    private final GameRepository gameRepository;
    private final GameQueryService gameQueryService;
    private final EnrichmentService enrichmentService;
    private final SteamMetadataService steamMetadataService;
    private final MultiplayerService multiplayerService;

    public Optional<GameDetailResponse> refreshMetadata(UUID gameId, String userSubject) {
        Optional<Game> game = gameRepository.findVisibleById(gameId);
        if (game.isEmpty()) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(game.get().getSteamAppId())) {
            throw new MetadataNotSupportedException("deterministic metadata is only available for Steam games");
        }
        steamMetadataService.refresh(game.get());

        return gameQueryService.getGameDetail(gameId, userSubject);
    }

    public Optional<GameDetailResponse> refreshEnrichment(UUID gameId, String userSubject) {
        Optional<Game> game = gameRepository.findVisibleById(gameId);
        if (game.isEmpty()) {
            return Optional.empty();
        }
        enrichmentService.refresh(game.get());

        return gameQueryService.getGameDetail(gameId, userSubject);
    }

    public Optional<GameDetailResponse> refreshMultiplayer(UUID gameId, String userSubject) {
        Optional<Game> game = gameRepository.findVisibleById(gameId);
        if (game.isEmpty()) {
            return Optional.empty();
        }
        multiplayerService.refresh(game.get());

        return gameQueryService.getGameDetail(gameId, userSubject);
    }
}
