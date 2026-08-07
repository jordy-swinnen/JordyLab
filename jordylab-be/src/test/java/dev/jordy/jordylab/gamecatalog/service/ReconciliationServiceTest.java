package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final String PLATFORM = "SNES";

    @Mock
    private GameRepository gameRepository;

    @TempDir
    private Path artworkDir;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(gameRepository, properties());
    }

    @Test
    void addsNewGames() {
        ScanSource source = aSource();
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of());

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(new dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload("rom.smc", "Some Game",
                        PLATFORM, false)), NOW);

        assertThat(counts.added()).isEqualTo(1);
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getTitle()).isEqualTo("Some Game");
            softly.assertThat(captor.getValue().getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(captor.getValue().getFirstSeenAt()).isEqualTo(NOW);
        });
    }

    @Test
    void unchangedGameIsNoOpBeyondSeenAgain() {
        ScanSource source = aSource();
        Game existing = aGame(source, "rom.smc", "Some Game");
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "Some Game")), NOW);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.updated()).isZero();
            softly.assertThat(counts.added()).isZero();
            softly.assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        });
    }

    @Test
    void changedTitleCountsAsUpdate() {
        ScanSource source = aSource();
        Game existing = aGame(source, "rom.smc", "Old Title");
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "New Title")), NOW);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.updated()).isEqualTo(1);
            softly.assertThat(existing.getTitle()).isEqualTo("New Title");
        });
    }

    @Test
    void rediscoveredGameWithinGraceIsRestoredWithDataIntact() {
        ScanSource source = aSource();
        Game existing = aGame(source, "rom.smc", "Some Game");
        existing.applyEnrichment("Platformer", 2, false, true, "A classic.");
        existing.markUninstalled(NOW.minusSeconds(86400));
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        reconciliationService.applySnapshot(source, List.of(payload("rom.smc", "Some Game")), NOW);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(existing.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(existing.getUninstalledAt()).isNull();
            softly.assertThat(existing.getDescription()).isEqualTo("A classic.");
            softly.assertThat(existing.getGenre()).isEqualTo("Platformer");
        });
    }

    @Test
    void gamesMissingFromSnapshotAreHidden() {
        ScanSource source = aSource();
        Game existing = aGame(source, "gone.smc", "Gone Game");
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source, List.of(), NOW);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.removed()).isEqualTo(1);
            softly.assertThat(existing.getPresence()).isEqualTo(Presence.UNINSTALLED);
            softly.assertThat(existing.getUninstalledAt()).isEqualTo(NOW);
        });
    }

    @Test
    void duplicateRefsInPayloadKeepFirstEntry() {
        ScanSource source = aSource();
        when(gameRepository.findAllBySourceId(source.getId())).thenReturn(List.of());

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "First"), payload("rom.smc", "Second")), NOW);

        assertThat(counts.added()).isEqualTo(1);
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("First");
    }

    @Test
    void purgeDeletesExpiredGamesAndTheirLocalArtwork() throws Exception {
        Game expired = aGame(aSource(), "old.smc", "Old Game");
        expired.markUninstalled(NOW.minusSeconds(40L * 24 * 3600));
        expired.applyArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/abc.png");
        Path artworkFile = artworkDir.resolve("snes/abc.png");
        Files.createDirectories(artworkFile.getParent());
        Files.writeString(artworkFile, "fake-image");
        when(gameRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(expired));

        reconciliationService.purgeUninstalledGames();

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Files.exists(artworkFile)).isFalse();
        });
        verify(gameRepository).deleteAll(List.of(expired));
    }

    @Test
    void purgeDoesNothingWhenNothingExpired() {
        when(gameRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        reconciliationService.purgeUninstalledGames();

        verify(gameRepository, org.mockito.Mockito.never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }

    private ScanSource aSource() {
        return ScanSource.builder()
                .sourceKey("snes")
                .hostname("jordybox")
                .sourceType(SourceType.EMUDECK)
                .platform(PLATFORM)
                .enabled(true)
                .build();
    }

    private Game aGame(ScanSource source, String externalRef, String title) {
        return Game.builder()
                .source(source)
                .platform(PLATFORM)
                .externalRef(externalRef)
                .title(title)
                .firstSeenAt(NOW.minusSeconds(172800))
                .lastSeenAt(NOW.minusSeconds(86400))
                .build();
    }

    private dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload payload(String externalRef,
            String title) {
        return new dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload(externalRef, title, PLATFORM,
                false);
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                                new GameCatalogProperties.Artwork(artworkDir.toString(), 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144));
    }
}
