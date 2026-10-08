package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.BrandFamily;

/** A well-known console offered by the add-console autocomplete (fifth generation onward). */
public record KnownConsoleResponse(String name, BrandFamily family, int generation, boolean handheld) {
}
