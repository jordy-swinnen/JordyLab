package dev.jordy.jordylab.gamecatalog.service.scan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class VdfParserTest {

    private static final String PORTAL_2_MANIFEST = """
            // Steam app manifest
            "AppState"
            {
                "appid"          "620"
                "Universe"       "1"
                "name"           "Portal 2"
                "StateFlags"     "4"
                "installdir"     "Portal 2"
                "LastUpdated"    "1700000000"
            }
            """;

    @Test
    void parsesNestedObjectsAndQuotedLeaves() {
        Map<String, Object> root = VdfParser.parse(PORTAL_2_MANIFEST);

        assertThat(root).containsOnlyKeys("AppState");
        assertThat(root.get("AppState")).isInstanceOf(Map.class);
    }

    @Test
    void returnsAnEmptyMapForBlankInput() {
        assertSoftly(softly -> {
            softly.assertThat(VdfParser.parse(null)).isEmpty();
            softly.assertThat(VdfParser.parse("")).isEmpty();
            softly.assertThat(VdfParser.parse("   ")).isEmpty();
        });
    }

    @Test
    void acceptsUnquotedKeysAndBarewordValues() {
        Map<String, Object> root = VdfParser.parse("Setting 42");

        assertThat(root).containsEntry("Setting", "42");
    }

    @Test
    void skipsCommentLines() {
        Map<String, Object> root = VdfParser.parse("""
                // a comment
                "name" "Portal 2"
                """);

        assertThat(root).containsEntry("name", "Portal 2");
    }

    @Test
    void nestedStringReadsANestedKey() {
        Map<String, Object> root = VdfParser.parse(PORTAL_2_MANIFEST);

        assertThat(VdfParser.nestedString(root, "fallback", "AppState", "name"))
                .isEqualTo("Portal 2");
    }

    @Test
    void nestedStringReturnsTheDefaultWhenAPathSegmentIsMissing() {
        Map<String, Object> root = VdfParser.parse(PORTAL_2_MANIFEST);

        assertSoftly(softly -> {
            softly.assertThat(VdfParser.nestedString(root, "fallback", "AppState", "missing"))
                    .isEqualTo("fallback");
            softly.assertThat(VdfParser.nestedString(root, "fallback", "Missing", "name"))
                    .isEqualTo("fallback");
        });
    }
}
