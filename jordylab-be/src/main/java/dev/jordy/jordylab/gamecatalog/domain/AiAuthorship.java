package dev.jordy.jordylab.gamecatalog.domain;

import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Who wrote an AI description (spec 013 FR-059): {@code answeredModel} is the model the provider reported as answering
 * (for a router, the model it picked; null when the provider did not report one), {@code requestedModel} is the model
 * that was selected, kept only when it differs (a router, or the provider fallback), and {@code writtenAt} the time.
 */
public record AiAuthorship(String answeredModel, String requestedModel, Instant writtenAt) {

    public static AiAuthorship of(String answeredModel, String requestedModel, Instant writtenAt) {
        String answered = StringUtils.hasText(answeredModel) ? answeredModel : null;
        String requested = StringUtils.hasText(requestedModel) && !requestedModel.equals(answered) ? requestedModel : null;

        return new AiAuthorship(answered, requested, writtenAt);
    }
}
