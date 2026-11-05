package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetail;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSearchItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceSummary;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceSummary;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.LibraryStatusService;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunService;
import dev.jordy.jordylab.mcp.McpToolException;
import dev.jordy.jordylab.mcp.McpToolPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.jordy.jordylab.gamecatalog.rest.controller.GameDetailResponseTestBuilder.aDefaultGameDetailResponse;
import static dev.jordy.jordylab.gamecatalog.rest.controller.GameSummaryResponseTestBuilder.aDefaultGameSummaryResponse;
import static dev.jordy.jordylab.gamecatalog.rest.controller.GameSummaryResponseTestBuilder.aGameSummaryResponse;
import static dev.jordy.jordylab.gamecatalog.rest.controller.LibraryStatusResponseTestBuilder.aDefaultLibraryStatusResponse;
import static dev.jordy.jordylab.gamecatalog.rest.controller.ScanSourceResponseTestBuilder.aDefaultScanSourceResponse;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameCatalogMcpToolsTest {

    private static final String SUBJECT = "00000000-0000-0000-0000-00000000000a";
    private static final UUID GAME_ID = GameSummaryResponseTestBuilder.DEFAULT_ID;

    private final GameQueryService gameQueryService = mock(GameQueryService.class);
    private final ScanSourceService scanSourceService = mock(ScanSourceService.class);
    private final LibraryStatusService libraryStatusService = mock(LibraryStatusService.class);
    private final RefreshRunService refreshRunService = mock(RefreshRunService.class);
    private GameCatalogMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new GameCatalogMcpTools(gameQueryService, scanSourceService, libraryStatusService, refreshRunService);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchGamesMapsTheFieldsTheConnectorShowsAndReportsTheTotals() {
        ArgumentCaptor<GameFilter> filterCaptor = ArgumentCaptor.forClass(GameFilter.class);
        GamesPageResponse page = pageOf(List.of(aDefaultGameSummaryResponse()), 120, 3);
        doReturn(page).when(gameQueryService).getGames(filterCaptor.capture(), eq(0), eq(50));

        McpToolPage<GameSearchItem> result = tools.searchGames(null, null, null, null, null, null, null, null, null, null);

        GameSearchItem item = result.items().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(item.title()).isEqualTo("Mario Party");
            softly.assertThat(item.platforms()).containsExactly("Nintendo 64");
            softly.assertThat(item.sources()).containsExactly("EMULATED");
            softly.assertThat(item.installed()).isTrue();
            softly.assertThat(item.localMultiplayer()).isTrue();
            softly.assertThat(item.markTotals().playedLiked()).isEqualTo(2L);
            softly.assertThat(item.myMark()).isEqualTo(MarkType.PLAYED_LIKED);
            softly.assertThat(result.returned()).isEqualTo(1);
            softly.assertThat(result.totalMatching()).isEqualTo(120L);
            softly.assertThat(result.hasMore()).isTrue();
        });
        GameFilter filter = filterCaptor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(filter.installStatus()).isEqualTo(InstallStatus.INSTALLED.name());
            softly.assertThat(filter.markScope()).isEqualTo(GameFilter.MarkScope.MINE);
            softly.assertThat(filter.userSubject()).isEqualTo(SUBJECT);
            softly.assertThat(filter.minLocalPlayers()).isNull();
        });
    }

    @Test
    void searchGamesClampsTheLimitToFiftyItems() {
        ArgumentCaptor<GameFilter> filterCaptor = ArgumentCaptor.forClass(GameFilter.class);
        GamesPageResponse page = pageOf(List.of(aDefaultGameSummaryResponse()), 1, 1);
        doReturn(page).when(gameQueryService).getGames(filterCaptor.capture(), eq(0), eq(50));

        McpToolPage<GameSearchItem> result = tools.searchGames(null, null, null, null, null, null, null, null, 0, 500);

        GameFilter filter = filterCaptor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(result.returned()).isEqualTo(1);
            softly.assertThat(filter.installStatus()).isEqualTo(InstallStatus.INSTALLED.name());
            softly.assertThat(filter.markScope()).isEqualTo(GameFilter.MarkScope.MINE);
        });
    }

    @Test
    void searchGamesKeepsAnUnconfirmedLocalMultiplayerFactAsNull() {
        GameSummaryResponse unknown = aGameSummaryResponse(GAME_ID, "Untested game", null);
        ArgumentCaptor<GameFilter> filterCaptor = ArgumentCaptor.forClass(GameFilter.class);
        doReturn(pageOf(List.of(unknown), 1, 1))
                .when(gameQueryService).getGames(filterCaptor.capture(), eq(0), eq(50));

        McpToolPage<GameSearchItem> result = tools.searchGames(null, null, null, null, null, null, null, null, null, null);

        assertSoftly(softly -> {
            softly.assertThat(result.items().getFirst().localMultiplayer()).isNull();
            softly.assertThat(filterCaptor.getValue().minLocalPlayers()).isNull();
        });
    }

    @Test
    void searchGamesRefusesAnUnknownInstallStatus() {
        assertThatThrownBy(() -> tools.searchGames(null, null, "SOMETIMES", null, null, null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessage("installStatus must be INSTALLED, NOT_INSTALLED or ALL.");
    }

    @Test
    void searchGamesRefusesAMinimumOutsideOneToEight() {
        assertThatThrownBy(() -> tools.searchGames(null, null, null, null, 9, null, null, null, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessage("minLocalPlayers must be from 1 to 8.");
    }

    @Test
    void getGameReturnsTheDetailWithItsPlacesAndMarkTotals() {
        when(gameQueryService.getGameDetail(GAME_ID, SUBJECT)).thenReturn(Optional.of(aDefaultGameDetailResponse()));

        GameDetail detail = tools.getGame(GAME_ID);

        assertSoftly(softly -> {
            softly.assertThat(detail.title()).isEqualTo("Mario Party");
            softly.assertThat(detail.places().getFirst().kind()).isEqualTo("HOST_COPY");
            softly.assertThat(detail.places().getFirst().romStatus()).isEqualTo("VALIDATED");
            softly.assertThat(detail.description()).contains("four players");
            softly.assertThat(detail.descriptionSource()).isEqualTo("AI");
            softly.assertThat(detail.splitScreen()).isTrue();
            softly.assertThat(detail.markTotals().wantToPlay()).isEqualTo(3L);
        });
    }

    @Test
    void getGameRefusesAnUnknownIdentifierWithASafeMessage() {
        when(gameQueryService.getGameDetail(GAME_ID, SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.getGame(GAME_ID))
                .isInstanceOf(McpToolException.class)
                .hasMessage("No game has that identifier.");
    }

    @Test
    void listPlacesSeparatesHostsConsolesAndPlatforms() {
        when(gameQueryService.getPlaces()).thenReturn(new PlacesResponse(List.of(
                new PlacesResponse.PlaceOption(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
                        PlacesResponse.Kind.HOST, "Living room PC"),
                new PlacesResponse.PlaceOption(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"),
                        PlacesResponse.Kind.CONSOLE, "Switch"))));
        when(gameQueryService.getPlatforms()).thenReturn(
                new PlatformsResponse(List.of(new PlatformChip("Nintendo 64", null, null, null, null))));

        PlaceSummary places = tools.listPlaces();

        assertSoftly(softly -> {
            softly.assertThat(places.hosts()).containsExactly("Living room PC");
            softly.assertThat(places.consoles()).containsExactly("Switch");
            softly.assertThat(places.platforms()).containsExactly("Nintendo 64");
        });
    }

    @Test
    void listSourcesNamesEachSourceByItsPlatformAndLeavesOutTheMachineFields() {
        when(scanSourceService.listSources()).thenReturn(new SourcesResponse(List.of(aDefaultScanSourceResponse()), null));

        McpToolPage<SourceSummary> result = tools.listSources();

        SourceSummary source = result.items().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(source.name()).isEqualTo("Steam");
            softly.assertThat(source.sourceType()).isEqualTo("STEAM");
            softly.assertThat(source.lastOutcome()).isEqualTo("APPLIED");
            softly.assertThat(source.installedGames()).isEqualTo(42L);
            softly.assertThat(result.totalMatching()).isEqualTo(1L);
        });
    }

    @Test
    void getLibraryStatusReportsTheSyncsAndNoRunningRefreshWhenNoneIsRunning() {
        when(libraryStatusService.status()).thenReturn(aDefaultLibraryStatusResponse());

        LibraryStatus status = tools.getLibraryStatus();

        assertSoftly(softly -> {
            softly.assertThat(status.steamOwned().entriesActive()).isEqualTo(42L);
            softly.assertThat(status.steamOwned().lastOutcome()).isEqualTo("APPLIED");
            softly.assertThat(status.steamFamily().stale()).isTrue();
            softly.assertThat(status.ownedConfigured()).isTrue();
            softly.assertThat(status.refreshRunning()).isFalse();
        });
    }

    private static GamesPageResponse pageOf(List<GameSummaryResponse> content, long totalElements, int totalPages) {
        return new GamesPageResponse(content, 0, 50, totalElements, totalPages, null);
    }
}
