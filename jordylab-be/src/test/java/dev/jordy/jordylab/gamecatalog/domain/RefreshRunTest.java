package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshRunTest {

    private static final Instant FINISHED_AT = Instant.parse("2026-10-07T12:30:00Z");

    @Test
    void buildStartsRunningWithNoProgress() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(run.getId()).isEqualTo(RefreshRunTestBuilder.DEFAULT_ID);
            softly.assertThat(run.getKind()).isEqualTo(RefreshRunKind.DATA);
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.RUNNING);
            softly.assertThat(run.isRunning()).isTrue();
            softly.assertThat(run.getTotal()).isEqualTo(RefreshRunTestBuilder.DEFAULT_TOTAL);
            softly.assertThat(run.getProcessed()).isZero();
            softly.assertThat(run.getFailed()).isZero();
            softly.assertThat(run.isStopRequested()).isFalse();
            softly.assertThat(run.getStartedBy()).isEqualTo(RefreshRunTestBuilder.DEFAULT_STARTED_BY);
            softly.assertThat(run.getStartedAt()).isEqualTo(RefreshRunTestBuilder.DEFAULT_STARTED_AT);
            softly.assertThat(run.getFinishedAt()).isNull();
        });
    }

    @Test
    void buildWithoutKind() {
        assertThatThrownBy(() -> RefreshRunTestBuilder.aRefreshRun().kind(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNegativeTotal() {
        assertThatThrownBy(() -> RefreshRunTestBuilder.aRefreshRun().total(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutStartedBy() {
        assertThatThrownBy(() -> RefreshRunTestBuilder.aRefreshRun().startedBy(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutStartedAt() {
        assertThatThrownBy(() -> RefreshRunTestBuilder.aRefreshRun().startedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advanceCountsProcessedAndFailedGames() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();

        run.advance(true);
        run.advance(false);
        run.advance(true);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(run.getProcessed()).isEqualTo(3);
            softly.assertThat(run.getFailed()).isEqualTo(1);
        });
    }

    @Test
    void requestStopMarksARunningRun() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();

        run.requestStop();

        assertThat(run.isStopRequested()).isTrue();
    }

    @Test
    void requestStopFailsOnAFinishedRun() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();
        run.finish(RefreshRunStatus.SUCCEEDED, null, FINISHED_AT);

        assertThatThrownBy(run::requestStop).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishRecordsStatusSummaryAndTime() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();

        run.finish(RefreshRunStatus.FAILED, "5 failed: INSUFFICIENT_CREDITS", FINISHED_AT);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.FAILED);
            softly.assertThat(run.isRunning()).isFalse();
            softly.assertThat(run.getFailureSummary()).isEqualTo("5 failed: INSUFFICIENT_CREDITS");
            softly.assertThat(run.getFinishedAt()).isEqualTo(FINISHED_AT);
        });
    }

    @Test
    void finishCannotLeaveTheRunRunning() {
        RefreshRun run = RefreshRunTestBuilder.aDefaultRefreshRun();

        assertThatThrownBy(() -> run.finish(RefreshRunStatus.RUNNING, null, FINISHED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(RefreshRun.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
