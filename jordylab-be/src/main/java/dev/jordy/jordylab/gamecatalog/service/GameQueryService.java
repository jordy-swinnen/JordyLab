package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.GameSources;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.DescriptionResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.FactSourcesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RomSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import dev.jordy.jordylab.gamecatalog.util.ArtworkUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final GameLibraryEntryRepository gameLibraryEntryRepository;
    private final ConsoleGameEntryRepository consoleGameEntryRepository;
    private final GamePlatformService gamePlatformService;
    private final GameMarkRepository gameMarkRepository;

    @Transactional(readOnly = true)
    public GamesPageResponse getGames(GameFilter filter, int page, int size) {
        Page<Game> games = gameRepository.findFiltered(filter, PageRequest.of(page, size));
        Map<UUID, GameView> views = deriveViews(games.getContent(), filter.userSubject());
        Long unknownPlayerCount = filter.minLocalPlayers() == null ? null
                : gameRepository.countWithUnknownPlayerCount(filter);

        return new GamesPageResponse(games.getContent().stream()
                .map(game -> toSummary(game, views.get(game.getId()))).toList(),
                games.getNumber(), games.getSize(), games.getTotalElements(), games.getTotalPages(),
                unknownPlayerCount);
    }

    /** Summaries of the visible games among {@code ids}, keyed by id (LibBot's reference chips). */
    @Transactional(readOnly = true)
    public Map<UUID, GameSummaryResponse> getSummaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Game> games = gameRepository.findAllById(ids).stream()
                .filter(game -> gameRepository.findVisibleById(game.getId()).isPresent()).toList();
        Map<UUID, GameView> views = deriveViews(games, null);
        Map<UUID, GameSummaryResponse> summaries = new HashMap<>();
        games.forEach(game -> summaries.put(game.getId(), toSummary(game, views.get(game.getId()))));

        return summaries;
    }

    @Transactional(readOnly = true)
    public PlatformsResponse getPlatforms() {
        return new PlatformsResponse(gameRepository.findVisiblePlatforms().stream().map(PlatformChip::of).toList());
    }

    /** Hosts holding an installed copy on an enabled source, and consoles holding games, by the name people see. */
    @Transactional(readOnly = true)
    public PlacesResponse getPlaces() {
        List<PlacesResponse.PlaceOption> places = new ArrayList<>();
        gameInstallationRepository.findInstalledHosts().forEach(host -> places.add(
                new PlacesResponse.PlaceOption(host.getId(), PlacesResponse.Kind.HOST, host.label())));
        consoleGameEntryRepository.findConsolesWithGames().forEach(console -> places.add(
                new PlacesResponse.PlaceOption(console.getId(), PlacesResponse.Kind.CONSOLE, console.label())));
        places.sort(Comparator.comparing(place -> place.label().toLowerCase(java.util.Locale.ROOT)));

        return new PlacesResponse(places);
    }

    @Transactional(readOnly = true)
    public Optional<GameDetailResponse> getGameDetail(UUID id, String userSubject) {
        return gameRepository.findVisibleById(id).map(game -> toDetail(game, userSubject));
    }

    private Map<UUID, GameView> deriveViews(List<Game> games, String userSubject) {
        if (games.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = games.stream().map(Game::getId).toList();
        Map<UUID, List<GameInstallation>> installations = gameInstallationRepository.findAllByGameIdIn(ids).stream()
                .collect(Collectors.groupingBy(installation -> installation.getGame().getId()));
        Map<UUID, List<GameLibraryEntry>> libraryEntries = gameLibraryEntryRepository.findAllByGameIdIn(ids).stream()
                .collect(Collectors.groupingBy(entry -> entry.getGame().getId()));
        Map<UUID, List<ConsoleGameEntry>> consoleEntries = consoleGameEntryRepository.findAllByGameIdIn(ids).stream()
                .collect(Collectors.groupingBy(entry -> entry.getGame().getId()));
        Map<UUID, List<String>> platforms = gamePlatformService.platformsOf(ids);
        Map<UUID, VoteTotalsResponse> votes = votesOf(ids);
        Map<UUID, MarkType> myMarks = myMarksOf(ids, userSubject);
        Map<UUID, GameView> views = new HashMap<>();
        for (Game game : games) {
            List<GameInstallation> gameInstallations = installations.getOrDefault(game.getId(), List.of());
            List<ConsoleGameEntry> gameConsoleEntries = consoleEntries.getOrDefault(game.getId(), List.of());
            Set<GameSource> sources = GameSources.of(gameInstallations,
                    libraryEntries.getOrDefault(game.getId(), List.of()), gameConsoleEntries);
            boolean availableNow = !gameConsoleEntries.isEmpty()
                    || gameInstallations.stream().anyMatch(GameSources::isEffective);
            views.put(game.getId(), new GameView(availableNow ? InstallStatus.INSTALLED : InstallStatus.NOT_INSTALLED,
                    sources, platforms.getOrDefault(game.getId(), List.of()),
                    votes.getOrDefault(game.getId(), VoteTotalsResponse.none()), myMarks.get(game.getId()),
                    romSummaryOf(gameInstallations)));
        }

        return views;
    }

    private GameSummaryResponse toSummary(Game game, GameView view) {
        return new GameSummaryResponse(game.getId(), game.getTitle(),
                view.platforms().stream().map(PlatformChip::of).toList(), List.copyOf(view.sources()),
                game.getCoverStatus(), ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game),
                view.installStatus(), game.getLocalMultiplayer(), view.votes(), view.myMark(), view.romSummary());
    }

    private GameDetailResponse toDetail(Game game, String userSubject) {
        List<GameInstallation> installations = gameInstallationRepository.findAllByGameId(game.getId());
        List<GameLibraryEntry> libraryEntries = gameLibraryEntryRepository.findAllByGameId(game.getId());
        List<ConsoleGameEntry> consoleEntries = consoleGameEntryRepository.findAllByGameId(game.getId());
        boolean enriched = game.getEnrichmentStatus() == EnrichmentStatus.ENRICHED;
        boolean metadataAvailable = game.getMetadataStatus() == MetadataStatus.OK;
        boolean availableNow = !consoleEntries.isEmpty() || installations.stream().anyMatch(GameSources::isEffective);
        Set<GameSource> sources = GameSources.of(installations, libraryEntries, consoleEntries);
        Instant firstSeenAt = installations.stream()
                .map(GameInstallation::getFirstSeenAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        boolean hasCatalogData = enriched || metadataAvailable;

        return new GameDetailResponse(game.getId(), game.getTitle(),
                gamePlatformService.platformsOf(game.getId()).stream().map(PlatformChip::of).toList(),
                List.copyOf(sources), places(installations, libraryEntries, consoleEntries),
                game.getCoverStatus(), ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game),
                game.getBannerStatus(), externalBannerUrl(game), null,
                game.getEnrichmentStatus(),
                enriched ? game.getGenre() : null,
                game.getGenres(), game.getDeveloper(), game.getPublisher(), game.getReleaseYear(),
                metadataSource(game),
                game.getMaxLocalPlayers(),
                hasCatalogData ? game.getOnlineMultiplayer() : null,
                hasCatalogData ? game.getSinglePlayer() : null,
                hasCatalogData ? descriptionOf(game) : null,
                firstSeenAt,
                availableNow ? InstallStatus.INSTALLED : InstallStatus.NOT_INSTALLED,
                game.getLocalMultiplayer(),
                game.getSplitScreen(),
                deriveOnlineOnly(game),
                game.getMultiplayerSource(),
                new FactSourcesResponse(metadataSource(game),
                        game.getMultiplayerSource() == MultiplayerSource.UNKNOWN ? null : game.getMultiplayerSource()),
                votesOf(List.of(game.getId())).getOrDefault(game.getId(), VoteTotalsResponse.none()),
                myMarksOf(List.of(game.getId()), userSubject).get(game.getId()));
    }

    private List<PlaceResponse> places(List<GameInstallation> installations, List<GameLibraryEntry> libraryEntries,
            List<ConsoleGameEntry> consoleEntries) {
        List<PlaceResponse> places = new ArrayList<>();
        installations.stream().filter(GameSources::isEffective)
                .forEach(installation -> places.add(PlaceResponse.hostCopy(installation)));
        libraryEntries.stream().filter(GameLibraryEntry::isActive).forEach(entry -> places.add(new PlaceResponse(
                PlaceResponse.PlaceKind.STEAM_LIBRARY, null, null, null, "Steam", "Steam", null, null,
                entry.getLibrarySource(), StringUtils.hasText(entry.getFamilyOwnerNames()) ? entry.getFamilyOwnerNames() : null)));
        consoleEntries.forEach(entry -> places.add(new PlaceResponse(PlaceResponse.PlaceKind.CONSOLE, null, null,
                entry.getConsole().getId(), entry.getConsole().label(), entry.getConsole().getPlatform(), null, null, null,
                null)));

        return places;
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

    private DescriptionResponse descriptionOf(Game game) {
        if (!StringUtils.hasText(game.getDescription())) {
            return null;
        }

        return new DescriptionResponse(game.getDescription(), game.getDescriptionSource(), game.getDescriptionModel(),
                game.getDescriptionRequestedModel(), game.getDescriptionWrittenAt());
    }

    private String metadataSource(Game game) {
        if (game.getMetadataStatus() != MetadataStatus.OK) {
            return null;
        }

        return game.getSteamAppId() != null ? "STEAM" : "AI";
    }

    private Map<UUID, VoteTotalsResponse> votesOf(Collection<UUID> ids) {
        Map<UUID, long[]> counts = new HashMap<>();
        for (GameMarkRepository.VoteTotal total : gameMarkRepository.countVotes(ids)) {
            long[] row = counts.computeIfAbsent(total.getGameId(), key -> new long[3]);
            row[total.getMark().ordinal()] = total.getTotal();
        }
        Map<UUID, VoteTotalsResponse> votes = new HashMap<>();
        counts.forEach((gameId, row) -> votes.put(gameId, new VoteTotalsResponse(row[MarkType.WANT_TO_PLAY.ordinal()],
                row[MarkType.PLAYED_LIKED.ordinal()], row[MarkType.PLAYED_DISLIKED.ordinal()])));

        return votes;
    }

    private Map<UUID, MarkType> myMarksOf(Collection<UUID> ids, String userSubject) {
        if (!StringUtils.hasText(userSubject)) {
            return Map.of();
        }
        Map<UUID, MarkType> marks = new HashMap<>();
        gameMarkRepository.findAllByGameIdInAndUserSubject(ids, userSubject)
                .forEach(mark -> marks.put(mark.getGame().getId(), mark.getMark()));

        return marks;
    }

    /** The state of a game's emulated copies on enabled sources; null when it has none. */
    private RomSummaryResponse romSummaryOf(List<GameInstallation> installations) {
        List<GameInstallation> emulated = installations.stream()
                .filter(installation -> GameSources.isEffective(installation) && installation.isEmulated()).toList();
        if (emulated.isEmpty()) {
            return null;
        }
        int validated = (int) emulated.stream().filter(copy -> copy.getRomStatus() == RomStatus.VALIDATED).count();
        int broken = (int) emulated.stream().filter(copy -> copy.getRomStatus() == RomStatus.BROKEN).count();
        int unknown = emulated.size() - validated - broken;
        RomSummaryResponse.State state;
        if (validated == emulated.size()) {
            state = RomSummaryResponse.State.VALIDATED;
        } else if (broken == emulated.size()) {
            state = RomSummaryResponse.State.BROKEN;
        } else if (unknown == emulated.size()) {
            state = RomSummaryResponse.State.UNKNOWN;
        } else {
            state = RomSummaryResponse.State.MIXED;
        }

        return new RomSummaryResponse(state, validated, broken, unknown, emulated.size());
    }

    private record GameView(InstallStatus installStatus, Set<GameSource> sources, List<String> platforms,
            VoteTotalsResponse votes, MarkType myMark, RomSummaryResponse romSummary) {
    }
}
