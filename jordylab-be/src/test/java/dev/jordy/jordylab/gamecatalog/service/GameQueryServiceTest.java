package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.DescriptionResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RomSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameQueryServiceTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 60);
    private static final GameFilter DEFAULT_FILTER = GameFilter.builder().build();

    private static final Instant FIRST_SEEN = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant LAST_SEEN = Instant.parse("2026-08-02T10:15:00Z");
    private static final String PLATFORM = "SNES";
    private static final dev.jordy.jordylab.gamecatalog.domain.AiAuthorship AUTHORSHIP =
            dev.jordy.jordylab.gamecatalog.domain.AiAuthorship.of("model", "model", FIRST_SEEN);

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private GameLibraryEntryRepository gameLibraryEntryRepository;

    @Mock
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    @Mock
    private GamePlatformService gamePlatformService;

    @Mock
    private GameMarkRepository gameMarkRepository;

    private GameQueryService gameQueryService;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, gameInstallationRepository,
                gameLibraryEntryRepository, consoleGameEntryRepository, gamePlatformService, gameMarkRepository);
    }

    @Test
    void mapsVisibleGamesToSummariesWithExternalCoverUrl() {
        Game game = aGame("Super Mario World");
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png");
        when(gameRepository.findFiltered(eq(DEFAULT_FILTER), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(game), 1));
        when(gamePlatformService.platformsOf(List.of(game.getId()))).thenReturn(Map.of(game.getId(), List.of(PLATFORM)));

        GamesPageResponse response = gameQueryService.getGames(DEFAULT_FILTER, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content()).hasSize(1);
            GameSummaryResponse summary = response.content().getFirst();
            softly.assertThat(summary.id()).isEqualTo(game.getId());
            softly.assertThat(summary.title()).isEqualTo("Super Mario World");
            softly.assertThat(summary.platforms()).extracting(PlatformChip::name).containsExactly(PLATFORM);
            softly.assertThat(summary.platforms().getFirst().background()).isEqualTo("#E60012");
            softly.assertThat(summary.coverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(summary.coverUrl()).isEqualTo("https://example.com/smw.png");
            softly.assertThat(summary.coverEndpoint()).isNull();
            softly.assertThat(response.page()).isZero();
            softly.assertThat(response.size()).isEqualTo(60);
            softly.assertThat(response.totalElements()).isEqualTo(1);
            softly.assertThat(response.totalPages()).isEqualTo(1);
        });
    }

    @Test
    void mapsLocalUploadToCoverEndpointInsteadOfUrl() {
        Game game = aGame("Chrono Trigger");
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/abc123.png");
        when(gameRepository.findFiltered(eq(DEFAULT_FILTER), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(game), 1));

        GamesPageResponse response = gameQueryService.getGames(DEFAULT_FILTER, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content().getFirst().coverUrl()).isNull();
            softly.assertThat(response.content().getFirst().coverEndpoint())
                    .isEqualTo("/api/gamecatalog/games/" + game.getId() + "/artwork");
        });
    }

    @Test
    void mapsPendingAndPlaceholderCoverToNullFields() {
        Game pending = aGame("Pending Game");
        Game placeholder = aGame("Placeholder Game");
        placeholder.applyCoverArtwork(ArtworkStatus.PLACEHOLDER, null);
        when(gameRepository.findFiltered(eq(DEFAULT_FILTER), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(pending, placeholder), 2));

        GamesPageResponse response = gameQueryService.getGames(DEFAULT_FILTER, 0, 60);

        assertThat(response.content()).allSatisfy(summary -> assertSoftly(softly -> {
            softly.assertThat(summary.coverUrl()).isNull();
            softly.assertThat(summary.coverEndpoint()).isNull();
        }));
    }

    @Test
    void passesTheFilterAndPaginationToRepository() {
        GameFilter filter = GameFilter.builder().search("mario").platforms(List.of(PLATFORM)).minLocalPlayers(4).build();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(gameRepository.findFiltered(eq(filter), pageableCaptor.capture())).thenReturn(pageOf(List.of(), 0));
        when(gameRepository.countWithUnknownPlayerCount(filter)).thenReturn(31L);

        GamesPageResponse response = gameQueryService.getGames(filter, 2, 60);

        assertSoftly(softly -> {
            softly.assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(2, 60));
            softly.assertThat(response.unknownPlayerCount()).isEqualTo(31L);
        });
    }

    @Test
    void reportsNoUnknownPlayerCountWhenNoPlayerCountWasAskedFor() {
        when(gameRepository.findFiltered(eq(DEFAULT_FILTER), eq(FIRST_PAGE))).thenReturn(pageOf(List.of(), 0));

        GamesPageResponse response = gameQueryService.getGames(DEFAULT_FILTER, 0, 60);

        assertThat(response.unknownPlayerCount()).isNull();
    }

    @Test
    void summariesCarryVotesMyMarkAndRomSummary() {
        Game game = aGame("Super Mario World");
        GameInstallation validated = anInstallation(game, aSource("jordybox", SourceType.EMUDECK));
        validated.changeRomStatus(RomStatus.VALIDATED);
        GameFilter filter = DEFAULT_FILTER.toBuilder().userSubject("user-1").build();
        when(gameRepository.findFiltered(eq(filter), eq(FIRST_PAGE))).thenReturn(pageOf(List.of(game), 1));
        when(gameInstallationRepository.findAllByGameIdIn(List.of(game.getId()))).thenReturn(List.of(validated));
        when(gameMarkRepository.countVotes(List.of(game.getId()))).thenReturn(List.of(
                voteTotal(game.getId(), MarkType.WANT_TO_PLAY, 3), voteTotal(game.getId(), MarkType.PLAYED_LIKED, 1)));
        when(gameMarkRepository.findAllByGameIdInAndUserSubject(List.of(game.getId()), "user-1"))
                .thenReturn(List.of(aMark(game, "user-1", MarkType.WANT_TO_PLAY)));

        GameSummaryResponse summary = gameQueryService.getGames(filter, 0, 60).content().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(summary.votes()).isEqualTo(new VoteTotalsResponse(3, 1, 0));
            softly.assertThat(summary.myMark()).isEqualTo(MarkType.WANT_TO_PLAY);
            softly.assertThat(summary.romSummary().state()).isEqualTo(RomSummaryResponse.State.VALIDATED);
            softly.assertThat(summary.romSummary().total()).isEqualTo(1);
        });
    }

    @Test
    void returnsVisiblePlatforms() {
        when(gameRepository.findVisiblePlatforms()).thenReturn(List.of("SNES", "Steam"));

        PlatformsResponse response = gameQueryService.getPlatforms();

        assertSoftly(softly -> {
            softly.assertThat(response.platforms()).extracting(PlatformChip::name).containsExactly("SNES", "Steam");
            softly.assertThat(response.platforms().get(1).background()).isEqualTo("#1B2838");
            softly.assertThat(response.platforms().get(1).foreground()).isEqualTo("#66C0F4");
        });
    }

    @Test
    void placesListHostsAndConsolesByTheNameTheAdminChose() {
        Host plain = aHost("macbook");
        Host named = aHost("ryzen");
        named.rename("Living room PC");
        Console console = aConsole("Nintendo Switch");
        when(gameInstallationRepository.findInstalledHosts()).thenReturn(List.of(plain, named));
        when(consoleGameEntryRepository.findConsolesWithGames()).thenReturn(List.of(console));

        PlacesResponse response = gameQueryService.getPlaces();

        assertThat(response.places()).extracting(PlacesResponse.PlaceOption::label)
                .containsExactly("Living room PC", "macbook", "Nintendo Switch");
    }

    @Test
    void detailExposesEnrichmentFieldsForEnrichedGame() {
        Game game = aGame("Super Mario World");
        game.applyEnrichment("Platformer", false, true, "A classic.", AUTHORSHIP);
        GameInstallation installation = anInstallation(game, aSource("jordybox", SourceType.EMUDECK));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(installation));

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId(), null);

        assertSoftly(softly -> {
            softly.assertThat(detail).isPresent();
            softly.assertThat(detail.get().genre()).isEqualTo("Platformer");            softly.assertThat(detail.get().description().text()).isEqualTo("A classic.");
            softly.assertThat(detail.get().places()).hasSize(1);
            softly.assertThat(detail.get().places().getFirst().kind()).isEqualTo(PlaceResponse.PlaceKind.HOST_COPY);
            softly.assertThat(detail.get().places().getFirst().label()).isEqualTo("jordybox");
            softly.assertThat(detail.get().places().getFirst().platform()).isEqualTo(PLATFORM);
            softly.assertThat(detail.get().sources()).containsExactly(GameSource.EMULATED);
            softly.assertThat(detail.get().firstSeenAt()).isEqualTo(FIRST_SEEN);
        });
    }

    @Test
    void detailCarriesThePublicVoteTotalsAndTheCallersOwnMark() {
        Game game = aGame("Super Mario World");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameMarkRepository.countVotes(List.of(game.getId()))).thenReturn(List.of(
                voteTotal(game.getId(), MarkType.WANT_TO_PLAY, 4), voteTotal(game.getId(), MarkType.PLAYED_DISLIKED, 1)));
        when(gameMarkRepository.findAllByGameIdInAndUserSubject(List.of(game.getId()), "user-1"))
                .thenReturn(List.of(aMark(game, "user-1", MarkType.PLAYED_DISLIKED)));

        GameDetailResponse mine = gameQueryService.getGameDetail(game.getId(), "user-1").orElseThrow();
        GameDetailResponse anonymous = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(mine.votes()).isEqualTo(new VoteTotalsResponse(4, 0, 1));
            softly.assertThat(mine.myMark()).isEqualTo(MarkType.PLAYED_DISLIKED);
            softly.assertThat(anonymous.votes()).isEqualTo(new VoteTotalsResponse(4, 0, 1));
            softly.assertThat(anonymous.myMark()).isNull();
        });
    }

    @Test
    void anAiDescriptionCarriesTheModelThatAnsweredAndTheRouterThatWasRequested() {
        Game game = aGame("Aseprite");
        game.applyEnrichment("Tool", false, true, "A pixel art editor.",
                dev.jordy.jordylab.gamecatalog.domain.AiAuthorship.of("claude-haiku-4.5", "jev-router",
                        Instant.parse("2026-10-07T10:12:00Z")));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        DescriptionResponse description = gameQueryService.getGameDetail(game.getId(), null).orElseThrow().description();

        assertSoftly(softly -> {
            softly.assertThat(description.text()).isEqualTo("A pixel art editor.");
            softly.assertThat(description.source()).isEqualTo(DescriptionSource.AI);
            softly.assertThat(description.model()).isEqualTo("claude-haiku-4.5");
            softly.assertThat(description.requestedModel()).isEqualTo("jev-router");
            softly.assertThat(description.writtenAt()).isEqualTo(Instant.parse("2026-10-07T10:12:00Z"));
        });
    }

    @Test
    void aRouterThatReportedNoModelLeavesOnlyTheRequestedId() {
        Game game = aGame("Aseprite");
        game.applyEnrichment("Tool", false, true, "A pixel art editor.",
                dev.jordy.jordylab.gamecatalog.domain.AiAuthorship.of(null, "jev-router", Instant.parse("2026-10-07T10:12:00Z")));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        DescriptionResponse description = gameQueryService.getGameDetail(game.getId(), null).orElseThrow().description();

        assertSoftly(softly -> {
            softly.assertThat(description.model()).isNull();
            softly.assertThat(description.requestedModel()).isEqualTo("jev-router");
        });
    }

    @Test
    void textWrittenBeforeTheModelWasRecordedHasNoModelAtAll() {
        Game game = aGame("Old text");
        game.applyEnrichment("Tool", false, true, "Old words.",
                dev.jordy.jordylab.gamecatalog.domain.AiAuthorship.of(null, null, null));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        DescriptionResponse description = gameQueryService.getGameDetail(game.getId(), null).orElseThrow().description();

        assertSoftly(softly -> {
            softly.assertThat(description.source()).isEqualTo(DescriptionSource.AI);
            softly.assertThat(description.model()).isNull();
            softly.assertThat(description.requestedModel()).isNull();
            softly.assertThat(description.writtenAt()).isNull();
        });
    }

    @Test
    void aSteamDescriptionIsMarkedAsSteamsOwnWithNoModel() {
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();
        game.applyDeterministicDescription("Portal 2 is a puzzle game.");
        game.applyDeterministicMetadata("Puzzle", "Valve", "Valve", 2011);
        game.markMetadataFetched();
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.description().source()).isEqualTo(DescriptionSource.STEAM);
            softly.assertThat(detail.description().model()).isNull();
            softly.assertThat(detail.factSources().facts()).isEqualTo("STEAM");
            softly.assertThat(detail.factSources().multiplayer()).isNull();
        });
    }

    @Test
    void detailListsOnlyInstalledEnabledHosts() {
        Game game = aGame("Super Mario World");
        GameInstallation enabled = anInstallation(game, aSource("jordybox", SourceType.EMUDECK));
        GameInstallation disabled = anInstallation(game, aSource("disabled-host", SourceType.STEAM, false));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(enabled, disabled));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertThat(detail.places()).hasSize(1);
        assertThat(detail.places().getFirst().label()).isEqualTo("jordybox");
    }

    @Test
    void detailShowsTheHostDisplayNameAndNeverTheRawHostname() {
        Game game = aGame("Super Mario World");
        Host host = Host.builder().hostname("cachyos-htpc").displayName("Living room PC").build();
        GameInstallation installation = anInstallation(game,
                ScanSource.builder().host(host).sourceType(SourceType.EMUDECK).enabled(true).build());
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(installation));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.places().getFirst().label()).isEqualTo("Living room PC");
            softly.assertThat(detail.places().getFirst().hostId()).isEqualTo(host.getId());
            softly.assertThat(detail.places().toString()).doesNotContain("cachyos-htpc");
        });
    }

    @Test
    void detailListsConsolePlacesWithTheConsoleNameAndPlatform() {
        Game game = aGame("Mario Kart 8 Deluxe");
        Console console = Console.builder().platform("Nintendo Switch").name("Living room Switch").build();
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(consoleGameEntryRepository.findAllByGameId(game.getId()))
                .thenReturn(List.of(ConsoleGameEntry.builder().game(game).console(console).build()));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.places()).hasSize(1);
            softly.assertThat(detail.places().getFirst().kind()).isEqualTo(PlaceResponse.PlaceKind.CONSOLE);
            softly.assertThat(detail.places().getFirst().label()).isEqualTo("Living room Switch");
            softly.assertThat(detail.places().getFirst().platform()).isEqualTo("Nintendo Switch");
            softly.assertThat(detail.places().getFirst().consoleId()).isEqualTo(console.getId());
            softly.assertThat(detail.sources()).containsExactly(GameSource.CONSOLE);
            softly.assertThat(detail.installStatus()).isEqualTo(dev.jordy.jordylab.gamecatalog.domain.InstallStatus.INSTALLED);
        });
    }

    @Test
    void detailCarriesTheRomStatusOfEachEmulatedCopyAndNoneForSteamCopies() {
        Game game = aGame("Hades");
        GameInstallation emulated = anInstallation(game, aSource("htpc", SourceType.EMUDECK));
        emulated.changeRomStatus(RomStatus.BROKEN);
        GameInstallation steam = anInstallation(game, aSource("macbook", SourceType.STEAM));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(emulated, steam));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertThat(detail.places()).extracting(PlaceResponse::romStatus).containsExactlyInAnyOrder(RomStatus.BROKEN, null);
    }

    @Test
    void aGameOnlyInTheSteamLibraryIsNotInstalled() {
        Game game = Game.builder().steamAppId("620").title("Portal 2").build();
        dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry entry = dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry.builder()
                .game(game).librarySource(dev.jordy.jordylab.gamecatalog.domain.LibrarySource.FAMILY)
                .firstSeenAt(FIRST_SEEN).lastSeenAt(LAST_SEEN).familyOwnerNames("Anna").build();
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameLibraryEntryRepository.findAllByGameId(game.getId())).thenReturn(List.of(entry));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.installStatus()).isEqualTo(dev.jordy.jordylab.gamecatalog.domain.InstallStatus.NOT_INSTALLED);
            softly.assertThat(detail.sources()).containsExactly(GameSource.STEAM_FAMILY);
            softly.assertThat(detail.places().getFirst().kind()).isEqualTo(PlaceResponse.PlaceKind.STEAM_LIBRARY);
            softly.assertThat(detail.places().getFirst().familyOwners()).isEqualTo("Anna");
        });
    }

    @Test
    void detailExposesMetadataFieldsAndProvenanceForSteamGame() {
        Game game = Game.builder()
                .steamAppId("620")
                .title("Portal 2")
                .build();
        game.applyDeterministicMetadata("Puzzle, Adventure", "Valve", "Valve", 2011);
        game.markMetadataFetched();
        GameInstallation installation = anInstallation(game, aSource("jordybox", SourceType.STEAM));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(installation));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.genres()).isEqualTo("Puzzle, Adventure");
            softly.assertThat(detail.developer()).isEqualTo("Valve");
            softly.assertThat(detail.publisher()).isEqualTo("Valve");
            softly.assertThat(detail.releaseYear()).isEqualTo(2011);
            softly.assertThat(detail.metadataSource()).isEqualTo("STEAM");
        });
    }

    @Test
    void detailHasNoMetadataProvenanceUntilMetadataIsOk() {
        Game game = Game.builder().steamAppId("620").title("Portal 2").build();
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of());

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertThat(detail.metadataSource()).isNull();
        assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
    }

    @Test
    void mapsBannerCoverAndEndpointIndependently() {
        Game game = aGame("Super Mario World");
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/cover.png");
        game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/banner.png");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of());

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.coverUrl()).isEqualTo("https://example.com/cover.png");
            softly.assertThat(detail.bannerUrl()).isEqualTo("https://example.com/banner.png");
            softly.assertThat(detail.bannerEndpoint()).isNull();
        });
    }

    @Test
    void detailNullsEnrichmentFieldsWhenNotEnriched() {
        Game game = aGame("Super Mario World");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of());

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId(), null);

        assertSoftly(softly -> {
            softly.assertThat(detail).isPresent();
            softly.assertThat(detail.get().enrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(detail.get().genre()).isNull();
            softly.assertThat(detail.get().maxLocalPlayers()).isNull();
            softly.assertThat(detail.get().onlineMultiplayer()).isNull();
            softly.assertThat(detail.get().singlePlayer()).isNull();
            softly.assertThat(detail.get().description()).isNull();
        });
    }

    @Test
    void detailIsEmptyForInvisibleGame() {
        UUID unknownId = UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");
        when(gameRepository.findVisibleById(unknownId)).thenReturn(Optional.empty());

        assertThat(gameQueryService.getGameDetail(unknownId, null)).isEmpty();
    }

    private Page<Game> pageOf(List<Game> games, long total) {
        return new PageImpl<>(games, PageRequest.of(0, 60), total);
    }

    private Game aGame(String title) {
        return Game.builder()
                .title(title)
                .build();
    }

    private ScanSource aSource(String hostname, SourceType sourceType) {
        return aSource(hostname, sourceType, true);
    }

    private ScanSource aSource(String hostname, SourceType sourceType, boolean enabled) {
        return ScanSource.builder()
                .host(Host.builder().hostname(hostname).build())
                .sourceType(sourceType)
                .enabled(enabled)
                .build();
    }

    private GameInstallation anInstallation(Game game, ScanSource source) {
        return GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(UUID.randomUUID().toString())
                .platform(PLATFORM)
                .firstSeenAt(FIRST_SEEN)
                .lastSeenAt(LAST_SEEN)
                .build();
    }

    private Host aHost(String hostname) {
        return Host.builder().hostname(hostname).build();
    }

    private Console aConsole(String name) {
        return Console.builder().platform("Nintendo Switch").name(name).build();
    }

    private GameMark aMark(Game game, String userSubject, MarkType mark) {
        return GameMark.builder().game(game).userSubject(userSubject).mark(mark).build();
    }

    private GameMarkRepository.VoteTotal voteTotal(UUID gameId, MarkType mark, long total) {
        return new GameMarkRepository.VoteTotal() {
            @Override
            public UUID getGameId() {
                return gameId;
            }

            @Override
            public MarkType getMark() {
                return mark;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
