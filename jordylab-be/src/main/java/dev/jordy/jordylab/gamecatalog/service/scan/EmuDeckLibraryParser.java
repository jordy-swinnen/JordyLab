package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses an EmuDeck scan. The script writes a single EmuDeck source covering
 * one or more emulator ROM subfolders (the user picks them at script runtime).
 *
 * <p>EmuDeck's default install places ROMs under {@code ~/Emulation/roms/<emulator>/}.
 * The parser treats the parent directory of each scanned file as the
 * emulator key, looks up its display name in
 * {@link #EMUDECK_PLATFORM_LOOKUPS}, and emits one {@link GamePayload} per
 * ROM file. Multi-disc games ({@code .m3u} lists) are not specially handled
 * here — the script lists them as regular files and the parser emits one entry
 * per listed file. We can revisit when the user hits the edge case in practice.
 */
@Slf4j
@Component("EMUDECK")
public class EmuDeckLibraryParser implements LibraryParser {

    private static final Set<String> ROM_EXTENSIONS = Set.of(
            ".smc", ".sfc", ".iso", ".bin", ".cue", ".m3u",
            ".nes", ".gb", ".gbc", ".gba", ".nds", ".3ds",
            ".n64", ".z64", ".v64",
            ".gcm", ".ciso",
            ".chd", ".pbp", ".ecm", ".mds");

    /**
     * EmuDeck's standard emulator subfolder names mapped to user-facing
     * platform labels. Any unmapped subfolder is reported with the raw
     * subfolder name (capitalised) so a user with a non-standard layout
     * still gets something readable in the catalog.
     */
    private static final Map<String, String> EMUDECK_PLATFORM_LOOKUPS = Map.ofEntries(
            Map.entry("snes", "SNES"),
            Map.entry("nes", "NES"),
            Map.entry("n64", "Nintendo 64"),
            Map.entry("gb", "Game Boy"),
            Map.entry("gbc", "Game Boy Color"),
            Map.entry("gba", "Game Boy Advance"),
            Map.entry("nds", "Nintendo DS"),
            Map.entry("3ds", "Nintendo 3DS"),
            Map.entry("gamecube", "GameCube"),
            Map.entry("gc", "GameCube"),
            Map.entry("wii", "Wii"),
            Map.entry("ps1", "PlayStation"),
            Map.entry("psx", "PlayStation"),
            Map.entry("psp", "PSP"),
            Map.entry("ps2", "PlayStation 2"),
            Map.entry("dreamcast", "Dreamcast"),
            Map.entry("dc", "Dreamcast"),
            Map.entry("saturn", "Saturn"),
            Map.entry("genesis", "Genesis"),
            Map.entry("megadrive", "Genesis"),
            Map.entry("sega32x", "Sega 32X"),
            Map.entry("gamegear", "Game Gear"),
            Map.entry("atari2600", "Atari 2600"),
            Map.entry("msu1", "SNES MSU-1"));

    @Override
    public SourceType supports() {
        return SourceType.EMUDECK;
    }

    @Override
    public List<GamePayload> parse(ScanRequest request) {
        List<ScanEntry> entries = entries(request);
        if (entries.isEmpty()) {
            log.info("EmuDeck scan from '{}' contained no paths", request.hostname());

            return List.of();
        }
        // Preserve order from the script's directory walk for deterministic results.
        Map<String, GamePayload> byRef = new LinkedHashMap<>();
        for (ScanEntry entry : entries) {
            String ext = extension(entry.relpath());
            if (ext == null || !ROM_EXTENSIONS.contains(ext)) {
                continue;
            }
            String emulator = parentDirectory(entry.relpath());
            if (!StringUtils.hasText(emulator)) {
                continue;
            }
            String platform = platformFor(emulator);
            String title = titleFromFilename(entry.relpath());
            String externalRef = entry.relpath();
            byRef.put(externalRef, new GamePayload(externalRef, TextSanitizer.sanitizeTitle(title), platform, null));
        }

        return new ArrayList<>(byRef.values());
    }

    private static String extension(String relpath) {
        int slash = relpath.lastIndexOf('/');
        String basename = slash < 0 ? relpath : relpath.substring(slash + 1);
        int dot = basename.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }

        return basename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String parentDirectory(String relpath) {
        int slash = relpath.lastIndexOf('/');
        if (slash < 0) {
            return null;
        }
        int prev = relpath.lastIndexOf('/', slash - 1);

        return prev < 0 ? relpath.substring(0, slash) : relpath.substring(prev + 1, slash);
    }

    private static String titleFromFilename(String relpath) {
        int slash = relpath.lastIndexOf('/');
        String basename = slash < 0 ? relpath : relpath.substring(slash + 1);
        int dot = basename.lastIndexOf('.');
        if (dot > 0) {
            basename = basename.substring(0, dot);
        }

        return basename
                .replace('_', ' ')
                .replace("\\[", "(")
                .replace("\\]", ")")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String platformFor(String emulator) {
        String lowered = emulator.toLowerCase(Locale.ROOT);
        String mapped = EMUDECK_PLATFORM_LOOKUPS.get(lowered);
        if (mapped != null) {
            return mapped;
        }

        // Fall back to a capitalised version of the subfolder name so unknown
        // emulators still appear with a sensible label.
        return Character.toUpperCase(lowered.charAt(0)) + lowered.substring(1);
    }
}
