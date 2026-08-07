package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GamePayload(
        String externalRef,
        String title,
        String platform,
        Boolean localArtworkAvailable) {

    public boolean artworkAvailable() {
        return Boolean.TRUE.equals(localArtworkAvailable);
    }
}
