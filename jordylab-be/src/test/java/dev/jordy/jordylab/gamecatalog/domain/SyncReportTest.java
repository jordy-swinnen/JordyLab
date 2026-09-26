package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncReportTest {

    @Test
    void buildSyncReport() {
        SyncReport report = SyncReportTestBuilder.aDefaultSyncReport();

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(report.getId()).isNotNull();
            softly.assertThat(report.getOutcome()).isEqualTo(SyncOutcome.APPLIED);
            softly.assertThat(report.getReceivedAt()).isEqualTo(SyncReportTestBuilder.DEFAULT_RECEIVED_AT);
        });
    }

    @Test
    void buildWithoutSource() {
        assertThatThrownBy(() -> SyncReportTestBuilder.aSyncReport().source(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutReceivedAt() {
        assertThatThrownBy(() -> SyncReportTestBuilder.aSyncReport().receivedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutOutcome() {
        assertThatThrownBy(() -> SyncReportTestBuilder.aSyncReport().outcome(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(SyncReport.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
