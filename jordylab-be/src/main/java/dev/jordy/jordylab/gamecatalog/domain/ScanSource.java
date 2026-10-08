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
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "gamecatalog", name = "scan_source")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanSource extends BaseEntity<ScanSource> {

    @Id
    private UUID id;

    private String sourceKey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_id")
    private Host host;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String platform;

    @Setter
    private boolean enabled;

    private Instant lastAttemptAt;

    private Instant lastSuccessAt;

    @Enumerated(EnumType.STRING)
    private SyncOutcome lastOutcome;

    private String lastPayloadHash;

    private String machineId;

    private String lastClientDigest;

    private int ingestVersion;

    private Instant lastCheckedAt;

    /** The name every screen shows for the machine behind this source: the host's display name or its hostname. */
    public String hostLabel() {
        return host.label();
    }

    public void announce(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public void adoptMachine(String machineId) {
        this.machineId = machineId;
    }

    public void recordClientDigest(String clientDigest, int ingestVersion) {
        this.lastClientDigest = clientDigest;
        this.ingestVersion = ingestVersion;
    }

    public void clearClientDigest() {
        this.lastClientDigest = null;
    }

    public void recordCheck(Instant checkedAt) {
        this.lastCheckedAt = checkedAt;
    }

    public void recordAttempt(SyncOutcome outcome, Instant attemptedAt) {
        this.lastAttemptAt = attemptedAt;
        this.lastOutcome = outcome;
        if (outcome == SyncOutcome.APPLIED || outcome == SyncOutcome.NO_CHANGE) {
            this.lastSuccessAt = attemptedAt;
        }
    }

    public void recordApplied(String payloadHash) {
        this.lastPayloadHash = payloadHash;
    }

    public static class ScanSourceBuilder {
        public ScanSource build() {
            Preconditions.checkArgument(host != null, "host is required");
            Preconditions.checkArgument(sourceType != null, "sourceType is required");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (!StringUtils.hasText(sourceKey)) {
                sourceKey = host.getHostname() + ":" + sourceType.name();
            }
            if (!StringUtils.hasText(platform)) {
                platform = sourceType.platform();
            }

            return new ScanSource(id, sourceKey, host, sourceType, platform, enabled, lastAttemptAt, lastSuccessAt,
                    lastOutcome, lastPayloadHash, machineId, lastClientDigest, ingestVersion, lastCheckedAt);
        }
    }
}
