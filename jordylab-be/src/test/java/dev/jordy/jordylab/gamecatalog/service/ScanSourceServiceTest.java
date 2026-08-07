package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanSourceServiceTest {

    private static final Instant ATTEMPT_AT = Instant.parse("2026-08-02T10:20:00Z");

    @Mock
    private ScanSourceRepository scanSourceRepository;

    @Mock
    private GameRepository gameRepository;

    private ScanSourceService scanSourceService;

    @BeforeEach
    void setUp() {
        scanSourceService = new ScanSourceService(scanSourceRepository, gameRepository);
    }

    @Test
    void listsSourcesWithInstalledGameCounts() {
        ScanSource source = aSource("snes");
        source.recordAttempt(SyncOutcome.APPLIED, ATTEMPT_AT);
        when(scanSourceRepository.findAll()).thenReturn(List.of(source));
        when(gameRepository.countInstalledBySourceId(source.getId())).thenReturn(412L);

        SourcesResponse response = scanSourceService.listSources();

        assertSoftly(softly -> {
            softly.assertThat(response.sources()).hasSize(1);
            ScanSourceResponse summary = response.sources().getFirst();
            softly.assertThat(summary.id()).isEqualTo(source.getId());
            softly.assertThat(summary.sourceKey()).isEqualTo("snes");
            softly.assertThat(summary.hostname()).isEqualTo("jordybox");
            softly.assertThat(summary.sourceType()).isEqualTo(SourceType.EMUDECK);
            softly.assertThat(summary.platform()).isEqualTo("SNES");
            softly.assertThat(summary.enabled()).isTrue();
            softly.assertThat(summary.lastAttemptAt()).isEqualTo(ATTEMPT_AT);
            softly.assertThat(summary.lastSuccessAt()).isEqualTo(ATTEMPT_AT);
            softly.assertThat(summary.lastOutcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(summary.installedGameCount()).isEqualTo(412L);
        });
    }

    @Test
    void togglesSourceEnabledState() {
        ScanSource source = aSource("snes");
        when(scanSourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(scanSourceRepository.save(source)).thenReturn(source);

        Optional<SourceEnabledResponse> response = scanSourceService.setEnabled(source.getId(), false);

        assertSoftly(softly -> {
            softly.assertThat(response).isPresent();
            softly.assertThat(response.get().id()).isEqualTo(source.getId());
            softly.assertThat(response.get().enabled()).isFalse();
            softly.assertThat(source.isEnabled()).isFalse();
        });
        verify(scanSourceRepository).save(source);
    }

    @Test
    void toggleUnknownSourceIsEmpty() {
        UUID unknownId = UUID.fromString("cccccccc-dddd-4eee-8fff-111111111111");
        when(scanSourceRepository.findById(unknownId)).thenReturn(Optional.empty());

        Optional<SourceEnabledResponse> response = scanSourceService.setEnabled(unknownId, false);

        assertThat(response).isEmpty();
    }

    private ScanSource aSource(String sourceKey) {
        return ScanSource.builder()
                .sourceKey(sourceKey)
                .hostname("jordybox")
                .sourceType(SourceType.EMUDECK)
                .platform("SNES")
                .enabled(true)
                .build();
    }
}
