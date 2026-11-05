package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetail;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSearchItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySyncStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkTotals;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceSummary;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatusResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySourceStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceSummary;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.LibraryStatusService;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunService;
import dev.jordy.jordylab.mcp.McpAdminIdentity;
import dev.jordy.jordylab.mcp.McpToolException;
import dev.jordy.jordylab.mcp.McpToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The game catalog tools of the JordyLab connector (spec 021, contracts/tools.md). Each one calls the same service the web
 * app uses, so the access rule and the filters are the same. Each returns a record of its own, so fields the screen
 * hides never reach the model (FR-015).
 */
@Component
@RequiredArgsConstructor
public class GameCatalogMcpTools {

    private static final int MAX_LOCAL_PLAYERS = 8;
    private static final String DEFAULT_INSTALL_STATUS = InstallStatus.INSTALLED.name();

    private final GameQueryService gameQueryService;
    private final ScanSourceService scanSourceService;
    private final LibraryStatusService libraryStatusService;
    private final RefreshRunService refreshRunService;

    @McpTool(name = "search_games",
            description = "Searches the game catalog with the same filters as the Game Catalog grid. Returns up to 50 games per page "
                    + "with the total match count. Local multiplayer is null when unconfirmed. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public McpToolPage<GameSearchItem> searchGames(
            @McpToolParam(description = "Words to find in the title", required = false) String search,
            @McpToolParam(description = "Platform names, for example PlayStation 2", required = false) List<String> platforms,
            @McpToolParam(description = "INSTALLED (default), NOT_INSTALLED or ALL", required = false) String installStatus,
            @McpToolParam(description = "Where the game lives: STEAM_OWNED, STEAM_FAMILY, EMULATED or CONSOLE", required = false)
            List<GameSource> sources,
            @McpToolParam(description = "Only games with at least this many confirmed local players, from 1 to 8", required = false)
            Integer minLocalPlayers,
            @McpToolParam(description = "ROM statuses to keep: UNKNOWN, VALIDATED or BROKEN", required = false)
            List<RomStatus> romStatus,
            @McpToolParam(description = "Marks to keep, from the admin's own marks: WANT_TO_PLAY, PLAYED_LIKED or PLAYED_DISLIKED",
                    required = false)
            List<MarkType> mark,
            @McpToolParam(description = "TITLE (default), MOST_WANTED or MOST_LIKED", required = false) GameFilter.Sort sort,
            @McpToolParam(description = "Zero-based page index", required = false) Integer page,
            @McpToolParam(description = "Items per page, from 1 to 50", required = false) Integer limit) {
        String normalizedInstallStatus = normalizeInstallStatus(installStatus);
        Integer localPlayers = normalizeMinLocalPlayers(minLocalPlayers);
        GameFilter filter = GameFilter.builder()
                .search(StringUtils.hasText(search) ? search.trim() : null)
                .platforms(platforms)
                .installStatus(normalizedInstallStatus)
                .sources(sources)
                .minLocalPlayers(localPlayers)
                .romStatuses(romStatus)
                .marks(mark)
                .markScope(GameFilter.MarkScope.MINE)
                .userSubject(McpAdminIdentity.currentSubject())
                .sort(sort)
                .build();
        int pageIndex = Math.max(0, page == null ? 0 : page);
        int pageSize = McpToolPage.clampLimit(limit == null ? McpToolPage.MAX_ITEMS_PER_CALL : limit);
        GamesPageResponse response = gameQueryService.getGames(filter, pageIndex, pageSize);
        List<GameSearchItem> items = response.content().stream().map(GameCatalogMcpTools::toSearchItem).toList();
        boolean hasMore = pageIndex + 1 < response.totalPages();
        return new McpToolPage<>(items, items.size(), response.totalElements(), hasMore);
    }

    @McpTool(name = "get_game",
            description = "Returns one game in full: its places, its platforms and sources, its metadata, the local multiplayer "
                    + "facts (null when unknown), its description with the source, and the mark totals. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public GameDetail getGame(
            @McpToolParam(description = "The game's identifier, from search_games", required = true) UUID gameId) {
        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(gameId, McpAdminIdentity.currentSubject());
        return detail.map(GameCatalogMcpTools::toGameDetail)
                .orElseThrow(() -> new McpToolException("No game has that identifier."));
    }

    @McpTool(name = "list_places",
            description = "Lists the hosts, consoles and platforms the catalog knows about. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public PlaceSummary listPlaces() {
        PlacesResponse places = gameQueryService.getPlaces();
        List<String> hosts = labelsOf(places, PlacesResponse.Kind.HOST);
        List<String> consoles = labelsOf(places, PlacesResponse.Kind.CONSOLE);
        List<String> platforms = gameQueryService.getPlatforms().platforms().stream().map(PlatformChip::name).toList();
        return new PlaceSummary(hosts, consoles, platforms);
    }

