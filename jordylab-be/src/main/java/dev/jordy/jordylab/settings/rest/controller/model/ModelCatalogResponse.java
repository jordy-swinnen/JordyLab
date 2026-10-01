package dev.jordy.jordylab.settings.rest.controller.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** {@code GET /api/settings/ai-models/catalog}: the gateway's chat models, trimmed and cached. */
public record ModelCatalogResponse(Instant fetchedAt, boolean fresh, List<Model> models) {

    public record Model(String id, String name, String vendor, Pricing pricingPerMillionTokens, Integer contextLength,
            boolean expiring) {
    }

    /** USD per million tokens; null for router-priced models. */
    public record Pricing(BigDecimal input, BigDecimal output) {
    }
}
