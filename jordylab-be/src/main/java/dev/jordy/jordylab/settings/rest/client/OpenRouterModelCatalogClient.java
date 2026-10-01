package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.shared.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The gateway's model list for the AI Models picker (006 US6, research §1.2): keyless {@code GET <gateway>/models},
 * trimmed to text-output chat models (no {@code ~} aliases), prices per million tokens, cached for
 * {@code jordylab.settings.model-catalog.cache-ttl-minutes}. When the gateway is down a stale cache is served with
 * {@code fresh=false}; with no cache at all it throws — never a silent empty list.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenRouterModelCatalogClient {

    private static final BigDecimal PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final AiProperties aiProperties;
    private final SettingsProperties settingsProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile Catalog cached;

    public Catalog catalog() {
        Catalog current = cached;
        Instant now = Instant.now(clock);
        Duration ttl = Duration.ofMinutes(settingsProperties.modelCatalog().cacheTtlMinutes());
        if (current != null && current.fetchedAt().plus(ttl).isAfter(now)) {
            return current;
        }
        try {
            Catalog fetched = new Catalog(now, true, fetch());
            cached = fetched;

            return fetched;
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Gateway model catalog unavailable: {}", exception.getMessage());
            if (current != null) {
                return new Catalog(current.fetchedAt(), false, current.models());
            }

            throw new ModelCatalogUnavailableException(exception);
        }
    }

    private List<CatalogModel> fetch() {
        String body = restClient.get()
                .uri(aiProperties.gateway().baseUrl() + "/models")
                .retrieve()
                .body(String.class);
        JsonNode data;
        try {
            data = objectMapper.readTree(body).path("data");
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable gateway model list", exception);
        }
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("Gateway model list is empty");
        }

        List<CatalogModel> models = new ArrayList<>();
        for (JsonNode model : data) {
            String id = model.path("id").asText("");
            if (!StringUtils.hasText(id) || id.startsWith("~") || model.hasNonNull("alias_target")
                    || !producesText(model)) {
                continue;
            }
            models.add(new CatalogModel(id, model.path("name").asText(id), vendorOf(id),
                    perMillion(model.path("pricing").path("prompt").asText(null)),
                    perMillion(model.path("pricing").path("completion").asText(null)),
                    model.path("context_length").isNumber() ? model.path("context_length").asInt() : null,
                    model.hasNonNull("expiration_date")));
        }
        models.sort(Comparator.comparing(CatalogModel::id));

        return List.copyOf(models);
    }

    private static boolean producesText(JsonNode model) {
        for (JsonNode modality : model.path("architecture").path("output_modalities")) {
            if ("text".equals(modality.asText())) {
                return true;
            }
        }

        return false;
    }

    static String vendorOf(String id) {
        int slash = id.indexOf('/');

        return slash > 0 ? id.substring(0, slash) : id;
    }

    /** Per-token USD string → per-million; {@code "-1"} (router-priced) and unparseable values → null. */
    static BigDecimal perMillion(String perToken) {
        if (!StringUtils.hasText(perToken) || perToken.startsWith("-")) {
            return null;
        }
        try {
            return new BigDecimal(perToken).multiply(PER_MILLION).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record Catalog(Instant fetchedAt, boolean fresh, List<CatalogModel> models) {

        public boolean contains(String modelId) {
            return models.stream().anyMatch(model -> model.id().equals(modelId));
        }
    }

    public record CatalogModel(String id, String name, String vendor, BigDecimal inputPerMillion,
            BigDecimal outputPerMillion, Integer contextLength, boolean expiring) {
    }
}
