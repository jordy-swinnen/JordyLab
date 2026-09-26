package dev.jordy.jordylab.gamecatalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ScriptServiceTest {

    private static final String KEYCLOAK_URL = "http://localhost:8180";
    private static final String REALM = "jordylab";
    private static final String CLIENT_ID = "gamecatalog-script";

    private ScriptService scriptService;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        scriptService = new ScriptService(KEYCLOAK_URL + "/", REALM, CLIENT_ID);
        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
    }

    @Test
    void substitutesTheKnownPlaceholders() {
        String script = scriptService.generateScript("steam", request);

        assertSoftly(softly -> {
            softly.assertThat(script).contains("KEYCLOAK_URL='http://localhost:8180'");
            softly.assertThat(script).contains("REALM='jordylab'");
            softly.assertThat(script).contains("CLIENT_ID='gamecatalog-script'");
            softly.assertThat(script).contains("BACKEND_URL='http://localhost:8080'");
            softly.assertThat(script).contains("SCAN_ENDPOINT='/api/gamecatalog/ingest/scan'");
            softly.assertThat(script).contains("LIBRARY_TYPE='STEAM'");
        });
    }

    @Test
    void leavesTheScriptsOwnBashParameterExpansionsUntouched() {
        String script = scriptService.generateScript("steam", request);

        assertSoftly(softly -> {
            softly.assertThat(script).contains("host=${host%%.*}");
            softly.assertThat(script).contains("${interval:-5}");
        });
    }

    @Test
    void leavesNoKnownPlaceholderUnresolved() {
        String script = scriptService.generateScript("emudeck", request);

        assertThat(script)
                .doesNotContain("${KEYCLOAK_URL}", "${REALM}", "${CLIENT_ID}", "${BACKEND_URL}",
                        "${SCAN_ENDPOINT}", "${LIBRARY_TYPE}");
    }

    @Test
    void generatedScriptDoesNotDependOnGnuOnlyFindPrintf() {
        String script = scriptService.generateScript("steam", request);

        assertThat(script).doesNotContain("-printf");
    }

    @Test
    void libraryTypeIsCaseInsensitive() {
        String script = scriptService.generateScript(" EmuDeck ", request);

        assertThat(script).contains("LIBRARY_TYPE='EMUDECK'");
    }

    @Test
    void unknownLibraryTypeIsRejected() {
        assertThatThrownBy(() -> scriptService.generateScript("bogus", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("libraryType must be 'steam' or 'emudeck'");
    }

    @Test
    void missingLibraryTypeIsRejected() {
        assertThatThrownBy(() -> scriptService.generateScript(null, request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
