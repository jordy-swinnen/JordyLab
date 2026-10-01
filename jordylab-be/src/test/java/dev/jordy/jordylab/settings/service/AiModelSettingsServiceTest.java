package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRun;
import dev.jordy.jordylab.settings.domain.AiFeatureLastRunTestBuilder;
import dev.jordy.jordylab.settings.domain.AiFeatureModelSetting;
import dev.jordy.jordylab.settings.domain.AiFeatureModelSettingTestBuilder;
import dev.jordy.jordylab.settings.domain.AiFeatureModelSettingUpdated;
import dev.jordy.jordylab.settings.domain.AiRunOutcome;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureLastRunRepository;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureModelSettingRepository;
import dev.jordy.jordylab.settings.rest.client.ModelCatalogUnavailableException;
import dev.jordy.jordylab.settings.rest.client.OpenRouterModelCatalogClient;
import dev.jordy.jordylab.shared.ai.AiCallCompleted;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.AiProperties;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelSettingsServiceTest {

    private static final AiFeature FEATURE = AiFeature.GAMECATALOG_ENRICHMENT;
    private static final String DEFAULT_MODEL = "anthropic/claude-haiku-4.5";
    private static final String CHOSEN_MODEL = "deepseek/deepseek-v4.1-flash";
    private static final String ADMIN = "keycloak-subject-admin";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private AiFeatureModelSettingRepository settingRepository;

    @Mock
    private AiFeatureLastRunRepository lastRunRepository;

    @Mock
    private OpenRouterModelCatalogClient catalogClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AiModelSettingsService service;

    @BeforeEach
    void setUp() {
        Map<String, AiProperties.Feature> features = Arrays.stream(AiFeature.values())
                .collect(Collectors.toMap(AiFeature::key, feature -> new AiProperties.Feature(DEFAULT_MODEL)));
        AiProperties properties = new AiProperties(30, 120, new AiProperties.Gateway("https://gateway.test", "k"),
                new AiProperties.Fallback("anthropic", "claude-sonnet-5"), features);
        service = new AiModelSettingsService(settingRepository, lastRunRepository, catalogClient, properties,
                transactionManager);
    }

    private static OpenRouterModelCatalogClient.Catalog catalog(boolean fresh, String... ids) {
        return new OpenRouterModelCatalogClient.Catalog(NOW, fresh, Arrays.stream(ids)
                .map(id -> new OpenRouterModelCatalogClient.CatalogModel(id, id, id.split("/")[0], null, null, null,
                        false))
                .toList());
    }

    /** listFeatures reads every feature: only {@code FEATURE} has a saved model, none has run yet. */
    private void givenOnlyFeatureHasASavedModel(String modelId) {
        for (AiFeature feature : AiFeature.values()) {
            when(settingRepository.findByFeatureKey(feature.key())).thenReturn(feature == FEATURE
                    ? Optional.of(AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting().modelId(modelId).build())
                    : Optional.empty());
            when(lastRunRepository.findByFeatureKey(feature.key())).thenReturn(Optional.empty());
        }
    }

    @Test
    void usesTheCodeDefaultUntilAModelIsSaved() {
        when(settingRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.empty());

        assertThat(service.resolveModel(FEATURE)).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void usesTheSavedModelAndCachesIt() {
        when(settingRepository.findByFeatureKey(FEATURE.key()))
                .thenReturn(Optional.of(AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting()
                        .modelId(CHOSEN_MODEL).build()));

        service.resolveModel(FEATURE);
        String model = service.resolveModel(FEATURE);

        assertThat(model).isEqualTo(CHOSEN_MODEL);
        verify(settingRepository, times(1)).findByFeatureKey(FEATURE.key());
    }

    @Test
    void aChangedSettingAppliesToTheNextCallWithoutARestart() {
        when(settingRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.empty(),
                Optional.of(AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting().modelId(CHOSEN_MODEL).build()));
        String before = service.resolveModel(FEATURE);

        service.onSettingUpdated(new AiFeatureModelSettingUpdated(FEATURE.key(), CHOSEN_MODEL));

        assertSoftly(softly -> {
            softly.assertThat(before).isEqualTo(DEFAULT_MODEL);
            softly.assertThat(service.resolveModel(FEATURE)).isEqualTo(CHOSEN_MODEL);
        });
    }

    @Test
    void savesAFirstChoice() {
        when(catalogClient.catalog()).thenReturn(catalog(true, CHOSEN_MODEL));
        when(settingRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.empty());
        ArgumentCaptor<AiFeatureModelSetting> savedCaptor = ArgumentCaptor.forClass(AiFeatureModelSetting.class);

        service.saveModel(FEATURE.key(), " " + CHOSEN_MODEL + " ", ADMIN);

        verify(settingRepository).save(savedCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(savedCaptor.getValue().getFeatureKey()).isEqualTo(FEATURE.key());
            softly.assertThat(savedCaptor.getValue().getModelId()).isEqualTo(CHOSEN_MODEL);
            softly.assertThat(savedCaptor.getValue().getUpdatedBySubject()).isEqualTo(ADMIN);
        });
    }

    @Test
    void changesAnExistingChoice() {
        AiFeatureModelSetting existing = AiFeatureModelSettingTestBuilder.anAiFeatureModelSetting()
                .modelId(DEFAULT_MODEL).build();
        when(catalogClient.catalog()).thenReturn(catalog(true, CHOSEN_MODEL));
        when(settingRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.of(existing));

        service.saveModel(FEATURE.key(), CHOSEN_MODEL, ADMIN);

        assertThat(existing.getModelId()).isEqualTo(CHOSEN_MODEL);
        verify(settingRepository).save(existing);
    }

    @Test
    void rejectsAnUnknownFeatureABlankModelAndAModelTheFreshCatalogLacks() {
        assertThatThrownBy(() -> service.saveModel("no.such.feature", CHOSEN_MODEL, ADMIN))
                .isInstanceOf(AiModelSettingsService.UnknownAiFeatureException.class);
        assertThatThrownBy(() -> service.saveModel(FEATURE.key(), " ", ADMIN))
                .isInstanceOf(AiModelSettingsService.BlankModelException.class);
        when(catalogClient.catalog()).thenReturn(catalog(true, DEFAULT_MODEL));
        assertThatThrownBy(() -> service.saveModel(FEATURE.key(), CHOSEN_MODEL, ADMIN))
                .isInstanceOf(AiModelSettingsService.ModelUnavailableException.class);
        verify(settingRepository, never()).findByFeatureKey(FEATURE.key());
    }

    @Test
    void acceptsAnyModelWhileTheCatalogIsStale() {
        when(catalogClient.catalog()).thenReturn(catalog(false, DEFAULT_MODEL));
        when(settingRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.empty());

        service.saveModel(FEATURE.key(), CHOSEN_MODEL, ADMIN);

        verify(settingRepository).save(argThat(setting -> CHOSEN_MODEL.equals(setting.getModelId())));
    }

    @Test
    void flagsASavedModelTheGatewayNoLongerOffers() {
        when(catalogClient.catalog()).thenReturn(catalog(true, DEFAULT_MODEL));
        givenOnlyFeatureHasASavedModel("gone/model");

        List<AiModelSettingsService.FeatureModelView> views = service.listFeatures();

        AiModelSettingsService.FeatureModelView view = views.stream()
                .filter(candidate -> candidate.feature() == FEATURE).findFirst().orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(views).hasSize(AiFeature.values().length);
            softly.assertThat(view.currentModel()).isEqualTo("gone/model");
            softly.assertThat(view.defaultModel()).isEqualTo(DEFAULT_MODEL);
            softly.assertThat(view.fallbackModel()).isEqualTo("claude-sonnet-5");
            softly.assertThat(view.modelAvailable()).isFalse();
        });
    }

    @Test
    void anUnreachableCatalogDoesNotFlagSavedModels() {
        when(catalogClient.catalog()).thenThrow(new ModelCatalogUnavailableException(new IllegalStateException()));
        givenOnlyFeatureHasASavedModel("any/model");

        assertThat(service.listFeatures()).filteredOn(view -> view.feature() == FEATURE)
                .extracting(AiModelSettingsService.FeatureModelView::modelAvailable).containsExactly(true);
    }

    @Test
    void recordsTheFirstRunOfAFeature() {
        when(transactionManager.getTransaction(argThat(definition -> definition.getPropagationBehavior()
                == TransactionDefinition.PROPAGATION_REQUIRES_NEW))).thenReturn(new SimpleTransactionStatus());
        when(lastRunRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.empty());
        ArgumentCaptor<AiFeatureLastRun> runCaptor = ArgumentCaptor.forClass(AiFeatureLastRun.class);

        service.onAiCallCompleted(new AiCallCompleted(FEATURE, "anthropic", "claude-sonnet-5", false, true,
                ProviderFailureReason.TIMEOUT, NOW));

        verify(lastRunRepository).save(runCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(runCaptor.getValue().getProvider()).isEqualTo("anthropic");
            softly.assertThat(runCaptor.getValue().isFallbackUsed()).isTrue();
            softly.assertThat(runCaptor.getValue().getOutcome()).isEqualTo(AiRunOutcome.FAILURE);
            softly.assertThat(runCaptor.getValue().getFailureReason()).isEqualTo("TIMEOUT");
            softly.assertThat(runCaptor.getValue().getRanAt()).isEqualTo(NOW);
        });
    }

    @Test
    void overwritesTheLastRun() {
        AiFeatureLastRun existing = AiFeatureLastRunTestBuilder.aDefaultAiFeatureLastRun();
        when(transactionManager.getTransaction(argThat(definition -> definition.getPropagationBehavior()
                == TransactionDefinition.PROPAGATION_REQUIRES_NEW))).thenReturn(new SimpleTransactionStatus());
        when(lastRunRepository.findByFeatureKey(FEATURE.key())).thenReturn(Optional.of(existing));

        service.onAiCallCompleted(new AiCallCompleted(FEATURE, "openrouter", CHOSEN_MODEL, true, false, null, NOW));

        assertSoftly(softly -> {
            softly.assertThat(existing.getModel()).isEqualTo(CHOSEN_MODEL);
            softly.assertThat(existing.getOutcome()).isEqualTo(AiRunOutcome.SUCCESS);
            softly.assertThat(existing.getRanAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aRecordingFailureNeverReachesTheAiCaller() {
        when(transactionManager.getTransaction(argThat(definition -> definition.getPropagationBehavior()
                == TransactionDefinition.PROPAGATION_REQUIRES_NEW))).thenReturn(new SimpleTransactionStatus());
        when(lastRunRepository.findByFeatureKey(FEATURE.key())).thenThrow(new IllegalStateException("db down"));

        service.onAiCallCompleted(new AiCallCompleted(FEATURE, "openrouter", CHOSEN_MODEL, true, false, null, NOW));

        verify(lastRunRepository, never()).save(argThat(run -> true));
    }
}