    @McpTool(name = "list_sources",
            description = "Lists the scan sources with their last outcome and how many games each one holds. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public McpToolPage<SourceSummary> listSources() {
        List<SourceSummary> sources = scanSourceService.listSources().sources().stream()
                .map(GameCatalogMcpTools::toSourceSummary)
                .toList();
        return McpToolPage.firstPage(sources);
    }

    @McpTool(name = "get_library_status",
            description = "Reports the Steam library syncs (the last run, its outcome, and whether it is stale) and whether a "
                    + "catalog refresh is running now. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public LibraryStatus getLibraryStatus() {
        LibraryStatusResponse status = libraryStatusService.status();
        boolean refreshRunning = refreshRunService.current(RefreshRunKind.DATA).isPresent()
                || refreshRunService.current(RefreshRunKind.AI).isPresent();
        return new LibraryStatus(toSyncStatus(status.owned()), toSyncStatus(status.family()),
                status.ownedConfigured(), refreshRunning);
    }

    private static String normalizeInstallStatus(String installStatus) {
        if (!StringUtils.hasText(installStatus)) {
            return DEFAULT_INSTALL_STATUS;
        }
        String normalized = installStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals(InstallStatus.INSTALLED.name()) || normalized.equals(InstallStatus.NOT_INSTALLED.name())
                || normalized.equals(InstallStatus.ALL.name())) {
            return normalized;
        }
        throw new McpToolException("installStatus must be INSTALLED, NOT_INSTALLED or ALL.");
    }

    private static Integer normalizeMinLocalPlayers(Integer minLocalPlayers) {
        if (minLocalPlayers == null) {
            return null;
        }
        if (minLocalPlayers < 1 || minLocalPlayers > MAX_LOCAL_PLAYERS) {
            throw new McpToolException("minLocalPlayers must be from 1 to " + MAX_LOCAL_PLAYERS + ".");
        }
        return minLocalPlayers;
    }

    private static List<String> labelsOf(PlacesResponse places, PlacesResponse.Kind kind) {
        List<String> labels = new ArrayList<>();
        for (PlacesResponse.PlaceOption place : places.places()) {
            if (place.kind() == kind) {
                labels.add(place.label());
            }
        }
        return labels;
    }

    private static GameSearchItem toSearchItem(GameSummaryResponse game) {
        return new GameSearchItem(
                game.id(),
                game.title(),
                platformNames(game.platforms()),
                sourceNames(game.sources()),
                InstallStatus.INSTALLED == game.installStatus(),
                game.localMultiplayer(),
                toMarkTotals(game.votes()),
                game.myMark());
    }

    private static GameDetail toGameDetail(GameDetailResponse game) {
        return new GameDetail(
                game.id(),
                game.title(),
                platformNames(game.platforms()),
                sourceNames(game.sources()),
                InstallStatus.INSTALLED == game.installStatus(),
                placeRefs(game.places()),
                game.genres(),
                game.developer(),
                game.publisher(),
                game.releaseYear(),
                game.description() == null ? null : game.description().text(),
                game.description() == null || game.description().source() == null ? null
                        : game.description().source().name(),
                game.maxLocalPlayers(),
                game.localMultiplayer(),
                game.splitScreen(),
                game.onlineOnly(),
                game.onlineMultiplayer(),
                game.singlePlayer(),
                toMarkTotals(game.votes()),
                game.myMark());
    }

    private static SourceSummary toSourceSummary(ScanSourceResponse source) {
        return new SourceSummary(
                source.id(),
                source.platform(),
                source.sourceType() == null ? null : source.sourceType().name(),
                source.enabled(),
                source.lastOutcome() == null ? null : source.lastOutcome().name(),
                source.lastSuccessAt(),
                source.installedGameCount());
    }

    private static LibrarySyncStatus toSyncStatus(LibrarySourceStatus status) {
        return new LibrarySyncStatus(
                status.lastSuccessAt(),
                status.lastOutcome() == null ? null : status.lastOutcome().name(),
                status.entriesActive(),
                status.stale());
    }

    private static List<PlaceRef> placeRefs(List<PlaceResponse> places) {
        return places.stream()
                .map(place -> new PlaceRef(
                        place.kind() == null ? null : place.kind().name(),
                        place.label(),
                        place.platform(),
                        place.installed(),
                        place.romStatus() == null ? null : place.romStatus().name()))
                .toList();
    }

    private static List<String> platformNames(List<PlatformChip> platforms) {
        return platforms.stream().map(PlatformChip::name).toList();
    }

    private static List<String> sourceNames(List<GameSource> sources) {
        return sources.stream().map(GameSource::name).toList();
    }

    private static MarkTotals toMarkTotals(VoteTotalsResponse votes) {
        return new MarkTotals(votes.wantToPlay(), votes.playedLiked(), votes.playedDisliked());
    }
}
