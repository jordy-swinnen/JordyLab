package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRunTestBuilder;
import dev.jordy.jordylab.settings.rest.client.ModelCatalogUnavailableException;
import dev.jordy.jordylab.settings.rest.client.OpenRouterModelCatalogClient;
import dev.jordy.jordylab.settings.service.AiModelSettingsService;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsAiModelsController.class)
class SettingsAiModelsControllerTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiModelSettingsService settingsService;

    @MockitoBean
    private OpenRouterModelCatalogClient catalogClient;

    @Test
    void listsEveryFeatureWithItsModelsAndLastRun() throws Exception {
        when(settingsService.listFeatures()).thenReturn(List.of(new AiModelSettingsService.FeatureModelView(
                AiFeature.GAMECATALOG_ENRICHMENT, "deepseek/deepseek-v4.1-flash", "anthropic/claude-haiku-4.5",
                "claude-sonnet-5", false, AiFeatureLastRunTestBuilder.aDefaultAiFeatureLastRun())));

        mockMvc.perform(get("/api/settings/ai-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].key").value("gamecatalog.enrichment"))
                .andExpect(jsonPath("$.features[0].displayName").value("Game descriptions"))
                .andExpect(jsonPath("$.features[0].moduleName").value("gamecatalog"))
                .andExpect(jsonPath("$.features[0].currentModel").value("deepseek/deepseek-v4.1-flash"))
                .andExpect(jsonPath("$.features[0].defaultModel").value("anthropic/claude-haiku-4.5"))
                .andExpect(jsonPath("$.features[0].fallbackModel").value("claude-sonnet-5"))
                .andExpect(jsonPath("$.features[0].modelAvailable").value(false))
                .andExpect(jsonPath("$.features[0].lastRun.provider").value("openrouter"))
                .andExpect(jsonPath("$.features[0].lastRun.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.features[0].lastRun.ranAt").value("2026-10-01T12:00:00Z"));
    }

    @Test
    void filtersTheCatalogByVendorAndSearch() throws Exception {
        when(catalogClient.catalog()).thenReturn(new OpenRouterModelCatalogClient.Catalog(FETCHED_AT, false, List.of(
                new OpenRouterModelCatalogClient.CatalogModel("anthropic/claude-haiku-4.5",
                        "Anthropic: Claude Haiku 4.5", "anthropic", new BigDecimal("1"), new BigDecimal("5"), 200000,
                        false),
                new OpenRouterModelCatalogClient.CatalogModel("anthropic/claude-sonnet-5", "Anthropic: Claude Sonnet 5",
                        "anthropic", new BigDecimal("3"), new BigDecimal("15"), 200000, false),
                new OpenRouterModelCatalogClient.CatalogModel("deepseek/deepseek-v4.1-flash", "DeepSeek: V4.1 Flash",
                        "deepseek", null, null, 128000, true))));

        mockMvc.perform(get("/api/settings/ai-models/catalog").param("vendor", "anthropic").param("search", "HAIKU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fresh").value(false))
                .andExpect(jsonPath("$.fetchedAt").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$.models.length()").value(1))
                .andExpect(jsonPath("$.models[0].id").value("anthropic/claude-haiku-4.5"))
                .andExpect(jsonPath("$.models[0].pricingPerMillionTokens.input").value(1))
                .andExpect(jsonPath("$.models[0].pricingPerMillionTokens.output").value(5))
                .andExpect(jsonPath("$.models[0].contextLength").value(200000));
    }

    @Test
    void theCatalogIs503WhenNothingCouldEverBeFetched() throws Exception {
        when(catalogClient.catalog()).thenThrow(new ModelCatalogUnavailableException(new IllegalStateException()));

        mockMvc.perform(get("/api/settings/ai-models/catalog"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("GATEWAY_CATALOG_UNAVAILABLE"));
    }

    @Test
    void savesTheChoiceAsTheSignedInAdmin() throws Exception {
        mockMvc.perform(put("/api/settings/ai-models/{featureKey}", "gamecatalog.enrichment")
                        .with(jwt().jwt(token -> token.subject("keycloak-subject-admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelId": "deepseek/deepseek-v4.1-flash"}
                                """))
                .andExpect(status().isNoContent());

        verify(settingsService).saveModel("gamecatalog.enrichment", "deepseek/deepseek-v4.1-flash",
                "keycloak-subject-admin");
    }

    @Test
    void mapsEachRejectionToItsReason() throws Exception {
        doThrow(new AiModelSettingsService.UnknownAiFeatureException())
                .when(settingsService).saveModel("nope", "a/b", "admin");
        doThrow(new AiModelSettingsService.BlankModelException())
                .when(settingsService).saveModel("fna.briefing", " ", "admin");
        doThrow(new AiModelSettingsService.ModelUnavailableException())
                .when(settingsService).saveModel("fna.briefing", "gone/model", "admin");

        expectRejection("nope", "a/b", "UNKNOWN_FEATURE");
        expectRejection("fna.briefing", " ", "BLANK_MODEL");
        expectRejection("fna.briefing", "gone/model", "MODEL_UNAVAILABLE");
    }

    private void expectRejection(String featureKey, String modelId, String reason) throws Exception {
        mockMvc.perform(put("/api/settings/ai-models/{featureKey}", featureKey)
                        .with(jwt().jwt(token -> token.subject("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\": \"" + modelId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(reason));
    }
}
