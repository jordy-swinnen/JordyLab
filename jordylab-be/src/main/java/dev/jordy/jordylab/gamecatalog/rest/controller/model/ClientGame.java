package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A game entry produced client-side by the scan client (normalization and
 * multi-file grouping happen on the host). When a scan carries {@code games},
 * they are the parsed game set; the server validates and sanitizes them exactly
 * like server-parsed payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientGame(
        String externalRef,
        String title,
        String platform) {
}
