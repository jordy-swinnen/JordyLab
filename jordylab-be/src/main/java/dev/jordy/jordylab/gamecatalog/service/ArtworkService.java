package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.ArtworkLookupClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtworkService {

    private static final int MAX_ARTWORK_FALLBACK_REQUESTS = 3;
    private static final int ARTWORK_REQUEST_RESPONSE_CAP = 200;

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final ArtworkLookupClient artworkLookupClient;
    private final GameCatalogProperties properties;

    public List<String> processArtworkAfterSync(ScanSource source, List<GamePayload> validEntries) {
        Map<String, Game> gamesByRef = gameInstallationRepository.findAllBySourceId(source.getId()).stream()
                .collect(Collectors.toMap(GameInstallation::getExternalRef, GameInstallation::getGame));

        List<String> requested = new ArrayList<>();
        for (GamePayload entry : validEntries) {
            Game game = gamesByRef.get(entry.externalRef());
            if (game == null) {
                continue;
            }
            resolveCover(source, game, entry, requested);
            resolveBanner(source, game);
        }

        return requested;
    }

    public Optional<ArtworkContent> loadVisibleArtwork(UUID gameId) {
        return gameRepository.findVisibleById(gameId)
                .filter(game -> game.getCoverStatus() == ArtworkStatus.LOCAL_UPLOAD)
                .filter(game -> game.getCoverRef() != null)
                .flatMap(game -> readArtworkFile(game.getCoverRef()));
    }

    private void resolveCover(ScanSource source, Game game, GamePayload entry, List<String> requested) {
        if (game.getCoverStatus() == ArtworkStatus.LOCAL_FALLBACK_REQUESTED) {
            transitionCoverToFallbackOrPlaceholder(game, entry, requested);

            return;
        }
        if (game.getCoverStatus() != ArtworkStatus.PENDING) {
            return;
        }
        if (properties.artwork().externalLookupEnabled()) {
            Optional<String> externalUrl = artworkLookupClient.findCoverArtworkUrl(source.getSourceType(),
                    game.getPlatform(), game.getSteamAppId(), game.getTitle());
            if (externalUrl.isPresent()) {
                game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, externalUrl.get());

                return;
            }
        }

        transitionCoverToFallbackOrPlaceholder(game, entry, requested);
    }

    private void resolveBanner(ScanSource source, Game game) {
        if (game.getBannerStatus() != ArtworkStatus.PENDING) {
            return;
        }
        if (properties.artwork().externalLookupEnabled()) {
            Optional<String> externalUrl = artworkLookupClient.findBannerArtworkUrl(source.getSourceType(),
                    game.getPlatform(), game.getSteamAppId(), game.getTitle());
            if (externalUrl.isPresent()) {
                game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, externalUrl.get());

                return;
            }
        }

        game.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);
    }

    private void transitionCoverToFallbackOrPlaceholder(Game game, GamePayload entry, List<String> requested) {
        if (!entry.artworkAvailable() || game.getArtworkFallbackRequests() >= MAX_ARTWORK_FALLBACK_REQUESTS) {
            game.applyCoverArtwork(ArtworkStatus.PLACEHOLDER, null);

            return;
        }
        if (requested.size() >= ARTWORK_REQUEST_RESPONSE_CAP) {
            return;
        }

        game.requestLocalCoverFallback();
        requested.add(game.getTitle());
    }

    private Optional<ArtworkContent> readArtworkFile(String relativeRef) {
        try {
            Path root = artworkRoot();
            Path target = root.resolve(relativeRef).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                return Optional.empty();
            }

            return Optional.of(new ArtworkContent(Files.readAllBytes(target), mediaTypeFor(target)));
        } catch (IOException exception) {
            log.warn("Could not read artwork file {}: {}", relativeRef, exception.getMessage());

            return Optional.empty();
        }
    }

    private String mediaTypeFor(Path target) {
        return target.getFileName().toString().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private Path artworkRoot() {
        return Path.of(properties.artwork().dir());
    }
}
