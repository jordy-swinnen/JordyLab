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

    private String hostname;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String platform;

    private boolean enabled;

    private Instant lastAttemptAt;

    private Instant lastSuccessAt;

    @Enumerated(EnumType.STRING)
    private SyncOutcome lastOutcome;

    private String lastPayloadHash;

    public void announce(String hostname, SourceType sourceType) {
        this.hostname = hostname;
        this.sourceType = sourceType;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
            Preconditions.checkArgument(StringUtils.hasText(hostname), "hostname is required");
            Preconditions.checkArgument(sourceType != null, "sourceType is required");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (!StringUtils.hasText(sourceKey)) {
                sourceKey = hostname + ":" + sourceType.name();
            }
            if (!StringUtils.hasText(platform)) {
                platform = sourceType.platform();
            }

            return new ScanSource(id, sourceKey, hostname, sourceType, platform, enabled, lastAttemptAt, lastSuccessAt,
                    lastOutcome, lastPayloadHash);
        }
    }
}
