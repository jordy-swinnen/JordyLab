package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;

    public GamesPageResponse getGames(String search, String platform, int page, int size) {
        Page<Game> games = gameRepository.findVisibleGames(normalize(search), normalize(platform),
                PageRequest.of(page, size));

        return new GamesPageResponse(games.getContent().stream().map(this::toSummary).toList(),
                games.getNumber(), games.getSize(), games.getTotalElements(), games.getTotalPages());
    }

    public PlatformsResponse getPlatforms() {
        return new PlatformsResponse(gameRepository.findVisiblePlatforms());
    }

    public Optional<GameDetailResponse> getGameDetail(UUID id) {
        return gameRepository.findVisibleById(id).map(this::toDetail);
    }

    private GameSummaryResponse toSummary(Game game) {
        return new GameSummaryResponse(game.getId(), game.getTitle(), game.getPlatform(), game.getArtworkStatus(),
                externalArtworkUrl(game), localArtworkEndpoint(game));
    }

    private GameDetailResponse toDetail(Game game) {
        boolean enriched = game.getEnrichmentStatus() == EnrichmentStatus.ENRICHED;

        return new GameDetailResponse(game.getId(), game.getTitle(), game.getPlatform(),
                game.getSource().getSourceKey(), game.getArtworkStatus(), externalArtworkUrl(game),
                localArtworkEndpoint(game), game.getEnrichmentStatus(),
                enriched ? game.getGenre() : null,
                enriched ? game.getMaxLocalPlayers() : null,
                enriched ? game.getOnlineMultiplayer() : null,
                enriched ? game.getSinglePlayer() : null,
                enriched ? game.getDescription() : null,
                game.getFirstSeenAt());
    }

    private String externalArtworkUrl(Game game) {
        return game.getArtworkStatus() == ArtworkStatus.EXTERNAL_URL ? game.getArtworkRef() : null;
    }

    private String localArtworkEndpoint(Game game) {
        return game.getArtworkStatus() == ArtworkStatus.LOCAL_UPLOAD
                ? "/api/gamecatalog/games/" + game.getId() + "/artwork"
                : null;
    }

    private String normalize(String filter) {
        return StringUtils.hasText(filter) ? filter : null;
    }
}
