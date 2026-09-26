package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class SteamLibraryParserTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-02T10:20:00Z");
    private static final String MANIFEST_PATH = "steamapps/appmanifest_620.acf";
    private static final String PORTAL_2 = """
            "AppState"
            {
                "appid"      "620"
                "name"       "Portal 2"
                "installdir" "Portal 2"
            }
            """;

    private final SteamLibraryParser parser = new SteamLibraryParser();

    @Test
    void mapsEverySteamManifestToAGamePayload() {
        ScanRequest request = request(Map.of(MANIFEST_PATH, PORTAL_2));

        List<GamePayload> games = parser.parse(request);

        assertThat(games).containsExactly(new GamePayload("620", "Portal 2", "Steam", null));
    }

    @Test
    void fallsBackToInstalldirWhenTheManifestHasNoName() {
        String manifest = """
                "AppState"
                {
                    "appid"      "620"
                    "installdir" "Portal 2"
                }
                """;
        ScanRequest request = request(Map.of(MANIFEST_PATH, manifest));

        assertThat(parser.parse(request)).containsExactly(new GamePayload("620", "Portal 2", "Steam", null));
    }

    @Test
    void sanitizesMarkupInTheSteamName() {
        String manifest = """
                "AppState"
                {
                    "appid" "620"
                    "name"  "Portal <b>2</b>"
                }
                """;
        ScanRequest request = request(Map.of(MANIFEST_PATH, manifest));

        assertThat(parser.parse(request).get(0).title()).isEqualTo("Portal 2");
    }

    @Test
    void skipsFilesThatAreNotAppManifests() {
        ScanRequest request = request(Map.of(
                "steamapps/libraryfolders.vdf", PORTAL_2,
                "steamapps/appmanifest_bogus.acf", PORTAL_2));

        assertThat(parser.parse(request)).isEmpty();
    }

    @Test
    void skipsManifestsWithoutAnAppIdOrTitle() {
        ScanRequest request = request(Map.of(MANIFEST_PATH, """
                "AppState"
                {
                    "appid"      "620"
                    "StateFlags" "4"
                }
                """));

        assertThat(parser.parse(request)).isEmpty();
    }

    @Test
    void returnsNoGamesWhenThereAreNoManifests() {
        assertSoftly(softly -> {
            softly.assertThat(parser.parse(request(Map.of()))).isEmpty();
            softly.assertThat(parser.parse(request(null))).isEmpty();
        });
    }

    @Test
    void supportsSteamSourceType() {
        assertThat(parser.supports()).isEqualTo(SourceType.STEAM);
    }

    private static ScanRequest request(Map<String, String> manifests) {
        return new ScanRequest("jordybox", SourceType.STEAM, CAPTURED_AT,
                List.of(new ScanEntry(MANIFEST_PATH, 1024L, CAPTURED_AT)), manifests);
    }
}
