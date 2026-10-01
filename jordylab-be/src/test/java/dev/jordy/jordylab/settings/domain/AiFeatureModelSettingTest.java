package dev.jordy.jordylab.settings.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class AiFeatureModelSettingTest {

    @Test
    void buildAiFeatureModelSetting() {
        AiFeatureModelSetting setting = AiFeatureModelSettingTestBuilder.aDefaultAiFeatureModelSetting();

        assertSoftly(softly -> {
            softly.assertThat(setting.getId()).isEqualTo(AiFeatureModelSettingTestBuilder.DEFAULT_ID);
            softly.assertThat(setting.getFeatureKey()).isEqualTo(AiFeatureModelSettingTestBuilder.DEFAULT_FEATURE_KEY);
            softly.assertThat(setting.getModelId()).isEqualTo(AiFeatureModelSettingTestBuilder.DEFAULT_MODEL_ID);
            softly.assertThat(setting.getUpdatedBySubject())
                    .isEqualTo(AiFeatureModelSettingTestBuilder.DEFAULT_UPDATED_BY);
        });
    }

    @Test
    void buildWithoutFeatureKey() {
        assertThatThrownBy(() -> AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting().featureKey(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutModel() {
        assertThatThrownBy(() -> AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting().modelId(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateModelSwitchesTheModelAndAnnouncesIt() {
        AiFeatureModelSetting setting = AiFeatureModelSettingTestBuilder.aDefaultAiFeatureModelSetting();

        setting.updateModel("openai/gpt-6-luna-pro", "keycloak-subject-other-admin");

        Collection<Object> events = ReflectionTestUtils.invokeMethod(setting, "domainEvents");
        assertSoftly(softly -> {
            softly.assertThat(setting.getModelId()).isEqualTo("openai/gpt-6-luna-pro");
            softly.assertThat(setting.getUpdatedBySubject()).isEqualTo("keycloak-subject-other-admin");
            softly.assertThat(events).containsExactly(new AiFeatureModelSettingUpdated(
                    AiFeatureModelSettingTestBuilder.DEFAULT_FEATURE_KEY, "openai/gpt-6-luna-pro"));
        });
    }

    @Test
    void updateModelRejectsABlankModel() {
        AiFeatureModelSetting setting = AiFeatureModelSettingTestBuilder.aDefaultAiFeatureModelSetting();

        assertThatThrownBy(() -> setting.updateModel(" ", "keycloak-subject-admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(setting.getModelId()).isEqualTo(AiFeatureModelSettingTestBuilder.DEFAULT_MODEL_ID);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(AiFeatureModelSetting.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
