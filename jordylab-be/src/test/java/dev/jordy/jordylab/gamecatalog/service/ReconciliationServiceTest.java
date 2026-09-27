package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final int GRACE_PERIOD_DAYS = 30;
    private static final String PLATFORM = "SNES";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @TempDir
    private Path artworkDir;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(gameRepository, gameInstallationRepository, properties());
    }

    @Test
    void addsNewGameWithInstallation() {
        ScanSource source = aSource();
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of());
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        when(gameRepository.save(gameCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "Some Game")), NOW);

        ArgumentCaptor<GameInstallation> installationCaptor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(installationCaptor.capture());
        assertThat(counts.added()).isEqualTo(1);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(gameCaptor.getValue().getTitle()).isEqualTo("Some Game");
            softly.assertThat(gameCaptor.getValue().getPlatform()).isEqualTo(PLATFORM);
            softly.assertThat(installationCaptor.getValue().getExternalRef()).isEqualTo("rom.smc");
            softly.assertThat(installationCaptor.getValue().getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(installationCaptor.getValue().getFirstSeenAt()).isEqualTo(NOW);
            softly.assertThat(installationCaptor.getValue().getSource()).isSameAs(source);
        });
    }

    @Test
    void newSteamGameCarriesSteamAppIdAsIdentity() {
        ScanSource source = aSource(SourceType.STEAM);
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of());
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        when(gameRepository.save(gameCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationService.applySnapshot(source, List.of(new GamePayload("620", "Portal 2", "Steam", false)), NOW);

        assertThat(gameCaptor.getValue().getSteamAppId()).isEqualTo("620");
    }

    @Test
    void sameSteamGameFromAnotherHostIsAdoptedWithoutTouchingTheGame() {
        Game existing = Game.builder().platform("Steam").steamAppId("620").title("Portal 2").build();
        existing.applyEnrichment("Puzzle", 2, true, true, "A classic.");
        existing.applyDeterministicMetadata("Puzzle, Adventure", "Valve", "Valve", 2011);
        ScanSource secondHost = aSource(SourceType.STEAM);
        when(gameInstallationRepository.findAllBySourceId(secondHost.getId())).thenReturn(List.of());
        when(gameRepository.findByPlatformAndSteamAppId("Steam", "620")).thenReturn(Optional.of(existing));

        reconciliationService.applySnapshot(secondHost,
                List.of(new GamePayload("620", "Portal 2", "Steam", false)), NOW);

        ArgumentCaptor<GameInstallation> captor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(captor.capture());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getGame()).isSameAs(existing);
            softly.assertThat(captor.getValue().getSource()).isSameAs(secondHost);
            softly.assertThat(existing.getEnrichmentStatus())
                    .isEqualTo(dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus.ENRICHED);
            softly.assertThat(existing.getDescription()).isEqualTo("A classic.");
            softly.assertThat(existing.getReleaseYear()).isEqualTo(2011);
        });
    }

    @Test
    void sameRomTitleOnAnotherHostIsAdoptedByPlatformAndNormalizedTitle() {
        Game existing = Game.builder().platform(PLATFORM).title("Super Mario World").build();
        ScanSource secondHost = aSource();
        when(gameInstallationRepository.findAllBySourceId(secondHost.getId())).thenReturn(List.of());
        when(gameRepository.findByPlatformAndLowercaseTitle(eq(PLATFORM), eq("Super Mario World"),
                eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(existing));

        reconciliationService.applySnapshot(secondHost, List.of(payload("other-host/smw.smc", "Super Mario World")),
                NOW);

        ArgumentCaptor<GameInstallation> captor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(captor.capture());
        assertThat(captor.getValue().getGame()).isSameAs(existing);
    }

    @Test
    void unchangedGameIsNoOpBeyondSeenAgain() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "rom.smc", "Some Game");
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "Some Game")), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.updated()).isZero();
            softly.assertThat(counts.added()).isZero();
            softly.assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        });
        verifyNoInteractions(gameRepository);
    }

    @Test
    void changedTitleCountsAsUpdate() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "rom.smc", "Old Title");
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "New Title")), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.updated()).isEqualTo(1);
            softly.assertThat(existing.getGame().getTitle()).isEqualTo("New Title");
        });
    }

    @Test
    void rediscoveredGameWithinGraceIsRestoredWithDataIntact() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "rom.smc", "Some Game");
        existing.getGame().applyEnrichment("Platformer", 2, false, true, "A classic.");
        existing.markUninstalled(NOW.minusSeconds(86400));
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        reconciliationService.applySnapshot(source, List.of(payload("rom.smc", "Some Game")), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(existing.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(existing.getUninstalledAt()).isNull();
            softly.assertThat(existing.getGame().getDescription()).isEqualTo("A classic.");
            softly.assertThat(existing.getGame().getGenre()).isEqualTo("Platformer");
        });
    }

    @Test
    void gamesMissingFromSnapshotAreHiddenPerHost() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "gone.smc", "Gone Game");
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source, List.of(), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.removed()).isEqualTo(1);
            softly.assertThat(existing.getPresence()).isEqualTo(Presence.UNINSTALLED);
            softly.assertThat(existing.getUninstalledAt()).isEqualTo(NOW);
        });
    }

    @Test
    void duplicateRefsInPayloadKeepFirstEntry() {
        ScanSource source = aSource();
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of());
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        when(gameRepository.save(gameCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "First"), payload("rom.smc", "Second")), NOW);

        assertThat(counts.added()).isEqualTo(1);
        assertThat(gameCaptor.getValue().getTitle()).isEqualTo("First");
    }

    @Test
    void purgeDeletesExpiredInstallationsAndOrphanedGameWithArtwork() throws Exception {
        Game game = Game.builder().platform(PLATFORM).title("Old Game").build();
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/abc.png");
        GameInstallation expired = anInstallation(aSource(), game, "old.smc");
        expired.markUninstalled(NOW.minusSeconds(40L * 24 * 3600));
        Path artworkFile = artworkDir.resolve("snes/abc.png");
        Files.createDirectories(artworkFile.getParent());
        Files.writeString(artworkFile, "fake-image");
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(gameInstallationRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                cutoffCaptor.capture())).thenReturn(List.of(expired));
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(0L);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        Instant before = Instant.now();

        reconciliationService.purgeUninstalledGames();

        Instant after = Instant.now();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Files.exists(artworkFile)).isFalse();
            softly.assertThat(cutoffCaptor.getValue()).isBetween(
                    before.minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS),
                    after.minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS));
        });
        verify(gameInstallationRepository).deleteAll(List.of(expired));
        verify(gameRepository).delete(game);
    }

    @Test
    void purgeKeepsGameWhenAnotherHostInstallationRemains() {
        Game game = Game.builder().platform(PLATFORM).title("Shared Game").build();
        GameInstallation expired = anInstallation(aSource(), game, "old.smc");
        expired.markUninstalled(NOW.minusSeconds(40L * 24 * 3600));
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(gameInstallationRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                cutoffCaptor.capture())).thenReturn(List.of(expired));
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(1L);

        reconciliationService.purgeUninstalledGames();

        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now());
        verify(gameRepository, never()).delete(game);
        verify(gameRepository, never()).findById(game.getId());
    }

    @Test
    void purgeDoesNothingWhenNothingExpired() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(gameInstallationRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                cutoffCaptor.capture())).thenReturn(List.of());

        reconciliationService.purgeUninstalledGames();

        assertThat(cutoffCaptor.getValue()).isBefore(Instant.now());
        verify(gameInstallationRepository, never()).deleteAll(List.of());
    }

    private ScanSource aSource() {
        return aSource(SourceType.EMUDECK);
    }

    private ScanSource aSource(SourceType sourceType) {
        return ScanSource.builder()
                .hostname("jordybox")
                .sourceType(sourceType)
                .platform(sourceType.platform())
                .enabled(true)
                .build();
    }

    private GameInstallation anInstallation(ScanSource source, String externalRef, String title) {
        return anInstallation(source, Game.builder().platform(PLATFORM).title(title).build(), externalRef);
    }

    private GameInstallation anInstallation(ScanSource source, Game game, String externalRef) {
        return GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(externalRef)
                .firstSeenAt(NOW.minusSeconds(172800))
                .lastSeenAt(NOW.minusSeconds(86400))
                .build();
    }

    private GamePayload payload(String externalRef, String title) {
        return new GamePayload(externalRef, title, PLATFORM, false);
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork(artworkDir.toString(), 2097152L, true, 2000L),
                GRACE_PERIOD_DAYS,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5));
    }
}
