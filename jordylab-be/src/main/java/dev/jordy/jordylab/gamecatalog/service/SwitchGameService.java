package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient.SwitchGameDetails;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameUpdateRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Manual management of Nintendo Switch games. Switch titles are added by IGDB search or by
 * plain title; in both cases the resulting installation is marked {@code manual} and attached
 * to the well-known virtual Switch source so scans, syncs and purges never touch it.
 */
@Service
@RequiredArgsConstructor
public class SwitchGameService {

    private static final String SWITCH_PLATFORM = "Nintendo Switch";
    private static final String SWITCH_SOURCE_KEY = "Nintendo Switch";

    private final IgdbClient igdbClient;
    private final GameRepository gameRepository;
    private final GameInstallationRepository installationRepository;
    private final GameLibraryEntryRepository libraryEntryRepository;
    private final ScanSourceRepository scanSourceRepository;
    private final EnrichmentService enrichmentService;

    @Transactional(readOnly = true)
    public List<SwitchSearchResult> search(String query) {
        return igdbClient.searchSwitchGames(query).stream()
                .map(r -> new SwitchSearchResult(r.igdbGameId(), r.title(), r.releaseYear(), r.genres(),
                        r.developer(), r.coverUrl(), r.bannerUrl()))
                .toList();
    }

    /**
     * The catalog game already tracked on the Switch under this IGDB id or (case-insensitively) this title, if any.
     * Used by the bulk-add review to flag lines as already present (spec 009 US3).
     */
    @Transactional(readOnly = true)
    public Optional<UUID> findSwitchGameId(String title, Long igdbGameId) {
        Optional<Game> byIgdb = igdbGameId == null ? Optional.empty()
                : gameRepository.findByPlatformAndIgdbGameId(SWITCH_PLATFORM, String.valueOf(igdbGameId));
        Optional<Game> candidate = byIgdb.isPresent() ? byIgdb
                : gameRepository.findByPlatformAndLowercaseTitle(SWITCH_PLATFORM, title, PageRequest.of(0, 1)).stream()
                        .findFirst();

        return candidate.filter(game -> findSwitchInstallation(game).isPresent()).map(Game::getId);
    }

    @Transactional
    public SwitchGameResponse addFromIgdb(SwitchGameRequest request) {
        if (request.igdbGameId() == null) {
            throw new IllegalArgumentException("igdbGameId is required");
        }

        Instant now = Instant.now();
        SwitchGameDetails details = igdbClient.fetchSwitchGameDetails(request.igdbGameId())
                .orElseThrow(() -> new IllegalArgumentException("IGDB game not found"));

        String igdbGameId = String.valueOf(request.igdbGameId());
        Optional<Game> existing = gameRepository.findByPlatformAndIgdbGameId(SWITCH_PLATFORM, igdbGameId);
        Game game = existing.orElseGet(() -> createGameFromIgdb(details));

        ensureNoSwitchInstallation(game);
        createManualInstallation(game, request.format(), now);
        ensureOwnedLibraryEntry(game, now);

        if (existing.isEmpty()) {
            game.markMetadataFetched();
            applyIgdbDetails(game, details);
            enrichmentService.refresh(game);
        }

        return new SwitchGameResponse(game.getId(), game.getTitle(), game.getPlatform(), request.format().name());
    }

    @Transactional
    public SwitchGameResponse addManual(SwitchGameRequest request) {
        if (!StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("title is required");
        }

        Instant now = Instant.now();
        String title = request.title().trim();
        List<Game> matches = gameRepository.findByPlatformAndLowercaseTitle(SWITCH_PLATFORM, title,
                PageRequest.of(0, 1));
        Game game = matches.stream().findFirst().orElseGet(() -> createCustomGame(title, now));

        ensureNoSwitchInstallation(game);
        createManualInstallation(game, request.format(), now);
        ensureOwnedLibraryEntry(game, now);

        return new SwitchGameResponse(game.getId(), game.getTitle(), game.getPlatform(), request.format().name());
    }

