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

import java.time.Instant;
import java.util.UUID;

/**
 * One owned or family library sync run. Records the outcome, the content hash used for the
 * no-change short-circuit, the entry counts, and how many Steam store / AI calls the run
 * caused, so the cost of a sync is visible (FR-007).
 */
@Entity
@Table(schema = "gamecatalog", name = "library_sync_run")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LibrarySyncRun extends BaseEntity<LibrarySyncRun> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private LibrarySource librarySource;

    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    private LibrarySyncOutcome outcome;

    private String contentHash;

    private int entriesSubmitted;

    private int entriesAdded;

    private int entriesRemoved;

    private int metadataCalls;

    private int aiCalls;

    private String errorCode;

    public static class LibrarySyncRunBuilder {
        public LibrarySyncRun build() {
            Preconditions.checkArgument(librarySource != null, "librarySource is required");
            Preconditions.checkArgument(startedAt != null, "startedAt is required");
            Preconditions.checkArgument(finishedAt != null, "finishedAt is required");
            Preconditions.checkArgument(outcome != null, "outcome is required");
            Preconditions.checkArgument(entriesSubmitted >= 0 && entriesAdded >= 0 && entriesRemoved >= 0
                            && metadataCalls >= 0 && aiCalls >= 0,
                    "counts must not be negative");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new LibrarySyncRun(id, librarySource, startedAt, finishedAt, outcome, contentHash,
                    entriesSubmitted, entriesAdded, entriesRemoved, metadataCalls, aiCalls, errorCode);
        }
    }
}
