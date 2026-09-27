package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.ArtworkLookupClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtworkServiceTest {

    private static final Instant SEEN_AT = Instant.parse("2026-08-02T10:15:00Z");
    private static final String PLATFORM = "SNES";
    private static final String HOSTNAME = "jordybox";
    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private ArtworkLookupClient artworkLookupClient;

    @TempDir
    private Path artworkDir;

    private ArtworkService artworkService;

    @BeforeEach
    void setUp() {
        artworkService = new ArtworkService(gameRepository, gameInstallationRepository, artworkLookupClient,
                properties(true));
    }

    @Test
    void resolvesSteamGameToPortraitCoverAndHeroBanner() {
        ScanSource source = aSource(SourceType.STEAM);
        Game game = aGame(source, "620", "Portal 2", "620");
        stubGames(source, game);
        when(artworkLookupClient.findCoverArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn.example/steam/apps/620/library_600x900.jpg"));
        when(artworkLookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn.example/steam/apps/620/library_hero.jpg"));

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("620", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef())
                    .isEqualTo("https://cdn.example/steam/apps/620/library_600x900.jpg");
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getBannerRef()).isEqualTo("https://cdn.example/steam/apps/620/library_hero.jpg");
        });
    }

    @Test
    void resolvesEmudeckGameOnLibretroProbeHit() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        stubGames(source, game);
        when(artworkLookupClient.findCoverArtworkUrl(SourceType.EMUDECK, PLATFORM, null,
                "Super Mario World")).thenReturn(Optional.of("https://libretro.example/smw.png"));
        when(artworkLookupClient.findBannerArtworkUrl(SourceType.EMUDECK, PLATFORM, null,
                "Super Mario World")).thenReturn(Optional.of("https://libretro.example/smw-snap.png"));

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://libretro.example/smw.png");
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getBannerRef()).isEqualTo("https://libretro.example/smw-snap.png");
        });
    }

    @Test
    void bannerFallsBackToPlaceholderWhenMissing() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        stubGames(source, game);
        when(artworkLookupClient.findCoverArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.of("https://libretro.example/smw.png"));
        when(artworkLookupClient.findBannerArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());

        artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", true)));

        assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        assertThat(game.getBannerRef()).isNull();
    }

    @Test
    void requestsLocalFallbackWhenProbeMissesAndScriptHasArtwork() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        stubGames(source, game);
        when(artworkLookupClient.findCoverArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());
        when(artworkLookupClient.findBannerArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).containsExactly("Super Mario World");
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.LOCAL_FALLBACK_REQUESTED);
            softly.assertThat(game.getArtworkFallbackRequests()).isEqualTo(1);
        });
    }

    @Test
    void marksCoverPlaceholderWhenProbeMissesAndScriptHasNoArtwork() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        stubGames(source, game);
        when(artworkLookupClient.findCoverArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());
        when(artworkLookupClient.findBannerArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", false)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void skipsExternalLookupWhenDisabled() {
        artworkService = new ArtworkService(gameRepository, gameInstallationRepository, artworkLookupClient,
                properties(false));
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        stubGames(source, game);

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", true)));

        assertThat(requested).containsExactly("Super Mario World");
        verifyNoInteractions(artworkLookupClient);
    }

    @Test
    void agesStaleFallbackRequestToPlaceholderAfterMaxSyncs() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        game.requestLocalCoverFallback();
        game.requestLocalCoverFallback();
        game.requestLocalCoverFallback();
        stubGames(source, game);
        when(artworkLookupClient.findBannerArtworkUrl(eq(SourceType.EMUDECK), eq(PLATFORM), eq(null),
                eq("Super Mario World"))).thenReturn(Optional.empty());

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("Super Mario World.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void leavesTerminalCoverStatesUntouched() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game external = aGame(source, "a.smc", "A", null);
        external.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/a.png");
        external.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);
        Game uploaded = aGame(source, "b.smc", "B", null);
        uploaded.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/b.png");
        uploaded.applyBannerArtwork(ArtworkStatus.PLACEHOLDER, null);
        stubGames(source, external, uploaded);

        List<String> requested = artworkService.processArtworkAfterSync(source,
                List.of(payload("A.smc", true), payload("B.smc", true)));

        assertThat(requested).isEmpty();
        verifyNoInteractions(artworkLookupClient);
        assertSoftly(softly -> {
            softly.assertThat(external.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(uploaded.getCoverStatus()).isEqualTo(ArtworkStatus.LOCAL_UPLOAD);
        });
    }

    @Test
    void loadsVisibleArtworkForLocalUpload() throws Exception {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        String relativeRef = "smw.png";
        Files.createDirectories(artworkDir);
        Files.write(artworkDir.resolve(relativeRef), PNG_BYTES);
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, relativeRef);
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<ArtworkContent> content = artworkService.loadVisibleArtwork(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(content).isPresent();
            softly.assertThat(content.get().bytes()).isEqualTo(PNG_BYTES);
            softly.assertThat(content.get().mediaType()).isEqualTo("image/png");
        });
    }

    @Test
    void loadVisibleArtworkIsEmptyForNonUploadStatuses() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<ArtworkContent> content = artworkService.loadVisibleArtwork(game.getId());

        assertThat(content).isEmpty();
    }

    @Test
    void loadVisibleArtworkIsEmptyWhenFileIsMissing() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World", null);
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "gone.png");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<ArtworkContent> content = artworkService.loadVisibleArtwork(game.getId());

        assertThat(content).isEmpty();
    }

    @Test
    void loadVisibleArtworkIsEmptyForInvisibleGame() {
        UUID unknownId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(gameRepository.findVisibleById(unknownId)).thenReturn(Optional.empty());

        Optional<ArtworkContent> content = artworkService.loadVisibleArtwork(unknownId);

        assertThat(content).isEmpty();
    }

    private void stubGames(ScanSource source, Game... games) {
        List<GameInstallation> installations = List.of(games).stream()
                .map(game -> GameInstallation.builder()
                        .game(game)
                        .source(source)
                        .externalRef(game.getSteamAppId() == null ? game.getTitle() + ".smc" : game.getSteamAppId())
                        .firstSeenAt(SEEN_AT)
                        .lastSeenAt(SEEN_AT)
                        .build())
                .toList();
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(installations);
    }

    @Test
    void resolvesLibraryGameArtworkFromSteamCdn() {
        Game game = Game.builder().platform("Steam").steamAppId("620").title("Portal 2").build();
        when(artworkLookupClient.findCoverArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn.example/620/library_600x900.jpg"));
        when(artworkLookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn.example/620/library_hero.jpg"));

        int processed = artworkService.processLibraryGames(List.of(game));

        assertSoftly(softly -> {
            softly.assertThat(processed).isEqualTo(1);
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getCoverRef()).isEqualTo("https://cdn.example/620/library_600x900.jpg");
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
        });
    }

    @Test
    void libraryGameWithoutCdnArtBecomesPlaceholder() {
        Game game = Game.builder().platform("Steam").steamAppId("999999").title("Obscure").build();
        when(artworkLookupClient.findCoverArtworkUrl(SourceType.STEAM, "Steam", "999999", "Obscure"))
                .thenReturn(Optional.empty());
        when(artworkLookupClient.findBannerArtworkUrl(SourceType.STEAM, "Steam", "999999", "Obscure"))
                .thenReturn(Optional.empty());

        artworkService.processLibraryGames(List.of(game));

        assertSoftly(softly -> {
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
            softly.assertThat(game.getBannerStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void libraryArtworkSkipsGamesWithoutSteamAppId() {
        Game game = Game.builder().platform("SNES").title("Super Mario World").build();

        int processed = artworkService.processLibraryGames(List.of(game));

        assertThat(processed).isZero();
        verifyNoInteractions(artworkLookupClient);
    }

    private ScanSource aSource(SourceType sourceType) {
        return ScanSource.builder()
                .hostname(HOSTNAME)
                .sourceType(sourceType)
                .enabled(true)
                .build();
    }

    private Game aGame(ScanSource source, String externalRef, String title, String steamAppId) {
        return Game.builder()
                .platform(steamAppId == null ? PLATFORM : "Steam")
                .steamAppId(steamAppId)
                .title(title)
                .build();
    }

    private GamePayload payload(String externalRef, boolean localArtworkAvailable) {
        return new GamePayload(externalRef, "Some Title", PLATFORM, localArtworkAvailable);
    }

    private GameCatalogProperties properties(boolean externalLookupEnabled) {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork(artworkDir.toString(), 2097152L, externalLookupEnabled, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5), null);
    }
}
