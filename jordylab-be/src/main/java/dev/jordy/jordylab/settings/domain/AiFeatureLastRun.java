package dev.jordy.jordylab.settings.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
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
 * The most recent AI call per feature (006 FR-016): which provider and model actually answered, whether the fallback
 * was needed, and how it ended. One row per feature, overwritten by every call.
 */
@Entity
@Table(schema = "settings", name = "ai_feature_last_run")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFeatureLastRun extends BaseEntity<AiFeatureLastRun> {

    @Id
    private UUID id;

    @Column(name = "feature_key")
    private String featureKey;

    @Column(name = "provider")
    private String provider;

    @Column(name = "model")
    private String model;

    @Column(name = "fallback_used")
    private boolean fallbackUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private AiRunOutcome outcome;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "ran_at")
    private Instant ranAt;

    public void recordRun(String newProvider, String newModel, boolean usedFallback, AiRunOutcome newOutcome,
            String newFailureReason, Instant newRanAt) {
        Preconditions.checkArgument(StringUtils.hasText(newProvider), "provider is required");
        Preconditions.checkArgument(StringUtils.hasText(newModel), "model is required");
        Preconditions.checkArgument(newOutcome != null, "outcome is required");
        Preconditions.checkArgument(newRanAt != null, "ranAt is required");
        this.provider = newProvider;
        this.model = newModel;
        this.fallbackUsed = usedFallback;
        this.outcome = newOutcome;
        this.failureReason = newOutcome == AiRunOutcome.FAILURE ? newFailureReason : null;
        this.ranAt = newRanAt;
    }

    public static class AiFeatureLastRunBuilder {
        public AiFeatureLastRun build() {
            Preconditions.checkArgument(StringUtils.hasText(featureKey), "featureKey is required");
            Preconditions.checkArgument(StringUtils.hasText(provider), "provider is required");
            Preconditions.checkArgument(StringUtils.hasText(model), "model is required");
            Preconditions.checkArgument(outcome != null, "outcome is required");
            Preconditions.checkArgument(ranAt != null, "ranAt is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new AiFeatureLastRun(id, featureKey, provider, model, fallbackUsed, outcome,
                    outcome == AiRunOutcome.FAILURE ? failureReason : null, ranAt);
        }
    }
}
