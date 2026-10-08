package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * One admin-started bulk refresh (spec 013 US11). Durable so progress survives leaving the page and a restart marks a
 * running run {@code INTERRUPTED}; a partial unique index allows only one {@code RUNNING} run per kind.
 */
@Entity
@Table(schema = "gamecatalog", name = "refresh_run")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshRun extends BaseEntity<RefreshRun> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private RefreshRunKind kind;

    @Enumerated(EnumType.STRING)
    private RefreshRunStatus status;

    private int total;

    private int processed;

    private int failed;

    private boolean stopRequested;

    private String failureSummary;

    private String startedBy;

    private Instant startedAt;

    private Instant finishedAt;

    public boolean isRunning() {
        return status == RefreshRunStatus.RUNNING;
    }

    /** Counts one game as done; a failed game is also counted as processed so the progress bar always reaches the total. */
    public void advance(boolean succeeded) {
        this.processed++;
        if (!succeeded) {
            this.failed++;
        }
    }

    public void requestStop() {
        Preconditions.checkState(isRunning(), "only a running refresh can be stopped");
        this.stopRequested = true;
    }

    public void finish(RefreshRunStatus finalStatus, String summary, Instant finishedAt) {
        Preconditions.checkArgument(finalStatus != RefreshRunStatus.RUNNING, "a finished run cannot stay RUNNING");
        this.status = finalStatus;
        this.failureSummary = summary;
        this.finishedAt = finishedAt;
    }

    public static class RefreshRunBuilder {
        public RefreshRun build() {
            Preconditions.checkArgument(kind != null, "kind is required");
            Preconditions.checkArgument(total >= 0, "total must not be negative");
            Preconditions.checkArgument(StringUtils.hasText(startedBy), "startedBy is required");
            Preconditions.checkArgument(startedAt != null, "startedAt is required");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (status == null) {
                status = RefreshRunStatus.RUNNING;
            }

            return new RefreshRun(id, kind, status, total, processed, failed, stopRequested, failureSummary, startedBy,
                    startedAt, finishedAt);
        }
    }
}
