package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.BrandFamily;
import dev.jordy.jordylab.gamecatalog.domain.ChipColors;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.PlatformEntry;

/** A platform with the colours its chip uses everywhere. The frontend hard-codes none (spec 013 FR-040). */
public record PlatformChip(String name, BrandFamily family, String background, String foreground, String border) {

    public static PlatformChip of(String platformName) {
        PlatformEntry entry = PlatformCatalog.entryFor(platformName);
        ChipColors colors = entry.colors();

        return new PlatformChip(entry.name(), entry.family(), colors.background(), colors.foreground(), colors.border());
    }
}
