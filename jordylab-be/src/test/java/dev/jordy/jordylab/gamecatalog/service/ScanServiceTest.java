package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.SyncReport;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ClientGame;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.scan.LibraryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    private static final String HOSTNAME = "jordybox";
    private static final Host HOST = Host.builder().hostname(HOSTNAME).build();
    private static final String MACHINE_ID = "9f1c0a7e-2b3d-4c5e-8f90-1a2b3c4d5e6f";
    private static final String DIGEST = "sha256:abc123";
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-02T10:20:00Z");
    private static final ScanEntry MARIO_ENTRY = new ScanEntry("snes/Super Mario World.sfc", 524288L, CAPTURED_AT);
    private static final GamePayload MARIO = new GamePayload("snes/Super Mario World.sfc", "Super Mario World", "SNES", false);
    private static final GamePayload CLIENT_MARIO = new GamePayload("snes/Super Mario World.sfc", "Super Mario World", "SNES", null);
    private static final GamePayload ZELDA = new GamePayload("snes/Zelda.sfc", "Zelda", "SNES", false);

    @Mock
    private ScanLock scanLock;

    @Mock
    private ScanSourceRepository scanSourceRepository;

    @Mock
    private HostRepository hostRepository;

    @Mock
    private SyncReportRepository syncReportRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private ArtworkService artworkService;

    @Mock
    private SteamLibrarySyncService steamLibrarySyncService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private LibraryParser emuDeckParser;

    @Test
    void payloadOverTheByteCapIsRejectedWithItsReasonAndTouchesNothing() {
        ScanService service = serviceWithLimits(1000, 10);

        ScanResponse response = service.submitScan(aScanRequest(null, false, null));

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.REJECTED);
            softly.assertThat(response.reason()).isEqualTo("PAYLOAD_TOO_LARGE");
            softly.assertThat(response.sourceEnabled()).isFalse();
        });
        verifyNoInteractions(scanSourceRepository, reconciliationService);
    }

    @Test
    void tooManyParsedGamesAreRejectedWithTheirReasonAndAreNotReconciled() {
        ScanService service = serviceWithLimits(1, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, false, null);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO, ZELDA));

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.REJECTED);
            softly.assertThat(response.reason()).isEqualTo("TOO_MANY_GAMES");
        });
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void appliedScanRecordsExactlyOneAttemptAndCarriesNoReason() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = spy(anEnabledSource());
        ScanRequest request = aScanRequest(null, false, null);
        ArgumentCaptor<Instant> snapshotCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), snapshotCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        Instant before = Instant.now();

        ScanResponse response = service.submitScan(request);

        Instant after = Instant.now();
        ArgumentCaptor<Instant> attemptCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(source, times(1)).recordAttempt(eq(SyncOutcome.APPLIED), attemptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(response.reason()).isNull();
            softly.assertThat(response.counts().added()).isEqualTo(1);
            softly.assertThat(attemptCaptor.getValue()).isEqualTo(snapshotCaptor.getValue());
            softly.assertThat(attemptCaptor.getValue()).isBetween(before, after);
        });
        ArgumentCaptor<SyncReport> reportCaptor = ArgumentCaptor.forClass(SyncReport.class);
        verify(syncReportRepository, times(1)).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getOutcome()).isEqualTo(SyncOutcome.APPLIED);
        assertThat(source.getLastOutcome()).isEqualTo(SyncOutcome.APPLIED);
    }

    @Test
    void appliedScanReturnsWithoutLookupsAnnouncesTheChangeAndPurges() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, false, null);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        ArgumentCaptor<Instant> snapshotCaptor = ArgumentCaptor.forClass(Instant.class);
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), snapshotCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        Instant before = Instant.now();

        service.submitScan(request);

        Instant after = Instant.now();
        assertThat(snapshotCaptor.getValue()).isBetween(before, after);
        verify(eventPublisher).publishEvent(new CatalogChanged("scan"));
        verify(reconciliationService).purgeUninstalledGames();
    }

    @Test
    void noChangeScanAnnouncesNothingAndDoesNotPurge() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, false, null);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        ArgumentCaptor<Instant> snapshotCaptor = ArgumentCaptor.forClass(Instant.class);
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), snapshotCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        Instant before = Instant.now();
        service.submitScan(request);

        ScanResponse duplicate = service.submitScan(request);

        Instant after = Instant.now();
        assertThat(snapshotCaptor.getValue()).isBetween(before, after);
        assertThat(duplicate.outcome()).isEqualTo(SyncOutcome.NO_CHANGE);
        verify(eventPublisher, times(1)).publishEvent(new CatalogChanged("scan"));
        verify(reconciliationService, times(1)).purgeUninstalledGames();
    }

    @Test
    void identicalScanContentReturnsNoChangeEvenWhenCapturedAtDiffers() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest first = aScanRequest(null, false, null);
        ScanRequest second = aScanRequest(null, false, null, CAPTURED_AT.plusSeconds(3600));
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(first)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        ScanResponse firstResponse = service.submitScan(first);
        ScanResponse secondResponse = service.submitScan(second);

        assertSoftly(softly -> {
            softly.assertThat(firstResponse.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(secondResponse.outcome()).isEqualTo(SyncOutcome.NO_CHANGE);
            softly.assertThat(secondResponse.counts().submitted()).isZero();
        });
        verify(reconciliationService, times(1))
                .applySnapshot(eq(source), eq(List.of(MARIO)), receivedAtCaptor.capture());
        verify(emuDeckParser, never()).parse(second);
    }

    @Test
    void checkReportsScanNeededWhenNoDigestIsStored() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        stubSource(SourceType.EMUDECK, Optional.of(source));

        ScanCheckResponse response = service.submitCheck(aCheckRequest(null));

        assertSoftly(softly -> {
            softly.assertThat(response.scanNeeded()).isTrue();
            softly.assertThat(response.sourceEnabled()).isTrue();
            softly.assertThat(source.getLastCheckedAt()).isNotNull();
        });
    }

    @Test
    void checkReportsNoScanWhenDigestVersionAndOutcomeAllMatch() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        source.recordClientDigest(DIGEST, ScanService.CURRENT_INGEST_VERSION);
        source.recordAttempt(SyncOutcome.APPLIED, CAPTURED_AT);
        stubSource(SourceType.EMUDECK, Optional.of(source));

        ScanCheckResponse response = service.submitCheck(aCheckRequest(DIGEST));

        assertThat(response.scanNeeded()).isFalse();
    }

    @Test
    void checkReportsScanNeededWhenStoredIngestVersionIsOlder() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        source.recordClientDigest(DIGEST, 0);
        source.recordAttempt(SyncOutcome.APPLIED, CAPTURED_AT);
        stubSource(SourceType.EMUDECK, Optional.of(source));

        ScanCheckResponse response = service.submitCheck(aCheckRequest(DIGEST));

        assertThat(response.scanNeeded()).isTrue();
    }

    @Test
    void checkReportsScanNeededWhenLastOutcomeWasNotSuccessful() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        source.recordClientDigest(DIGEST, ScanService.CURRENT_INGEST_VERSION);
        source.recordAttempt(SyncOutcome.SCAN_FAILED, CAPTURED_AT);
        stubSource(SourceType.EMUDECK, Optional.of(source));

        ScanCheckResponse response = service.submitCheck(aCheckRequest(DIGEST));

        assertThat(response.scanNeeded()).isTrue();
    }

    @Test
    void checkReportsNoScanForADisabledSource() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        source.setEnabled(false);
        stubSource(SourceType.EMUDECK, Optional.of(source));

        ScanCheckResponse response = service.submitCheck(aCheckRequest(null));

        assertSoftly(softly -> {
            softly.assertThat(response.scanNeeded()).isFalse();
            softly.assertThat(response.sourceEnabled()).isFalse();
        });
    }

    @Test
    void emptyResultingSetIsSuspiciousUnlessForced() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, false, null);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of());

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.REJECTED);
            softly.assertThat(response.reason()).isEqualTo("SNAPSHOT_SHRINK_SUSPECT");
        });
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void largeShrinkIsSuspiciousUnlessForced() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, false, null);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        when(gameInstallationRepository.countInstalledBySourceId(source.getId())).thenReturn(100L);

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.REJECTED);
            softly.assertThat(response.reason()).isEqualTo("SNAPSHOT_SHRINK_SUSPECT");
        });
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void forcedScanBypassesTheShrinkGuard() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(null, true, null);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(0, 1, 0));

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
    }

    @Test
    void clientGamesTakePrecedenceOverTheParser() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        List<ClientGame> games = List.of(new ClientGame("snes/Super Mario World.sfc", "Super Mario World", "SNES"));
        ScanRequest request = aScanRequest(null, false, games);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(CLIENT_MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
        verify(emuDeckParser, never()).parse(request);
    }

    @Test
    void clientGameReferencesAndTitlesAreNfcNormalized() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        String nfdRef = "snes/Cafe\u0301.sfc";
        String nfdTitle = "Cafe\u0301";
        List<ClientGame> games = List.of(new ClientGame(nfdRef, nfdTitle, "SNES"));
        ScanRequest request = aScanRequest(null, false, games);
        GamePayload expected = new GamePayload("snes/Caf\u00e9.sfc", "Caf\u00e9", "SNES", null);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(expected)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        ScanResponse response = service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(response.outcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
    }

    @Test
    void machineIdAdoptsAnExistingHostnameSource() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        ScanRequest request = aScanRequest(MACHINE_ID, false, null);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        when(scanSourceRepository.findByMachineIdAndSourceType(MACHINE_ID, SourceType.EMUDECK))
                .thenReturn(Optional.empty());
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(source.getMachineId()).isEqualTo(MACHINE_ID);
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
    }

    @Test
    void digestIsRecordedOnAppliedAndNotOnRejected() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource applied = anEnabledSource();
        ScanRequest appliedRequest = aScanRequest(null, false, null, DIGEST);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(applied));
        when(emuDeckParser.parse(appliedRequest)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(applied), eq(List.of(MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        service.submitScan(appliedRequest);

        assertSoftly(softly -> {
            softly.assertThat(applied.getLastClientDigest()).isEqualTo(DIGEST);
            softly.assertThat(applied.getIngestVersion()).isEqualTo(ScanService.CURRENT_INGEST_VERSION);
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
    }

    @Test
    void preCutoverScanClearsTheStoredDigest() {
        ScanService service = serviceWithLimits(100, 1_000_000);
        ScanSource source = anEnabledSource();
        source.recordClientDigest(DIGEST, ScanService.CURRENT_INGEST_VERSION);
        ScanRequest request = aScanRequest(null, false, null);
        ArgumentCaptor<Instant> receivedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        stubSource(SourceType.EMUDECK, Optional.of(source));
        when(emuDeckParser.parse(request)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), receivedAtCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));

        service.submitScan(request);

        assertSoftly(softly -> {
            softly.assertThat(source.getLastClientDigest()).isNull();
            softly.assertThat(receivedAtCaptor.getValue()).isNotNull();
        });
    }

    private ScanService serviceWithLimits(int maxGamesPerSource, int maxPayloadBytes) {
        GameCatalogProperties properties = new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(8, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(maxGamesPerSource, maxPayloadBytes, 262_144, 0.5), null);

        return new ScanService(scanSourceRepository, hostRepository, syncReportRepository, gameInstallationRepository, reconciliationService,
                artworkService, steamLibrarySyncService, eventPublisher, properties,
                new ObjectMapper().findAndRegisterModules(), Map.of("EMUDECK", emuDeckParser), scanLock);
    }

    private static ScanRequest aScanRequest(String machineId, boolean force, List<ClientGame> games) {
        return aScanRequest(machineId, force, games, (String) null);
    }

    private static ScanRequest aScanRequest(String machineId, boolean force, List<ClientGame> games, String clientDigest) {
        return new ScanRequest(machineId, HOSTNAME, SourceType.EMUDECK, CAPTURED_AT, clientDigest, force,
                List.of(MARIO_ENTRY), Map.of(), games);
    }

    private static ScanRequest aScanRequest(String machineId, boolean force, List<ClientGame> games, Instant capturedAt) {
        return new ScanRequest(machineId, HOSTNAME, SourceType.EMUDECK, capturedAt, null, force,
                List.of(MARIO_ENTRY), Map.of(), games);
    }

    private static ScanCheckRequest aCheckRequest(String clientDigest) {
        return new ScanCheckRequest(null, HOSTNAME, SourceType.EMUDECK, clientDigest);
    }

    private void stubSource(SourceType sourceType, Optional<ScanSource> source) {
        when(hostRepository.findByHostnameIgnoreCase(HOSTNAME)).thenReturn(Optional.of(HOST));
        when(scanSourceRepository.findByHostIdAndSourceType(HOST.getId(), sourceType)).thenReturn(source);
    }

    private ScanSource anEnabledSource() {
        return ScanSource.builder()
                .id(UUID.fromString("4f675aad-fa21-4b3b-9555-1b698b4e0c0a"))
                .host(HOST)
                .sourceType(SourceType.EMUDECK)
                .enabled(true)
                .build();
    }
}
