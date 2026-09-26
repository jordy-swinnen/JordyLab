package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanSourceTest {

    @Test
    void buildScanSource() {
        ScanSource source = ScanSourceTestBuilder.aDefaultScanSource();

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(source.getId()).isNotNull();
            softly.assertThat(source.getSourceKey()).isEqualTo("jordybox:STEAM");
            softly.assertThat(source.getHostname()).isEqualTo(ScanSourceTestBuilder.DEFAULT_HOSTNAME);
            softly.assertThat(source.getSourceType()).isEqualTo(ScanSourceTestBuilder.DEFAULT_SOURCE_TYPE);
            softly.assertThat(source.getPlatform()).isEqualTo("Steam");
            softly.assertThat(source.isEnabled()).isTrue();
        });
    }

    @Test
    void buildWithoutHostname() {
        assertThatThrownBy(() -> ScanSourceTestBuilder.aScanSource().hostname(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankHostname() {
        assertThatThrownBy(() -> ScanSourceTestBuilder.aScanSource().hostname(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutSourceType() {
        assertThatThrownBy(() -> ScanSourceTestBuilder.aScanSource().sourceType(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildDerivesSourceKeyAndPlatform() {
        ScanSource source = ScanSourceTestBuilder.aScanSource()
                .hostname("media-pc")
                .sourceType(SourceType.EMUDECK)
                .build();

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(source.getSourceKey()).isEqualTo("media-pc:EMUDECK");
            softly.assertThat(source.getPlatform()).isEqualTo("EmuDeck");
        });
    }

    @Test
    void buildRespectsExplicitSourceKeyAndPlatform() {
        ScanSource source = ScanSourceTestBuilder.aScanSource()
                .sourceKey("custom-key")
                .platform("Custom")
                .build();

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(source.getSourceKey()).isEqualTo("custom-key");
            softly.assertThat(source.getPlatform()).isEqualTo("Custom");
        });
    }

    @Test
    void recordAttemptMarksSuccessOnlyForAppliedOrNoChange() {
        ScanSource source = ScanSourceTestBuilder.aDefaultScanSource();

        source.recordAttempt(SyncOutcome.APPLIED, ScanSourceTestBuilder.DEFAULT_SYNC_TIME);
        source.recordAttempt(SyncOutcome.SCAN_FAILED, ScanSourceTestBuilder.DEFAULT_SYNC_TIME.plusSeconds(60));

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(source.getLastSuccessAt()).isEqualTo(ScanSourceTestBuilder.DEFAULT_SYNC_TIME);
            softly.assertThat(source.getLastAttemptAt())
                    .isEqualTo(ScanSourceTestBuilder.DEFAULT_SYNC_TIME.plusSeconds(60));
            softly.assertThat(source.getLastOutcome()).isEqualTo(SyncOutcome.SCAN_FAILED);
        });
    }

    @Test
    void recordAppliedStoresPayloadHash() {
        ScanSource source = ScanSourceTestBuilder.aDefaultScanSource();

        source.recordApplied("abc123");

        assertThat(source.getLastPayloadHash()).isEqualTo("abc123");
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(ScanSource.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
