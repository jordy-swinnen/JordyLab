package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameQueryServiceTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 60);

    private static final Instant FIRST_SEEN = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant LAST_SEEN = Instant.parse("2026-08-02T10:15:00Z");
    private static final String PLATFORM = "SNES";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private GameLibraryEntryRepository gameLibraryEntryRepository;

    private GameQueryService gameQueryService;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository, gameInstallationRepository,
                gameLibraryEntryRepository);
    }

    @Test
    void mapsVisibleGamesToSummariesWithExternalCoverUrl() {
        Game game = aGame("Super Mario World");
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png");
        when(gameRepository.findVisibleGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(game), 1));

        GamesPageResponse response = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content()).hasSize(1);
            GameSummaryResponse summary = response.content().getFirst();
            softly.assertThat(summary.id()).isEqualTo(game.getId());
            softly.assertThat(summary.title()).isEqualTo("Super Mario World");
            softly.assertThat(summary.platform()).isEqualTo(PLATFORM);
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
        when(gameRepository.findVisibleGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(game), 1));

        GamesPageResponse response = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);

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
        when(gameRepository.findVisibleGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(pending, placeholder), 2));

        GamesPageResponse response = gameQueryService.getGames(null, null, null, null, null, null, 0, 60);

        assertThat(response.content()).allSatisfy(summary -> assertSoftly(softly -> {
            softly.assertThat(summary.coverUrl()).isNull();
            softly.assertThat(summary.coverEndpoint()).isNull();
        }));
    }

    @Test
    void passesSearchPlatformHostAndPaginationToRepository() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(gameRepository.findVisibleGames(eq("mario"), eq(PLATFORM), eq("jordybox"), eq("INSTALLED"), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(pageOf(List.of(), 0));

        gameQueryService.getGames("mario", PLATFORM, "jordybox", null, null, null, 2, 60);

        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(2, 60));
    }

    @Test
    void blankFiltersBecomeNullForRepository() {
        when(gameRepository.findVisibleGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(FIRST_PAGE)))
                .thenReturn(pageOf(List.of(), 0));

        gameQueryService.getGames(" ", "", " ", null, null, null, 0, 60);

        verify(gameRepository).findVisibleGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(FIRST_PAGE));
    }

    @Test
    void returnsVisiblePlatforms() {
        when(gameRepository.findVisiblePlatforms()).thenReturn(List.of("SNES", "Steam"));

        PlatformsResponse response = gameQueryService.getPlatforms();

        assertThat(response.platforms()).containsExactly("SNES", "Steam");
    }

    @Test
    void returnsVisibleHosts() {
        when(gameRepository.findVisibleHosts()).thenReturn(List.of("jordybox", "ryzen-desktop"));

        HostsResponse response = gameQueryService.getHosts();

        assertThat(response.hosts()).containsExactly("jordybox", "ryzen-desktop");
    }

    @Test
    void detailExposesEnrichmentFieldsForEnrichedGame() {
        Game game = aGame("Super Mario World");
        game.applyEnrichment("Platformer", false, true, "A classic.");
        GameInstallation installation = anInstallation(game, aSource("jordybox", SourceType.EMUDECK));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(installation));

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(detail).isPresent();
            softly.assertThat(detail.get().genre()).isEqualTo("Platformer");            softly.assertThat(detail.get().description()).isEqualTo("A classic.");
            softly.assertThat(detail.get().hosts()).hasSize(1);
            softly.assertThat(detail.get().hosts().getFirst().hostname()).isEqualTo("jordybox");
            softly.assertThat(detail.get().hosts().getFirst().sourceType()).isEqualTo(SourceType.EMUDECK);
            softly.assertThat(detail.get().firstSeenAt()).isEqualTo(FIRST_SEEN);
        });
    }

    @Test
    void detailListsOnlyInstalledEnabledHosts() {
        Game game = aGame("Super Mario World");
        GameInstallation enabled = anInstallation(game, aSource("jordybox", SourceType.EMUDECK));
        GameInstallation disabled = anInstallation(game, aSource("disabled-host", SourceType.STEAM, false));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(enabled, disabled));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();

        assertThat(detail.hosts()).hasSize(1);
        assertThat(detail.hosts().getFirst().hostname()).isEqualTo("jordybox");
    }

    @Test
    void detailMapsManualHostsToTheirFormatAndOmitsScannedHosts() {
        Game game = aGame("Mario Kart 8 Deluxe");
        GameInstallation switchCopy = GameInstallation.createManual(game, aSource("Nintendo Switch", SourceType.SWITCH),
                "igdb:1234", InstallationFormat.PHYSICAL, FIRST_SEEN);
        GameInstallation steamCopy = anInstallation(game, aSource("jordybox", SourceType.STEAM));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(switchCopy, steamCopy));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();

        assertThat(detail.hostFormats()).containsExactly(Map.entry("Nintendo Switch", InstallationFormat.PHYSICAL));
    }

    @Test
    void detailExposesMetadataFieldsAndProvenanceForSteamGame() {
        Game game = Game.builder()
                .platform("Steam")
                .steamAppId("620")
                .title("Portal 2")
                .build();
        game.applyDeterministicMetadata("Puzzle, Adventure", "Valve", "Valve", 2011);
        game.markMetadataFetched();
        GameInstallation installation = anInstallation(game, aSource("jordybox", SourceType.STEAM));
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of(installation));

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();

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
        Game game = Game.builder().platform("Steam").steamAppId("620").title("Portal 2").build();
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
        when(gameInstallationRepository.findAllByGameId(game.getId())).thenReturn(List.of());

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();

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

        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId()).orElseThrow();

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

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId());

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

        assertThat(gameQueryService.getGameDetail(unknownId)).isEmpty();
    }

    private Page<Game> pageOf(List<Game> games, long total) {
        return new PageImpl<>(games, PageRequest.of(0, 60), total);
    }

    private Game aGame(String title) {
        return Game.builder()
                .platform(PLATFORM)
                .title(title)
                .build();
    }

    private ScanSource aSource(String hostname, SourceType sourceType) {
        return aSource(hostname, sourceType, true);
    }

    private ScanSource aSource(String hostname, SourceType sourceType, boolean enabled) {
        return ScanSource.builder()
                .hostname(hostname)
                .sourceType(sourceType)
                .enabled(enabled)
                .build();
    }

    private GameInstallation anInstallation(Game game, ScanSource source) {
        return GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(UUID.randomUUID().toString())
                .firstSeenAt(FIRST_SEEN)
                .lastSeenAt(LAST_SEEN)
                .build();
    }
}
