package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.ArtworkLookupClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtworkService {

    private static final int MAX_ARTWORK_FALLBACK_REQUESTS = 3;
    private static final int ARTWORK_REQUEST_RESPONSE_CAP = 200;

    private final GameRepository gameRepository;
    private final ArtworkLookupClient artworkLookupClient;
    private final GameCatalogProperties properties;

    public List<String> processArtworkAfterSync(ScanSource source, List<GamePayload> validEntries) {
        Map<String, Game> gamesByRef = gameRepository.findAllBySourceId(source.getId()).stream()
                .collect(Collectors.toMap(Game::getExternalRef, Function.identity()));

        List<String> requested = new ArrayList<>();
        for (GamePayload entry : validEntries) {
            Game game = gamesByRef.get(entry.externalRef());
            if (game == null) {
                continue;
            }
            if (game.getArtworkStatus() == ArtworkStatus.PENDING) {
                resolveDiscoveredGame(source, game, entry, requested);
            } else if (game.getArtworkStatus() == ArtworkStatus.LOCAL_FALLBACK_REQUESTED) {
                transitionToFallbackOrPlaceholder(game, entry, requested);
            }
        }

        return requested;
    }

    public Optional<ArtworkContent> loadVisibleArtwork(UUID gameId) {
        return gameRepository.findVisibleById(gameId)
                .filter(game -> game.getArtworkStatus() == ArtworkStatus.LOCAL_UPLOAD)
                .filter(game -> game.getArtworkRef() != null)
                .flatMap(game -> readArtworkFile(game.getArtworkRef()));
    }

    private void resolveDiscoveredGame(ScanSource source, Game game, GamePayload entry, List<String> requested) {
        if (properties.artwork().externalLookupEnabled()) {
            Optional<String> externalUrl = artworkLookupClient.findExternalArtworkUrl(source.getSourceType(),
                    game.getPlatform(), game.getExternalRef(), game.getTitle());
            if (externalUrl.isPresent()) {
                game.applyArtwork(ArtworkStatus.EXTERNAL_URL, externalUrl.get());

                return;
            }
        }

        transitionToFallbackOrPlaceholder(game, entry, requested);
    }

    private void transitionToFallbackOrPlaceholder(Game game, GamePayload entry, List<String> requested) {
        if (!entry.artworkAvailable() || game.getArtworkFallbackRequests() >= MAX_ARTWORK_FALLBACK_REQUESTS) {
            game.applyArtwork(ArtworkStatus.PLACEHOLDER, null);

            return;
        }
        if (requested.size() >= ARTWORK_REQUEST_RESPONSE_CAP) {
            return;
        }

        game.requestLocalArtworkFallback();
        requested.add(game.getExternalRef());
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
