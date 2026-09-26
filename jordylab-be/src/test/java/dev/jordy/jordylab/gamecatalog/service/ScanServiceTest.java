package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncReport;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.scan.LibraryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    private static final String HOSTNAME = "jordybox";
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-02T10:20:00Z");
    private static final ScanRequest REQUEST = new ScanRequest(
            HOSTNAME,
            SourceType.EMUDECK,
            CAPTURED_AT,
            List.of(new ScanEntry("snes/Super Mario World.sfc", 524288L, CAPTURED_AT)),
            Map.of());
    private static final GamePayload MARIO = new GamePayload("snes/Super Mario World.sfc", "Super Mario World", "SNES", false);
    private static final GamePayload ZELDA = new GamePayload("snes/Zelda.sfc", "Zelda", "SNES", false);

    @Mock
    private ScanSourceRepository scanSourceRepository;

    @Mock
    private SyncReportRepository syncReportRepository;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private ArtworkService artworkService;

    @Mock
    private LibraryParser emuDeckParser;

    @Test
    void payloadOverTheByteCapIsRejectedWithItsReasonAndTouchesNothing() {
        ScanService service = serviceWithLimits(1000, 10);

        ScanResponse response = service.submitScan(REQUEST);

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
        when(scanSourceRepository.findByHostnameAndSourceType(HOSTNAME, SourceType.EMUDECK))
                .thenReturn(Optional.of(source));
        when(emuDeckParser.parse(REQUEST)).thenReturn(List.of(MARIO, ZELDA));

        ScanResponse response = service.submitScan(REQUEST);

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
        ArgumentCaptor<Instant> snapshotCaptor = ArgumentCaptor.forClass(Instant.class);
        when(scanSourceRepository.findByHostnameAndSourceType(HOSTNAME, SourceType.EMUDECK))
                .thenReturn(Optional.of(source));
        when(emuDeckParser.parse(REQUEST)).thenReturn(List.of(MARIO));
        when(reconciliationService.applySnapshot(eq(source), eq(List.of(MARIO)), snapshotCaptor.capture()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        Instant before = Instant.now();

        ScanResponse response = service.submitScan(REQUEST);

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

    private ScanService serviceWithLimits(int maxGamesPerSource, int maxPayloadBytes) {
        GameCatalogProperties properties = new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Scan(maxGamesPerSource, maxPayloadBytes, 262_144));

        return new ScanService(scanSourceRepository, syncReportRepository, reconciliationService, artworkService,
                properties, new ObjectMapper().findAndRegisterModules(), Map.of("EMUDECK", emuDeckParser));
    }

    private ScanSource anEnabledSource() {
        return ScanSource.builder()
                .id(UUID.fromString("4f675aad-fa21-4b3b-9555-1b698b4e0c0a"))
                .hostname(HOSTNAME)
                .sourceType(SourceType.EMUDECK)
                .enabled(true)
                .build();
    }
}
