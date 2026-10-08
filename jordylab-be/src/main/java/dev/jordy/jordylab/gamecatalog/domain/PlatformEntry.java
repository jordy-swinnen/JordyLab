package dev.jordy.jordylab.gamecatalog.domain;

import java.util.Set;

/**
 * One platform in the {@link PlatformCatalog}: canonical name, spellings that mean the same platform, brand family,
 * generation (0 when not a console), and the identifiers other services need. {@code igdbPlatformId} and
 * {@code igdbName} were verified against live IGDB on 2026-10-07 (research B4); both are null for Steam and for custom
 * platforms. {@code libretroRepository} is the libretro-thumbnails repository name, null when unknown.
 */
public record PlatformEntry(String name, Set<String> aliases, BrandFamily family, int generation, boolean handheld,
        Long igdbPlatformId, String igdbName, String libretroRepository) {

    public ChipColors colors() {
        return family.chipColors();
    }

    /** A real console offered by the console autocomplete: fifth generation or newer (spec 013 FR-033). */
    public boolean isKnownConsole() {
        return generation >= PlatformCatalog.FIRST_OFFERED_GENERATION && igdbPlatformId != null;
    }
}
