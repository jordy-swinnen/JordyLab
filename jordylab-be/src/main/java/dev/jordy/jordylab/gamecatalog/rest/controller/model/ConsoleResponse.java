package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.BrandFamily;

import java.util.UUID;

/** A registered console; {@code chip} carries the platform's colours so the page paints it like everywhere else. */
public record ConsoleResponse(UUID id, String platform, BrandFamily family, PlatformChip chip, String name, String label,
        long gameCount) {
}
