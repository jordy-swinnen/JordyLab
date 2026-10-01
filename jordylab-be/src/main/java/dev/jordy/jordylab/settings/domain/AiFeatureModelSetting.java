package dev.jordy.jordylab.settings.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * The admin's model choice for one AI feature (006 US6, FR-014). No row = the feature uses its code default; there is
 * no delete — reverting means saving the default model id.
 */
@Entity
@Table(schema = "settings", name = "ai_feature_model_setting")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFeatureModelSetting extends BaseEntity<AiFeatureModelSetting> {

    @Id
    private UUID id;

    @Column(name = "feature_key")
    private String featureKey;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "updated_by_subject")
    private String updatedBySubject;

    /** Switches the feature to another model; the next AI call uses it without a restart. */
    public void updateModel(String newModelId, String updatedBy) {
        Preconditions.checkArgument(StringUtils.hasText(newModelId), "modelId is required");
        Preconditions.checkArgument(StringUtils.hasText(updatedBy), "updatedBy is required");
        this.modelId = newModelId;
        this.updatedBySubject = updatedBy;
        registerEvent(new AiFeatureModelSettingUpdated(featureKey, newModelId));
    }

    /** Announces a first-time choice, so the resolver cache is dropped after the commit like for any change. */
    public void markCreated() {
        registerEvent(new AiFeatureModelSettingUpdated(featureKey, modelId));
    }

    public static class AiFeatureModelSettingBuilder {
        public AiFeatureModelSetting build() {
            Preconditions.checkArgument(StringUtils.hasText(featureKey), "featureKey is required");
            Preconditions.checkArgument(StringUtils.hasText(modelId), "modelId is required");
            Preconditions.checkArgument(StringUtils.hasText(updatedBySubject), "updatedBySubject is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new AiFeatureModelSetting(id, featureKey, modelId, updatedBySubject);
        }
    }
}
