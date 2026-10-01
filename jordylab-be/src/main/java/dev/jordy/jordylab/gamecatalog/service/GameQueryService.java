package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.util.ArtworkUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private static final Set<String> VALID_INSTALL_STATUSES = Set.of("INSTALLED", "NOT_INSTALLED", "ALL");
    private static final Set<String> VALID_LIBRARY_SOURCES = Set.of("OWNED", "FAMILY", "LOCAL");

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final GameLibraryEntryRepository gameLibraryEntryRepository;

    public GamesPageResponse getGames(String search, String platform, String host, String installStatus,
            List<String> librarySources, Boolean localMultiplayer, int page, int size) {
        Page<Game> games = gameRepository.findVisibleGames(normalize(search), normalize(platform), normalize(host),
                normalizeInstallStatus(installStatus), normalizeLibrarySources(librarySources), localMultiplayer,
                PageRequest.of(page, size));
        Map<UUID, GameView> views = deriveViews(games.getContent());

        return new GamesPageResponse(games.getContent().stream()
                .map(game -> toSummary(game, views.get(game.getId()))).toList(),
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

    private Map<UUID, GameView> deriveViews(List<Game> games) {
        if (games.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = games.stream().map(Game::getId).toList();
        Set<UUID> installed = new java.util.HashSet<>();
        for (GameInstallation installation : gameInstallationRepository.findAllByGameIdIn(ids)) {
            if (installation.isInstalled() && installation.getSource().isEnabled()) {
                installed.add(installation.getGame().getId());
            }
        }
        Map<UUID, Set<LibrarySource>> sources = new HashMap<>();
        for (GameLibraryEntry entry : gameLibraryEntryRepository.findAllByGameIdIn(ids)) {
            if (entry.isActive()) {
                sources.computeIfAbsent(entry.getGame().getId(), key -> EnumSet.noneOf(LibrarySource.class))
                        .add(entry.getLibrarySource());
            }
        }
        Map<UUID, GameView> views = new HashMap<>();
        for (Game game : games) {
            Set<LibrarySource> gameSources = sources.getOrDefault(game.getId(), Set.of());
            views.put(game.getId(), new GameView(
                    installed.contains(game.getId()) ? InstallStatus.INSTALLED : InstallStatus.NOT_INSTALLED,
                    deriveLibrarySource(gameSources)));
        }

        return views;
    }

    private LibrarySource deriveLibrarySource(Set<LibrarySource> sources) {
        if (sources.contains(LibrarySource.OWNED)) {
            return LibrarySource.OWNED;
        }
        if (sources.contains(LibrarySource.FAMILY)) {
            return LibrarySource.FAMILY;
        }

        return LibrarySource.LOCAL;
    }

    private GameSummaryResponse toSummary(Game game, GameView view) {
        InstallStatus installStatus = view == null ? InstallStatus.NOT_INSTALLED : view.installStatus();
        LibrarySource librarySource = view == null ? LibrarySource.LOCAL : view.librarySource();

        return new GameSummaryResponse(game.getId(), game.getTitle(), game.getPlatform(), game.getCoverStatus(),
                ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game), installStatus, librarySource,
                game.getLocalMultiplayer());
    }

    private GameDetailResponse toDetail(Game game) {
        List<GameInstallation> installations = gameInstallationRepository.findAllByGameId(game.getId());
        List<GameLibraryEntry> libraryEntries = gameLibraryEntryRepository.findAllByGameId(game.getId());
        boolean enriched = game.getEnrichmentStatus() == EnrichmentStatus.ENRICHED;
        boolean metadataAvailable = game.getMetadataStatus() == MetadataStatus.OK;
        boolean installed = installations.stream()
                .anyMatch(installation -> installation.isInstalled() && installation.getSource().isEnabled());
        Set<LibrarySource> activeSources = EnumSet.noneOf(LibrarySource.class);
        List<String> familyOwners = new ArrayList<>();
        for (GameLibraryEntry entry : libraryEntries) {
            if (!entry.isActive()) {
                continue;
            }
            activeSources.add(entry.getLibrarySource());
            if (entry.getLibrarySource() == LibrarySource.FAMILY && StringUtils.hasText(entry.getFamilyOwnerNames())) {
                familyOwners.add(entry.getFamilyOwnerNames());
            }
        }
        List<HostRef> hosts = installations.stream()
                .filter(GameInstallation::isInstalled)
                .filter(installation -> installation.getSource().isEnabled())
                .map(installation -> new HostRef(installation.getSource().getHostname(),
                        installation.getSource().getSourceType()))
                .distinct()
                .toList();
        Map<String, InstallationFormat> hostFormats = installations.stream()
                .filter(GameInstallation::isManual)
                .filter(installation -> installation.getFormat() != null)
                .collect(Collectors.toMap(installation -> installation.getSource().getHostname(),
                        GameInstallation::getFormat, (first, second) -> first, LinkedHashMap::new));
        Instant firstSeenAt = installations.stream()
                .map(GameInstallation::getFirstSeenAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        boolean hasCatalogData = enriched || metadataAvailable;

        return new GameDetailResponse(game.getId(), game.getTitle(), game.getPlatform(), hosts, hostFormats,
                game.getCoverStatus(), ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game),
                game.getBannerStatus(), externalBannerUrl(game), null,
                game.getEnrichmentStatus(),
                enriched ? game.getGenre() : null,
                game.getGenres(), game.getDeveloper(), game.getPublisher(), game.getReleaseYear(),
                metadataSource(game),
                game.getMaxLocalPlayers(),
                hasCatalogData ? game.getOnlineMultiplayer() : null,
                hasCatalogData ? game.getSinglePlayer() : null,
                hasCatalogData ? game.getDescription() : null,
                firstSeenAt,
                installed ? InstallStatus.INSTALLED : InstallStatus.NOT_INSTALLED,
                deriveLibrarySource(activeSources),
                familyOwners,
                game.getLocalMultiplayer(),
                game.getSplitScreen(),
                deriveOnlineOnly(game),
                game.getMultiplayerSource());
    }

    /** Online-only = has online multiplayer but no local option; null while either fact is unknown. */
    private Boolean deriveOnlineOnly(Game game) {
        if (game.getOnlineMultiplayer() == null || game.getLocalMultiplayer() == null) {
            return null;
        }

        return game.getOnlineMultiplayer() && !game.getLocalMultiplayer();
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

    private String normalizeInstallStatus(String installStatus) {
        if (!StringUtils.hasText(installStatus)) {
            return "INSTALLED";
        }
        String normalized = installStatus.trim().toUpperCase();
        if (!VALID_INSTALL_STATUSES.contains(normalized)) {
            return "INSTALLED";
        }

        return normalized;
    }

    private List<String> normalizeLibrarySources(List<String> librarySources) {
        if (librarySources == null || librarySources.isEmpty()) {
            return null;
        }
        List<String> normalized = librarySources.stream()
                .filter(StringUtils::hasText)
                .map(source -> source.trim().toUpperCase())
                .filter(VALID_LIBRARY_SOURCES::contains)
                .distinct()
                .toList();

        return normalized.isEmpty() ? null : normalized;
    }

    private record GameView(InstallStatus installStatus, LibrarySource librarySource) {
    }
}
