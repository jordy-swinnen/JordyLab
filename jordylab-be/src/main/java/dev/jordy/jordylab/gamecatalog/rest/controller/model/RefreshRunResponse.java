package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.RefreshRun;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunStatus;

import java.time.Instant;
import java.util.UUID;

public record RefreshRunResponse(UUID id, RefreshRunKind kind, RefreshRunStatus status, int total, int processed, int failed,
        boolean stopRequested, String failureSummary, Instant startedAt, Instant finishedAt) {

    public static RefreshRunResponse of(RefreshRun run) {
        return new RefreshRunResponse(run.getId(), run.getKind(), run.getStatus(), run.getTotal(), run.getProcessed(),
                run.getFailed(), run.isStopRequested(), run.getFailureSummary(), run.getStartedAt(), run.getFinishedAt());
    }
}
