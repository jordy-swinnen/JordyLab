package dev.jordy.jordylab.settings.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class AiFeatureModelSettingTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("5c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f");
    public static final String DEFAULT_FEATURE_KEY = "gamecatalog.enrichment";
    public static final String DEFAULT_MODEL_ID = "deepseek/deepseek-v4.1-flash";
    public static final String DEFAULT_UPDATED_BY = "keycloak-subject-admin";

    public static AiFeatureModelSetting aDefaultAiFeatureModelSetting() {
        return anAiFeatureModelSetting().build();
    }

    public static AiFeatureModelSetting.AiFeatureModelSettingBuilder anAiFeatureModelSetting() {
        return AiFeatureModelSetting.builder()
                .id(DEFAULT_ID)
                .featureKey(DEFAULT_FEATURE_KEY)
                .modelId(DEFAULT_MODEL_ID)
                .updatedBySubject(DEFAULT_UPDATED_BY);
    }
}
