package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class EmuDeckLibraryParserTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-02T10:20:00Z");

    private final EmuDeckLibraryParser parser = new EmuDeckLibraryParser();

    @Test
    void mapsRomFilesToGamesAndInfersThePlatform() {
        ScanRequest request = request(
                entry("snes/Super Mario World.sfc"),
                entry("gba/Pokemon Emerald.gba"));

        assertThat(parser.parse(request)).containsExactly(
                new GamePayload("snes/Super Mario World.sfc", "Super Mario World", "SNES", null),
                new GamePayload("gba/Pokemon Emerald.gba", "Pokemon Emerald", "Game Boy Advance", null));
    }

    @Test
    void ignoresFilesWithoutARomExtension() {
        ScanRequest request = request(
                entry("snes/readme.txt"),
                entry("snes/Super Mario World.png"),
                entry("snes/noextension"));

        assertThat(parser.parse(request)).isEmpty();
    }

    @Test
    void stripsRegionAndRevisionTagsFromTheTitle() {
        ScanRequest request = request(entry("snes/Super Mario World (USA) (Rev 1) [!].smc"));

        assertThat(parser.parse(request).get(0).title()).isEqualTo("Super Mario World");
    }

    @Test
    void replacesUnderscoresWithSpaces() {
        ScanRequest request = request(entry("snes/Super_Mario_World.smc"));

        assertThat(parser.parse(request).get(0).title()).isEqualTo("Super Mario World");
    }

    @Test
    void capitalisesUnknownEmulatorFolders() {
        ScanRequest request = request(entry("custom/Homebrew.iso"));

        assertThat(parser.parse(request).get(0).platform()).isEqualTo("Custom");
    }

    @Test
    void keepsOneEntryWhenRelpathsRepeat() {
        ScanRequest request = request(
                entry("snes/Super Mario World.sfc"),
                entry("snes/Super Mario World.sfc"));

        assertThat(parser.parse(request)).hasSize(1);
    }

    @Test
    void returnsNoGamesWhenTheListingIsEmpty() {
        assertThat(parser.parse(request())).isEmpty();
    }

    @Test
    void supportsEmuDeckSourceType() {
        assertThat(parser.supports()).isEqualTo(SourceType.EMUDECK);
    }

    private static ScanEntry entry(String relpath) {
        return new ScanEntry(relpath, 1024L, CAPTURED_AT);
    }

    private static ScanRequest request(ScanEntry... entries) {
        return new ScanRequest("jordybox", SourceType.EMUDECK, CAPTURED_AT, List.of(entries), null);
    }
}
