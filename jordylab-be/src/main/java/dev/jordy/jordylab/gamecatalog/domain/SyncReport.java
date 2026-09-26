package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "gamecatalog", name = "sync_report")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncReport extends BaseEntity<SyncReport> {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id")
    private ScanSource source;

    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    private SyncOutcome outcome;

    private String payloadHash;

    private int gamesSubmitted;

    private int gamesAdded;

    private int gamesUpdated;

    private int gamesRemoved;

    private int gamesRejected;

    public static class SyncReportBuilder {
        public SyncReport build() {
            Preconditions.checkArgument(source != null, "source is required");
            Preconditions.checkArgument(receivedAt != null, "receivedAt is required");
            Preconditions.checkArgument(outcome != null, "outcome is required");
            if (id == null) id = UUID.randomUUID();

            return new SyncReport(id, source, receivedAt, outcome, payloadHash, gamesSubmitted, gamesAdded,
                    gamesUpdated, gamesRemoved, gamesRejected);
        }
    }
}
