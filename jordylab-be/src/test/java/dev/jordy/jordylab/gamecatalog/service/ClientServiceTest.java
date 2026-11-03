package dev.jordy.jordylab.gamecatalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ClientServiceTest {

    private ClientService clientService;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        clientService = new ClientService();
        clientService.init();
        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
    }

    @Test
    void substitutesTheKnownPlaceholdersAsPythonLiterals() {
        String client = clientService.generateClient("steam", request);

        assertSoftly(softly -> {
            softly.assertThat(client).contains("BACKEND_URL = \"http://localhost:8080\"");
            softly.assertThat(client).contains("SCAN_ENDPOINT = \"/api/gamecatalog/ingest/scan\"");
            softly.assertThat(client).contains("LIBRARY_TYPE = \"STEAM\"");
            softly.assertThat(client).doesNotContain("${");
            // The scanner authenticates with a machine token now; no Keycloak constants are rendered.
            softly.assertThat(client).doesNotContain("KEYCLOAK_URL");
            softly.assertThat(client).doesNotContain("REALM");
        });
    }

    @Test
    void escapesPythonStringLiterals() {
        assertThat(ClientService.escapePythonString("a\"b\\c")).isEqualTo("a\\\"b\\\\c");
    }

    @Test
    void libraryTypeIsCaseInsensitive() {
        String client = clientService.generateClient(" EmuDeck ", request);

        assertThat(client).contains("LIBRARY_TYPE = \"EMUDECK\"");
    }

    @Test
    void unknownLibraryTypeIsRejected() {
        assertThatThrownBy(() -> clientService.generateClient("bogus", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("libraryType must be 'steam' or 'emudeck'");
    }

    @Test
    void missingLibraryTypeIsRejected() {
        assertThatThrownBy(() -> clientService.generateClient(null, request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
