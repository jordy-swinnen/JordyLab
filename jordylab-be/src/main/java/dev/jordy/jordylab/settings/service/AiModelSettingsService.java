package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRun;
import dev.jordy.jordylab.settings.domain.AiFeatureModelSetting;
import dev.jordy.jordylab.settings.domain.AiFeatureModelSettingUpdated;
import dev.jordy.jordylab.settings.domain.AiRunOutcome;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureLastRunRepository;
import dev.jordy.jordylab.settings.domain.repository.AiFeatureModelSettingRepository;
import dev.jordy.jordylab.settings.rest.client.OpenRouterModelCatalogClient;
import dev.jordy.jordylab.shared.ai.AiCallCompleted;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.AiModelResolver;
import dev.jordy.jordylab.shared.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The AI Models settings (006 US6): which model each {@link AiFeature} uses and how its last call went.
 *
 * <ul>
 *     <li>As the {@code @Primary} {@link AiModelResolver}: the saved model when the admin picked one, else the code
 *     default — cached, and dropped from the cache when a setting changes, so a new choice applies from the next call
 *     without a restart (FR-014).</li>
 *     <li>Records every {@link AiCallCompleted} as the feature's last run (FR-016), in its own transaction so a caller's
 *     rollback can't lose it and a failure here can't break the AI call.</li>
 * </ul>
 */
@Primary
@Service
@Slf4j
@RequiredArgsConstructor
public class AiModelSettingsService implements AiModelResolver {

    private final AiFeatureModelSettingRepository settingRepository;
    private final AiFeatureLastRunRepository lastRunRepository;
    private final OpenRouterModelCatalogClient catalogClient;
    private final AiProperties aiProperties;
    private final PlatformTransactionManager transactionManager;

    private final Map<AiFeature, String> resolvedModels = new ConcurrentHashMap<>();

    @Override
    public String resolveModel(AiFeature feature) {
        return resolvedModels.computeIfAbsent(feature, key -> settingRepository.findByFeatureKey(key.key())
                .map(AiFeatureModelSetting::getModelId)
                .orElseGet(() -> aiProperties.defaultModel(key)));
    }

    @Transactional(readOnly = true)
    public List<FeatureModelView> listFeatures() {
        Optional<OpenRouterModelCatalogClient.Catalog> catalog = currentCatalog();

        return Arrays.stream(AiFeature.values())
                .map(feature -> {
                    Optional<AiFeatureModelSetting> saved = settingRepository.findByFeatureKey(feature.key());
                    String current = saved.map(AiFeatureModelSetting::getModelId)
                            .orElseGet(() -> aiProperties.defaultModel(feature));
                    // Only a saved choice can go missing from the gateway; an unknown catalog says nothing.
                    boolean available = saved.isEmpty() || catalog.map(c -> c.contains(current)).orElse(true);

                    return new FeatureModelView(feature, current, aiProperties.defaultModel(feature),
                            aiProperties.fallback().model(), available,
                            lastRunRepository.findByFeatureKey(feature.key()).orElse(null));
                })
                .toList();
    }

    @Transactional
    public void saveModel(String featureKey, String modelId, String updatedBy) {
        AiFeature feature = AiFeature.fromKey(featureKey).orElseThrow(UnknownAiFeatureException::new);
        if (!StringUtils.hasText(modelId)) {
            throw new BlankModelException();
        }
        String trimmed = modelId.trim();
        OpenRouterModelCatalogClient.Catalog catalog = catalogClient.catalog();
        // A stale catalog can't prove a model is gone, so the choice is accepted (spec edge case).
        if (catalog.fresh() && !catalog.contains(trimmed)) {
            throw new ModelUnavailableException();
        }

        Optional<AiFeatureModelSetting> existing = settingRepository.findByFeatureKey(feature.key());
        if (existing.isPresent()) {
            existing.get().updateModel(trimmed, updatedBy);
            settingRepository.save(existing.get());
        } else {
            settingRepository.save(AiFeatureModelSetting.builder()
                    .featureKey(feature.key()).modelId(trimmed).updatedBySubject(updatedBy).build());
            resolvedModels.remove(feature);
        }
    }

    @TransactionalEventListener
    void onSettingUpdated(AiFeatureModelSettingUpdated event) {
        AiFeature.fromKey(event.featureKey()).ifPresent(resolvedModels::remove);
    }

    @EventListener
    public void onAiCallCompleted(AiCallCompleted event) {
        // Own transaction, opened here so a commit failure is caught too: recording must never break the AI call.
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            ownTransaction.executeWithoutResult(status -> recordLastRun(event));
        } catch (RuntimeException exception) {
            log.warn("Could not record the last AI run for {}: {}", event.feature().key(), exception.getMessage());
        }
    }

    private void recordLastRun(AiCallCompleted event) {
        AiRunOutcome outcome = event.success() ? AiRunOutcome.SUCCESS : AiRunOutcome.FAILURE;
        String reason = event.failureReason() == null ? null : event.failureReason().name();
        Optional<AiFeatureLastRun> existing = lastRunRepository.findByFeatureKey(event.feature().key());
        if (existing.isPresent()) {
            existing.get().recordRun(event.provider(), event.model(), event.fallbackUsed(), outcome, reason,
                    event.completedAt());
        } else {
            lastRunRepository.save(AiFeatureLastRun.builder()
                    .featureKey(event.feature().key()).provider(event.provider()).model(event.model())
                    .fallbackUsed(event.fallbackUsed()).outcome(outcome).failureReason(reason)
                    .ranAt(event.completedAt()).build());
        }
    }

    private Optional<OpenRouterModelCatalogClient.Catalog> currentCatalog() {
        try {
            return Optional.of(catalogClient.catalog());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public record FeatureModelView(AiFeature feature, String currentModel, String defaultModel, String fallbackModel,
            boolean modelAvailable, AiFeatureLastRun lastRun) {
    }

    public static class UnknownAiFeatureException extends RuntimeException {
    }

    public static class BlankModelException extends RuntimeException {
    }

    public static class ModelUnavailableException extends RuntimeException {
    }
}
