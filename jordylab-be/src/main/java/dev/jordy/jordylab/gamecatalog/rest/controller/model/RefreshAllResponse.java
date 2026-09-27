package dev.jordy.jordylab.gamecatalog.rest.controller.model;

public record RefreshAllResponse(RefreshCountResponse metadata, RefreshCountResponse enrichment) {
}
