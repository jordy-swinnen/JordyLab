package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single place that knows platform names, their aliases, brand colours and external identifiers (spec 013 FR-033,
 * FR-040, research B4). It replaces the maps that used to live in the EmuDeck parser, the artwork client, the IGDB
 * client and the frontend. Unknown names (a custom console) resolve to a neutral entry, so they always render.
 *
 * <p>IGDB ids and names were verified against live IGDB on 2026-10-07; {@code IgdbPlatformCatalogCheck} re-checks them
 * on demand. Steam is not an IGDB platform, so it has none.
 */
@UtilityClass
public class PlatformCatalog {

    /** Console autocomplete starts at the fifth generation (PlayStation, Nintendo 64, Saturn). */
    public static final int FIRST_OFFERED_GENERATION = 5;
    public static final String STEAM = "Steam";

    private static final List<PlatformEntry> ENTRIES = List.of(
            entry("Steam", Set.of(), BrandFamily.STEAM, 0, false, null, null, null),

            entry("PlayStation", Set.of("PS1", "PSX", "PSOne", "Sony PlayStation"), BrandFamily.PLAYSTATION, 5, false,
                    7L, "PlayStation", "Sony - PlayStation"),
            entry("PlayStation 2", Set.of("PS2"), BrandFamily.PLAYSTATION, 6, false, 8L, "PlayStation 2",
                    "Sony - PlayStation 2"),
            entry("PlayStation 3", Set.of("PS3"), BrandFamily.PLAYSTATION, 7, false, 9L, "PlayStation 3", null),
            entry("PlayStation 4", Set.of("PS4"), BrandFamily.PLAYSTATION, 8, false, 48L, "PlayStation 4", null),
            entry("PlayStation 5", Set.of("PS5"), BrandFamily.PLAYSTATION, 9, false, 167L, "PlayStation 5", null),
            entry("PSP", Set.of("PlayStation Portable"), BrandFamily.PLAYSTATION, 7, true, 38L,
                    "PlayStation Portable", "Sony - PlayStation Portable"),
            entry("PlayStation Vita", Set.of("PS Vita", "Vita", "PSVita"), BrandFamily.PLAYSTATION, 8, true, 46L,
                    "PlayStation Vita", null),

            entry("Xbox", Set.of("Original Xbox", "Microsoft Xbox"), BrandFamily.XBOX, 6, false, 11L, "Xbox", null),
            entry("Xbox 360", Set.of("X360"), BrandFamily.XBOX, 7, false, 12L, "Xbox 360", null),
            entry("Xbox One", Set.of("XboxOne", "XB1"), BrandFamily.XBOX, 8, false, 49L, "Xbox One", null),
            entry("Xbox Series X|S", Set.of("Xbox Series X", "Xbox Series S", "Xbox Series", "Series X"),
                    BrandFamily.XBOX, 9, false, 169L, "Xbox Series X|S", null),

            entry("Nintendo 64", Set.of("N64"), BrandFamily.NINTENDO, 5, false, 4L, "Nintendo 64",
                    "Nintendo - Nintendo 64"),
            entry("GameCube", Set.of("Nintendo GameCube", "GC", "NGC"), BrandFamily.NINTENDO, 6, false, 21L,
                    "Nintendo GameCube", "Nintendo - GameCube"),
            entry("Wii", Set.of("Nintendo Wii"), BrandFamily.NINTENDO, 7, false, 5L, "Wii", "Nintendo - Wii"),
            entry("Wii U", Set.of("WiiU", "Nintendo Wii U"), BrandFamily.NINTENDO, 8, false, 41L, "Wii U", null),
            entry("Nintendo Switch", Set.of("Switch", "NSW"), BrandFamily.NINTENDO, 8, false, 130L, "Nintendo Switch",
                    null),
            entry("Nintendo Switch 2", Set.of("Switch 2", "Switch2"), BrandFamily.NINTENDO, 9, false, 508L,
                    "Nintendo Switch 2", null),
            entry("Nintendo DS", Set.of("DS", "NDS"), BrandFamily.NINTENDO, 7, true, 20L, "Nintendo DS",
                    "Nintendo - Nintendo DS"),
            entry("Nintendo 3DS", Set.of("3DS", "N3DS"), BrandFamily.NINTENDO, 8, true, 37L, "Nintendo 3DS", null),
            entry("Game Boy Advance", Set.of("GBA"), BrandFamily.NINTENDO, 6, true, 24L, "Game Boy Advance",
                    "Nintendo - Game Boy Advance"),
            entry("Game Boy Color", Set.of("GBC"), BrandFamily.NINTENDO, 5, true, 22L, "Game Boy Color",
                    "Nintendo - Game Boy Color"),
            entry("Game Boy", Set.of("GB"), BrandFamily.NINTENDO, 4, true, 33L, "Game Boy", "Nintendo - Game Boy"),
            entry("SNES", Set.of("Super Nintendo", "Super Famicom", "SFC", "SNES MSU-1",
                    "Super Nintendo Entertainment System"), BrandFamily.NINTENDO, 4, false, 19L,
                    "Super Nintendo Entertainment System", "Nintendo - Super Nintendo Entertainment System"),
            entry("NES", Set.of("Famicom", "Nintendo Entertainment System"), BrandFamily.NINTENDO, 3, false, 18L,
                    "Nintendo Entertainment System", "Nintendo - Nintendo Entertainment System"),

            entry("Saturn", Set.of("Sega Saturn"), BrandFamily.SEGA, 5, false, 32L, "Sega Saturn", "Sega - Saturn"),
            entry("Dreamcast", Set.of("Sega Dreamcast", "DC"), BrandFamily.SEGA, 6, false, 23L, "Dreamcast",
                    "Sega - Dreamcast"),
            entry("Genesis", Set.of("Mega Drive", "Megadrive", "Sega Genesis", "Sega Mega Drive"), BrandFamily.SEGA, 4,
                    false, 29L, "Sega Mega Drive/Genesis", "Sega - Mega Drive - Genesis"),
            entry("Game Gear", Set.of("Sega Game Gear", "GG"), BrandFamily.SEGA, 4, true, 35L, "Sega Game Gear",
                    "Sega - Game Gear"),
            entry("Sega 32X", Set.of("32X"), BrandFamily.SEGA, 4, false, 30L, "Sega 32X", "Sega - 32X"),
            entry("Sega CD", Set.of("Mega-CD", "Mega CD"), BrandFamily.SEGA, 4, false, 78L, "Sega CD",
                    "Sega - Mega-CD - Sega CD"),
            entry("Master System", Set.of("Sega Master System", "SMS"), BrandFamily.SEGA, 3, false, 64L,
                    "Sega Master System/Mark III", "Sega - Master System - Mark III"),

            entry("Atari 2600", Set.of("Atari2600", "VCS"), BrandFamily.OTHER, 2, false, 59L, "Atari 2600",
                    "Atari - 2600"));

