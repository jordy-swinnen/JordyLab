package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;

    public GamesPageResponse getGames(String search, String platform, String host, int page, int size) {
        Page<Game> games = gameRepository.findVisibleGames(normalize(search), normalize(platform), normalize(host),
                PageRequest.of(page, size));

        return new GamesPageResponse(games.getContent().stream().map(this::toSummary).toList(),
                games.getNumber(), games.getSize(), games.getTotalElements(), games.getTotalPages());
    }

    public PlatformsResponse getPlatforms() {
        return new PlatformsResponse(gameRepository.findVisiblePlatforms());
    }

    public HostsResponse getHosts() {
        return new HostsResponse(gameRepository.findVisibleHosts());
    }

    public Optional<GameDetailResponse> getGameDetail(UUID id) {
        return gameRepository.findVisibleById(id).map(this::toDetail);
    }

    public List<Game> findVisibleByIds(List<UUID> ids) {
        return ids.stream()
                .map(gameRepository::findVisibleById)
                .flatMap(Optional::stream)
                .toList();
    }

    private GameSummaryResponse toSummary(Game game) {
        return new GameSummaryResponse(game.getId(), game.getTitle(), game.getPlatform(), game.getCoverStatus(),
                externalCoverUrl(game), localCoverEndpoint(game));
    }

    private GameDetailResponse toDetail(Game game) {
        List<GameInstallation> installations = gameInstallationRepository.findAllByGameId(game.getId());
        boolean enriched = game.getEnrichmentStatus() == EnrichmentStatus.ENRICHED;
        List<HostRef> hosts = installations.stream()
                .filter(GameInstallation::isInstalled)
                .filter(installation -> installation.getSource().isEnabled())
                .map(installation -> new HostRef(installation.getSource().getHostname(),
                        installation.getSource().getSourceType()))
                .distinct()
                .toList();
        Instant firstSeenAt = installations.stream()
                .map(GameInstallation::getFirstSeenAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new GameDetailResponse(game.getId(), game.getTitle(), game.getPlatform(), hosts,
                game.getCoverStatus(), externalCoverUrl(game), localCoverEndpoint(game),
                game.getBannerStatus(), externalBannerUrl(game), null,
                game.getEnrichmentStatus(),
                enriched ? game.getGenre() : null,
                game.getGenres(), game.getDeveloper(), game.getPublisher(), game.getReleaseYear(),
                metadataSource(game),
                enriched ? game.getMaxLocalPlayers() : null,
                enriched ? game.getOnlineMultiplayer() : null,
                enriched ? game.getSinglePlayer() : null,
                enriched ? game.getDescription() : null,
                firstSeenAt);
    }

    private String externalCoverUrl(Game game) {
        return game.getCoverStatus() == ArtworkStatus.EXTERNAL_URL ? game.getCoverRef() : null;
    }

    private String localCoverEndpoint(Game game) {
        return game.getCoverStatus() == ArtworkStatus.LOCAL_UPLOAD
                ? "/api/gamecatalog/games/" + game.getId() + "/artwork"
                : null;
    }

    private String externalBannerUrl(Game game) {
        return game.getBannerStatus() == ArtworkStatus.EXTERNAL_URL ? game.getBannerRef() : null;
    }

    private String metadataSource(Game game) {
        if (game.getMetadataStatus() != MetadataStatus.OK) {
            return null;
        }

        return game.getSteamAppId() != null ? "STEAM" : "AI";
    }

    private String normalize(String filter) {
        return StringUtils.hasText(filter) ? filter : null;
    }
}
