package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** Binds the real {@code application.yaml}: feature keys contain dots ({@code fna.briefing}) and must survive. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AiConfiguration.class, initializers = ConfigDataApplicationContextInitializer.class)
class AiPropertiesTest {

    @Autowired
    private AiProperties properties;

    @Test
    void everyFeatureHasADefaultGatewayModel() {
        assertSoftly(softly -> {
            softly.assertThat(properties.defaultModel(AiFeature.FNA_BRIEFING)).isEqualTo("anthropic/claude-sonnet-5");
            softly.assertThat(properties.defaultModel(AiFeature.GAMECATALOG_ENRICHMENT))
                    .isEqualTo("anthropic/claude-haiku-4.5");
            softly.assertThat(properties.defaultModel(AiFeature.GAMECATALOG_CHAT_QUERY))
                    .isEqualTo("anthropic/claude-haiku-4.5");
            softly.assertThat(properties.defaultModel(AiFeature.GAMECATALOG_CHAT_ANSWER))
                    .isEqualTo("anthropic/claude-haiku-4.5");
        });
    }

    @Test
    void bindsTheGatewayFallbackAndTimeouts() {
        assertSoftly(softly -> {
            softly.assertThat(properties.gateway().baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
            softly.assertThat(properties.fallback().model()).isEqualTo("claude-sonnet-5");
            softly.assertThat(properties.callTimeoutSeconds()).isEqualTo(120);
            softly.assertThat(properties.healthCheckTtlSeconds()).isEqualTo(30);
        });
    }
}