    private static final Map<String, PlatformEntry> BY_KEY = indexByKey();

    public static List<PlatformEntry> all() {
        return ENTRIES;
    }

    /** Consoles offered by the add-console autocomplete: generation 5 up to the current one, ordered by generation then name. */
    public static List<PlatformEntry> knownConsoles() {
        return ENTRIES.stream()
                .filter(PlatformEntry::isKnownConsole)
                .sorted(Comparator.comparingInt(PlatformEntry::generation).thenComparing(PlatformEntry::name))
                .toList();
    }

    public static Optional<PlatformEntry> find(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }

        return Optional.ofNullable(BY_KEY.get(keyOf(name)));
    }

    /** The catalog entry for a name, or a neutral "other" entry carrying the trimmed name for a platform we do not know. */
    public static PlatformEntry entryFor(String name) {
        return find(name).orElseGet(() -> custom(name));
    }

    /** The canonical spelling of a platform name; unknown names are returned trimmed and unchanged. */
    public static String canonical(String name) {
        if (name == null) {
            return null;
        }

        return find(name).map(PlatformEntry::name).orElseGet(name::trim);
    }

    private static PlatformEntry custom(String name) {
        String trimmed = name == null ? "" : name.trim();

        return new PlatformEntry(trimmed, Set.of(), BrandFamily.OTHER, 0, false, null, null, null);
    }

    private static PlatformEntry entry(String name, Set<String> aliases, BrandFamily family, int generation,
            boolean handheld, Long igdbPlatformId, String igdbName, String libretroRepository) {
        return new PlatformEntry(name, aliases, family, generation, handheld, igdbPlatformId, igdbName,
                libretroRepository);
    }

    private static Map<String, PlatformEntry> indexByKey() {
        Map<String, PlatformEntry> index = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();
        for (PlatformEntry platformEntry : ENTRIES) {
            List<String> spellings = new ArrayList<>(platformEntry.aliases());
            spellings.add(platformEntry.name());
            if (platformEntry.igdbName() != null) {
                spellings.add(platformEntry.igdbName());
            }
            for (String spelling : spellings) {
                PlatformEntry previous = index.putIfAbsent(keyOf(spelling), platformEntry);
                if (previous != null && previous != platformEntry) {
                    collisions.add(spelling + " -> " + previous.name() + " / " + platformEntry.name());
                }
            }
        }
        if (!collisions.isEmpty()) {
            throw new IllegalStateException("Platform catalog spellings collide: " + collisions);
        }

        return Map.copyOf(index);
    }

    private static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
