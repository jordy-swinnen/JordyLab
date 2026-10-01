package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRun;
import dev.jordy.jordylab.settings.rest.client.ModelCatalogUnavailableException;
import dev.jordy.jordylab.settings.rest.client.OpenRouterModelCatalogClient;
import dev.jordy.jordylab.settings.rest.controller.model.AiModelsResponse;
import dev.jordy.jordylab.settings.rest.controller.model.ModelCatalogResponse;
import dev.jordy.jordylab.settings.rest.controller.model.SaveAiModelRequest;
import dev.jordy.jordylab.settings.service.AiModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Admin-only AI Models settings (006 US6, contracts/settings-ai-models-api.md). {@code /api/settings/**} is
 * {@code admin} in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/settings/ai-models")
@RequiredArgsConstructor
public class SettingsAiModelsController {

    private final AiModelSettingsService settingsService;
    private final OpenRouterModelCatalogClient catalogClient;

    @GetMapping
    public AiModelsResponse features() {
        return new AiModelsResponse(settingsService.listFeatures().stream()
                .map(view -> new AiModelsResponse.Feature(view.feature().key(), view.feature().displayName(),
                        view.feature().module(), view.feature().description(), view.currentModel(),
                        view.defaultModel(), view.fallbackModel(), view.modelAvailable(), lastRun(view.lastRun())))
                .toList());
    }

    @GetMapping("/catalog")
    public ModelCatalogResponse catalog(@RequestParam(required = false) String search,
            @RequestParam(required = false) String vendor) {
        OpenRouterModelCatalogClient.Catalog catalog = catalogClient.catalog();
        String needle = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        return new ModelCatalogResponse(catalog.fetchedAt(), catalog.fresh(), catalog.models().stream()
                .filter(model -> !StringUtils.hasText(vendor) || model.vendor().equalsIgnoreCase(vendor.trim()))
                .filter(model -> needle == null || model.id().toLowerCase(Locale.ROOT).contains(needle)
                        || model.name().toLowerCase(Locale.ROOT).contains(needle))
                .map(model -> new ModelCatalogResponse.Model(model.id(), model.name(), model.vendor(),
                        new ModelCatalogResponse.Pricing(model.inputPerMillion(), model.outputPerMillion()),
                        model.contextLength(), model.expiring()))
                .toList());
    }

    @PutMapping("/{featureKey}")
    public ResponseEntity<Void> save(@PathVariable String featureKey, @RequestBody SaveAiModelRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        settingsService.saveModel(featureKey, request.modelId(), jwt.getSubject());

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AiModelSettingsService.UnknownAiFeatureException.class)
    public ResponseEntity<ErrorBody> handleUnknownFeature() {
        return ResponseEntity.badRequest().body(new ErrorBody("UNKNOWN_FEATURE"));
    }

    @ExceptionHandler(AiModelSettingsService.BlankModelException.class)
    public ResponseEntity<ErrorBody> handleBlankModel() {
        return ResponseEntity.badRequest().body(new ErrorBody("BLANK_MODEL"));
    }

    @ExceptionHandler(AiModelSettingsService.ModelUnavailableException.class)
    public ResponseEntity<ErrorBody> handleModelUnavailable() {
        return ResponseEntity.badRequest().body(new ErrorBody("MODEL_UNAVAILABLE"));
    }

    @ExceptionHandler(ModelCatalogUnavailableException.class)
    public ResponseEntity<ErrorBody> handleCatalogUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody("GATEWAY_CATALOG_UNAVAILABLE"));
    }

    private static AiModelsResponse.LastRun lastRun(AiFeatureLastRun run) {
        if (run == null) {
            return null;
        }

        return new AiModelsResponse.LastRun(run.getProvider(), run.getModel(), run.isFallbackUsed(),
                run.getOutcome().name(), run.getFailureReason(), run.getRanAt());
    }

    private record ErrorBody(String reason) {
    }
}
