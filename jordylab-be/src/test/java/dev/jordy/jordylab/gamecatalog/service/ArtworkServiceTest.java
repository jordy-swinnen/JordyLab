package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
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
    private ArtworkLookupClient artworkLookupClient;

    @TempDir
    private Path artworkDir;

    private ArtworkService artworkService;

    @BeforeEach
    void setUp() {
        artworkService = new ArtworkService(gameRepository, artworkLookupClient, properties(true));
    }

    @Test
    void resolvesSteamGameToDeterministicCdnUrl() {
        ScanSource source = aSource(SourceType.STEAM);
        Game game = aGame(source, "620", "Portal 2");
        stubGames(source, game);
        when(artworkLookupClient.findExternalArtworkUrl(SourceType.STEAM, "Steam", "620", "Portal 2"))
                .thenReturn(Optional.of("https://cdn.example/steam/apps/620/header.jpg"));

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("620", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getArtworkRef()).isEqualTo("https://cdn.example/steam/apps/620/header.jpg");
        });
    }

    @Test
    void resolvesEmudeckGameOnLibretroProbeHit() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        stubGames(source, game);
        when(artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, source.getSourceType().platform(),
                "smw.smc", "Super Mario World"))
                .thenReturn(Optional.of("https://libretro.example/smw.png"));

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("smw.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(game.getArtworkRef()).isEqualTo("https://libretro.example/smw.png");
        });
    }

    @Test
    void requestsLocalFallbackWhenProbeMissesAndScriptHasArtwork() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        stubGames(source, game);
        when(artworkLookupClient.findExternalArtworkUrl(eq(SourceType.EMUDECK), eq(source.getSourceType().platform()),
                eq("smw.smc"), eq("Super Mario World"))).thenReturn(Optional.empty());

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("smw.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).containsExactly("smw.smc");
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.LOCAL_FALLBACK_REQUESTED);
            softly.assertThat(game.getArtworkFallbackRequests()).isEqualTo(1);
        });
    }

    @Test
    void marksPlaceholderWhenProbeMissesAndScriptHasNoArtwork() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        stubGames(source, game);
        when(artworkLookupClient.findExternalArtworkUrl(eq(SourceType.EMUDECK), eq(source.getSourceType().platform()),
                eq("smw.smc"), eq("Super Mario World"))).thenReturn(Optional.empty());

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("smw.smc", false)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void skipsExternalLookupWhenDisabled() {
        artworkService = new ArtworkService(gameRepository, artworkLookupClient, properties(false));
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        stubGames(source, game);

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("smw.smc", true)));

        assertThat(requested).containsExactly("smw.smc");
        verifyNoInteractions(artworkLookupClient);
    }

    @Test
    void agesStaleFallbackRequestToPlaceholderAfterMaxSyncs() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        game.requestLocalArtworkFallback();
        game.requestLocalArtworkFallback();
        game.requestLocalArtworkFallback();
        stubGames(source, game);

        List<String> requested = artworkService.processArtworkAfterSync(source, List.of(payload("smw.smc", true)));

        assertSoftly(softly -> {
            softly.assertThat(requested).isEmpty();
            softly.assertThat(game.getArtworkStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        });
    }

    @Test
    void leavesTerminalArtworkStatesUntouched() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game external = aGame(source, "a.smc", "A");
        external.applyArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/a.png");
        Game uploaded = aGame(source, "b.smc", "B");
        uploaded.applyArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/b.png");
        stubGames(source, external, uploaded);

        List<String> requested = artworkService.processArtworkAfterSync(source,
                List.of(payload("a.smc", true), payload("b.smc", true)));

        assertThat(requested).isEmpty();
        verifyNoInteractions(artworkLookupClient);
        assertSoftly(softly -> {
            softly.assertThat(external.getArtworkStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(uploaded.getArtworkStatus()).isEqualTo(ArtworkStatus.LOCAL_UPLOAD);
        });
    }

    @Test
    void loadsVisibleArtworkForLocalUpload() throws Exception {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        String relativeRef = "smw.png";
        Files.createDirectories(artworkDir);
        Files.write(artworkDir.resolve(relativeRef), PNG_BYTES);
        game.applyArtwork(ArtworkStatus.LOCAL_UPLOAD, relativeRef);
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
        Game game = aGame(source, "smw.smc", "Super Mario World");
        game.applyArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<ArtworkContent> content = artworkService.loadVisibleArtwork(game.getId());

        assertThat(content).isEmpty();
    }

    @Test
    void loadVisibleArtworkIsEmptyWhenFileIsMissing() {
        ScanSource source = aSource(SourceType.EMUDECK);
        Game game = aGame(source, "smw.smc", "Super Mario World");
        game.applyArtwork(ArtworkStatus.LOCAL_UPLOAD, "gone.png");
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
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of(games));
    }

    private ScanSource aSource(SourceType sourceType) {
        return ScanSource.builder()
                .hostname(HOSTNAME)
                .sourceType(sourceType)
                .enabled(true)
                .build();
    }

    private Game aGame(ScanSource source, String externalRef, String title) {
        return Game.builder()
                .source(source)
                .platform(source.getSourceType().platform())
                .externalRef(externalRef)
                .title(title)
                .firstSeenAt(SEEN_AT)
                .lastSeenAt(SEEN_AT)
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
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144));
    }
}
