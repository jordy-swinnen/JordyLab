package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.AiAuthorship;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
    private static final AiAuthorship AUTHORSHIP = AiAuthorship.of("model", "model", NOW);

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private GameIdentityService gameIdentityService;

    @Mock
    private PlaceRemovalService placeRemovalService;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(gameInstallationRepository, gameIdentityService,
                placeRemovalService, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsNewInstallationOnTheGameTheIdentityServiceResolves() {
        ScanSource source = aSource();
        Game resolved = Game.builder().title("Some Game").build();
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of());
        when(gameIdentityService.resolveOrCreateByTitle("Some Game", TitleSource.ROM)).thenReturn(resolved);

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "Some Game")), NOW);

        ArgumentCaptor<GameInstallation> installationCaptor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(installationCaptor.capture());
        assertThat(counts.added()).isEqualTo(1);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installationCaptor.getValue().getGame()).isSameAs(resolved);
            softly.assertThat(installationCaptor.getValue().getExternalRef()).isEqualTo("rom.smc");
            softly.assertThat(installationCaptor.getValue().getPlatform()).isEqualTo(PLATFORM);
            softly.assertThat(installationCaptor.getValue().getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(installationCaptor.getValue().getFirstSeenAt()).isEqualTo(NOW);
            softly.assertThat(installationCaptor.getValue().getSource()).isSameAs(source);
        });
    }

    @Test
    void platformNamesAreCanonicalisedBeforeTheyAreStored() {
        ScanSource source = aSource();
        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of());
        when(gameIdentityService.resolveOrCreateByTitle("Super Mario 64", TitleSource.ROM))
                .thenReturn(Game.builder().title("Super Mario 64").build());

        reconciliationService.applySnapshot(source,
                List.of(new GamePayload("sm64.z64", "Super Mario 64", "N64", false)), NOW);

        ArgumentCaptor<GameInstallation> captor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(captor.capture());
        assertThat(captor.getValue().getPlatform()).isEqualTo("Nintendo 64");
    }

    @Test
    void sameSteamGameFromAnotherHostIsAdoptedWithoutTouchingTheGame() {
        Game existing = Game.builder().steamAppId("620").title("Portal 2").titleSource(TitleSource.MANIFEST).build();
        existing.applyEnrichment("Puzzle", true, true, "A classic.", AUTHORSHIP);
        existing.applyDeterministicMetadata("Puzzle, Adventure", "Valve", "Valve", 2011);
        ScanSource secondHost = aSource(SourceType.STEAM);
        when(gameInstallationRepository.findAllBySourceId(secondHost.getId())).thenReturn(List.of());
        when(gameIdentityService.resolveOrCreateSteamGame("620", "Portal 2", TitleSource.MANIFEST)).thenReturn(existing);

        reconciliationService.applySnapshot(secondHost,
                List.of(new GamePayload("620", "Portal 2", "Steam", false)), NOW);

        ArgumentCaptor<GameInstallation> captor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(gameInstallationRepository).save(captor.capture());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getGame()).isSameAs(existing);
            softly.assertThat(captor.getValue().getSource()).isSameAs(secondHost);
            softly.assertThat(captor.getValue().getPlatform()).isEqualTo("Steam");
            softly.assertThat(existing.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
            softly.assertThat(existing.getDescription()).isEqualTo("A classic.");
            softly.assertThat(existing.getReleaseYear()).isEqualTo(2011);
        });
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
        verifyNoInteractions(gameIdentityService);
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
    void aSpellingOfTheSamePlatformIsNoUpdateButARealPlatformChangeIs() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "rom.smc", "Some Game");

        when(gameInstallationRepository.findAllBySourceId(source.getId())).thenReturn(List.of(existing));

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(new GamePayload("rom.smc", "Some Game", "Super Nintendo", false)), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(counts.updated()).isZero();
            softly.assertThat(existing.getPlatform()).isEqualTo("SNES");
        });
        ScanSource otherSource = aSource();
        GameInstallation moved = anInstallation(otherSource, "gb.gb", "Some GB Game");
        when(gameInstallationRepository.findAllBySourceId(otherSource.getId())).thenReturn(List.of(moved));

        ReconciliationCounts movedCounts = reconciliationService.applySnapshot(otherSource,
                List.of(new GamePayload("gb.gb", "Some GB Game", "Game Boy", false)), NOW);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(movedCounts.updated()).isEqualTo(1);
            softly.assertThat(moved.getPlatform()).isEqualTo("Game Boy");
        });
    }

    @Test
    void rediscoveredGameWithinGraceIsRestoredWithDataIntact() {
        ScanSource source = aSource();
        GameInstallation existing = anInstallation(source, "rom.smc", "Some Game");
        existing.getGame().applyEnrichment("Platformer", false, true, "A classic.", AUTHORSHIP);
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
        when(gameIdentityService.resolveOrCreateByTitle("First", TitleSource.ROM))
                .thenReturn(Game.builder().title("First").build());

        ReconciliationCounts counts = reconciliationService.applySnapshot(source,
                List.of(payload("rom.smc", "First"), payload("rom.smc", "Second")), NOW);

        assertThat(counts.added()).isEqualTo(1);
        verify(gameIdentityService, never()).resolveOrCreateByTitle("Second", TitleSource.ROM);
    }

    @Test
    void purgeDeletesExpiredInstallationsAndReleasesEachAffectedGame() {
        Game game = Game.builder().title("Old Game").build();
        GameInstallation expired = anInstallation(aSource(), game, "old.smc");
        expired.markUninstalled(NOW.minusSeconds(40L * 24 * 3600));
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(gameInstallationRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                cutoffCaptor.capture())).thenReturn(List.of(expired));
        when(placeRemovalService.releaseGameIfOrphaned(game.getId())).thenReturn(true);

        reconciliationService.purgeUninstalledGames();

        assertThat(cutoffCaptor.getValue()).isEqualTo(NOW.minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS));
        verify(gameInstallationRepository).deleteAll(List.of(expired));
        verify(placeRemovalService).releaseGameIfOrphaned(game.getId());
    }

    @Test
    void purgeDoesNothingWhenNothingExpired() {
        when(gameInstallationRepository.findByPresenceAndUninstalledAtBefore(eq(Presence.UNINSTALLED),
                eq(NOW.minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS)))).thenReturn(List.of());

        reconciliationService.purgeUninstalledGames();

        verify(gameInstallationRepository, never()).deleteAll(List.of());
        verifyNoInteractions(placeRemovalService);
    }

    private ScanSource aSource() {
        return aSource(SourceType.EMUDECK);
    }

    private ScanSource aSource(SourceType sourceType) {
        return ScanSource.builder()
                .host(Host.builder().hostname("jordybox").build())
                .sourceType(sourceType)
                .platform(sourceType.platform())
                .enabled(true)
                .build();
    }

    private GameInstallation anInstallation(ScanSource source, String externalRef, String title) {
        return anInstallation(source, Game.builder().title(title).build(), externalRef);
    }

    private GameInstallation anInstallation(ScanSource source, Game game, String externalRef) {
        return GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(externalRef)
                .platform(PLATFORM)
                .firstSeenAt(NOW.minusSeconds(172800))
                .lastSeenAt(NOW.minusSeconds(86400))
                .build();
    }

    private GamePayload payload(String externalRef, String title) {
        return new GamePayload(externalRef, title, PLATFORM, false);
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/unused", 2097152L, true, 2000L),
                GRACE_PERIOD_DAYS,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5), null);
    }
}
