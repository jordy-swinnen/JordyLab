package dev.jordy.jordylab;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRun;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureLastRunRepository;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureModelSettingRepository;
import dev.jordy.jordylab.settings.rest.client.OpenRouterModelCatalogClient;
import dev.jordy.jordylab.settings.service.AiModelSettingsService;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * 006 SC-003: a model saved on the AI Models page is used by the very next AI call, without a restart — through the
 * real resolver, repository, domain event and transaction wiring. The chat models are mocked; nothing leaves the
 * machine.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "OPENROUTER_API_KEY=test-gateway-key"
})
class AiModelSelectionIntegrationTest {

    private static final String CHOSEN_MODEL = "deepseek/deepseek-v4.1-flash";

    @MockitoBean
    private OpenAiChatModel gatewayChatModel;

    @MockitoBean
    private AnthropicChatModel fallbackChatModel;

    @MockitoBean
    private OpenRouterModelCatalogClient catalogClient;

    @Autowired
    private ResilientAiService aiService;

    @Autowired
    private AiModelSettingsService settingsService;

    @Autowired
    private AiFeatureModelSettingRepository settingRepository;

    @Autowired
    private AiFeatureLastRunRepository lastRunRepository;

    @TestConfiguration
    static class JwtDecoderOverride {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "test-user")
                    .claim("realm_access", Map.of("roles", List.of())).issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60)).build();
        }
    }

    @AfterEach
    void cleanUp() {
        settingRepository.deleteAll();
        lastRunRepository.deleteAll();
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void theNextCallUsesTheModelJustSavedAndItsRunIsRecorded() {
        when(gatewayChatModel.call(argThat((Prompt prompt) -> true))).thenReturn(answer("ok"));
        when(catalogClient.catalog()).thenReturn(new OpenRouterModelCatalogClient.Catalog(Instant.now(), true,
                List.of(new OpenRouterModelCatalogClient.CatalogModel(CHOSEN_MODEL, CHOSEN_MODEL, "deepseek", null,
                        null, 128000, false))));
        AiCallResult before = aiService.call(AiFeature.GAMECATALOG_ENRICHMENT, "system", "user");

        settingsService.saveModel(AiFeature.GAMECATALOG_ENRICHMENT.key(), CHOSEN_MODEL, "keycloak-subject-admin");
        AiCallResult after = aiService.call(AiFeature.GAMECATALOG_ENRICHMENT, "system", "user");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(gatewayChatModel, times(2)).call(promptCaptor.capture());
        AiFeatureLastRun lastRun = lastRunRepository.findByFeatureKey(AiFeature.GAMECATALOG_ENRICHMENT.key())
                .orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(before.model()).isEqualTo("anthropic/claude-haiku-4.5");
            softly.assertThat(after.model()).isEqualTo(CHOSEN_MODEL);
            softly.assertThat(promptCaptor.getAllValues().get(1).getOptions().getModel()).isEqualTo(CHOSEN_MODEL);
            softly.assertThat(lastRun.getModel()).isEqualTo(CHOSEN_MODEL);
            softly.assertThat(lastRun.getProvider()).isEqualTo("openrouter");
        });
    }

    @Test
    void changingAnExistingChoiceAlsoAppliesImmediately() {
        when(gatewayChatModel.call(argThat((Prompt prompt) -> true))).thenReturn(answer("ok"));
        when(catalogClient.catalog()).thenReturn(new OpenRouterModelCatalogClient.Catalog(Instant.now(), true,
                List.of(new OpenRouterModelCatalogClient.CatalogModel(CHOSEN_MODEL, CHOSEN_MODEL, "deepseek", null,
                        null, 128000, false),
                        new OpenRouterModelCatalogClient.CatalogModel("openai/gpt-6-luna-pro", "Luna", "openai", null,
                                null, 128000, false))));
        settingsService.saveModel(AiFeature.FNA_BRIEFING.key(), CHOSEN_MODEL, "admin");
        aiService.call(AiFeature.FNA_BRIEFING, "system", "user");

        settingsService.saveModel(AiFeature.FNA_BRIEFING.key(), "openai/gpt-6-luna-pro", "admin");

        assertThat(aiService.call(AiFeature.FNA_BRIEFING, "system", "user").model())
                .isEqualTo("openai/gpt-6-luna-pro");
    }
}
