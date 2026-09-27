package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;

/** Family sync request. The access token is used for this sync only and never stored. */
public record FamilySyncRequest(@NotBlank String accessToken, Boolean force) {
}