    @Transactional
    public SwitchGameResponse update(UUID gameId, SwitchGameUpdateRequest request) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new SwitchGameNotFoundException("Game not found"));
        GameInstallation installation = findSwitchInstallation(game)
                .orElseThrow(() -> new SwitchGameNotFoundException("Switch installation not found"));

        if (request.format() != null) {
            installation.setFormat(request.format());
        }

        if (request.igdbGameId() != null) {
            String igdbGameId = String.valueOf(request.igdbGameId());
            if (!igdbGameId.equals(game.getIgdbGameId())) {
                SwitchGameDetails details = igdbClient.fetchSwitchGameDetails(request.igdbGameId())
                        .orElseThrow(() -> new IllegalArgumentException("IGDB game not found"));
                game.setIgdbGameId(igdbGameId);
                game.markMetadataFetched();
                applyIgdbDetails(game, details);
            }
        }

        return new SwitchGameResponse(game.getId(), game.getTitle(), game.getPlatform(), installation.getFormat().name());
    }

    @Transactional
    public void delete(UUID gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new SwitchGameNotFoundException("Game not found"));
        GameInstallation installation = findSwitchInstallation(game)
                .orElseThrow(() -> new SwitchGameNotFoundException("Switch installation not found"));

        installationRepository.delete(installation);

        long remainingInstallations = installationRepository.countByGameId(gameId);
        boolean heldInLibrary = libraryEntryRepository.existsByGameIdAndRemovedAtIsNull(gameId);
        if (remainingInstallations <= 0 && !heldInLibrary) {
            libraryEntryRepository.deleteAll(libraryEntryRepository.findAllByGameId(gameId));
            gameRepository.delete(game);
        }
    }

    private Optional<GameInstallation> findSwitchInstallation(Game game) {
        return installationRepository.findBySourceIdAndExternalRef(switchSource().getId(), game.getId().toString());
    }

    private Game createGameFromIgdb(SwitchGameDetails details) {
        Game game = Game.builder()
                .platform(SWITCH_PLATFORM)
                .igdbGameId(String.valueOf(details.igdbGameId()))
                .title(details.title())
                .titleSource(TitleSource.MANUAL)
                .enrichmentStatus(EnrichmentStatus.PENDING)
                .metadataStatus(MetadataStatus.OK)
                .build();

        return gameRepository.save(game);
    }

    private Game createCustomGame(String title, Instant now) {
        Game game = Game.builder()
                .platform(SWITCH_PLATFORM)
                .title(title)
                .titleSource(TitleSource.MANUAL)
                .enrichmentStatus(EnrichmentStatus.ENRICHED)
                .metadataStatus(MetadataStatus.PENDING)
                .build();
        game.applyCoverArtwork(ArtworkStatus.PLACEHOLDER, null);
        game.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);

        return gameRepository.save(game);
    }

    private void applyIgdbDetails(Game game, SwitchGameDetails details) {
        String genres = details.genres().isEmpty() ? null : String.join(", ", details.genres());
        game.applyDeterministicMetadata(genres, details.developer(), null, details.releaseYear());

        if (details.multiplayerMode() != null) {
            game.applyDeterministicMultiplayer(details.multiplayerMode().localMultiplayer(),
                    details.multiplayerMode().splitScreen(), details.multiplayerMode().maxLocalPlayers(),
                    MultiplayerSource.IGDB);
            if (details.multiplayerMode().onlineMultiplayer()) {
                game.applyDeterministicMultiplayerFlags(null, true);
            }
        }

        if (StringUtils.hasText(details.coverUrl())) {
            game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, details.coverUrl());
        } else {
            game.applyCoverArtwork(ArtworkStatus.PLACEHOLDER, null);
        }
        if (StringUtils.hasText(details.bannerUrl())) {
            game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, details.bannerUrl());
        } else {
            game.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);
        }
    }

    private void ensureNoSwitchInstallation(Game game) {
        ScanSource source = switchSource();
        Optional<GameInstallation> existing = installationRepository.findBySourceIdAndExternalRef(source.getId(),
                game.getId().toString());
        if (existing.isPresent()) {
            throw new IllegalStateException("Switch installation already exists for game " + game.getId());
        }
    }

    private void createManualInstallation(Game game, InstallationFormat format, Instant now) {
        GameInstallation installation = GameInstallation.createManual(game, switchSource(), game.getId().toString(),
                format, now);
        installationRepository.save(installation);
    }

    private void ensureOwnedLibraryEntry(Game game, Instant now) {
        Optional<GameLibraryEntry> existing = libraryEntryRepository.findByGameIdAndLibrarySource(game.getId(),
                LibrarySource.OWNED);
        if (existing.isPresent()) {
            return;
        }

        GameLibraryEntry entry = GameLibraryEntry.builder()
                .game(game)
                .librarySource(LibrarySource.OWNED)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
        libraryEntryRepository.save(entry);
    }

    private ScanSource switchSource() {
        return scanSourceRepository.findBySourceKey(SWITCH_SOURCE_KEY)
                .orElseThrow(() -> new IllegalStateException("Virtual Switch source is missing"));
    }
}
