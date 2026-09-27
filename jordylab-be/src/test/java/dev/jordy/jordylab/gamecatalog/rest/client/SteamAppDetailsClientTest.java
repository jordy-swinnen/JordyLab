package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 9995)
class SteamAppDetailsClientTest {

    private static final String STORE_BASE_URL = "http://localhost:9995";

    private SteamAppDetailsClient steamAppDetailsClient;

    @BeforeEach
    void setUp() {
        steamAppDetailsClient = new SteamAppDetailsClient(properties(), new ObjectMapper());
        steamAppDetailsClient.storeBaseUrl = STORE_BASE_URL;
        steamAppDetailsClient.init();
    }

    @Test
    void parsesDeterministicFieldsFromAppDetails() {
        stubAppDetails("620", successfulBody());

        Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch("620");

        assertThat(metadata).isPresent();
        assertThat(metadata.get().genres()).isEqualTo("Puzzle, Adventure");
        assertThat(metadata.get().developer()).isEqualTo("Valve");
        assertThat(metadata.get().publisher()).isEqualTo("Valve");
        assertThat(metadata.get().releaseYear()).isEqualTo(2011);
    }

    @Test
    void unsuccessfulAppIsEmpty() {
        stubAppDetails("620", """
                { "620": { "success": false } }
                """);

        assertThat(steamAppDetailsClient.fetch("620")).isEmpty();
    }

    @Test
    void missingAppIdInResponseIsEmpty() {
        stubAppDetails("620", """
                { "999": { "success": true, "data": { "name": "Other" } } }
                """);

        assertThat(steamAppDetailsClient.fetch("620")).isEmpty();
    }

    @Test
    void genresAreBoundedToTwoHundredCharacters() {
        String manyGenres = """
                { "620": { "success": true, "data": {
                    "genres": [
                      { "id": "1", "description": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" },
                      { "id": "2", "description": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" },
                      { "id": "3", "description": "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC" },
                      { "id": "4", "description": "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD" },
                      { "id": "5", "description": "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE" },
                      { "id": "6", "description": "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF" }
                    ]
                } } }
                """;
        stubAppDetails("620", manyGenres);

        Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch("620");

        assertThat(metadata).isPresent();
        assertThat(metadata.get().genres()).hasSizeLessThanOrEqualTo(200);
    }

    @Test
    void releaseDateWithoutAYearIsNull() {
        stubAppDetails("620", """
                { "620": { "success": true, "data": {
                    "name": "Portal 2",
                    "release_date": { "coming_soon": true, "date": "To be announced" }
                } } }
                """);

        Optional<SteamAppDetailsClient.SteamMetadata> metadata = steamAppDetailsClient.fetch("620");

        assertThat(metadata).isPresent();
        assertThat(metadata.get().releaseYear()).isNull();
    }

    @Test
    void httpFailureIsEmpty() {
        stubFor(get(urlPathEqualTo("/api/appdetails")).willReturn(aResponse().withStatus(500)));

        assertThat(steamAppDetailsClient.fetch("620")).isEmpty();
    }

    @Test
    void blankAppIdIsEmpty() {
        assertThat(steamAppDetailsClient.fetch(" ")).isEmpty();
    }

    @Language("JSON")
    private String successfulBody() {
        return """
                {
                  "620": {
                    "success": true,
                    "data": {
                      "name": "Portal 2",
                      "developers": ["Valve"],
                      "publishers": ["Valve"],
                      "genres": [
                        { "id": "4", "description": "Puzzle" },
                        { "id": "25", "description": "Adventure" }
                      ],
                      "release_date": { "coming_soon": false, "date": "18 Apr, 2011" }
                    }
                  }
                }
                """;
    }

    private void stubAppDetails(String appId, @Language("JSON") String body) {
        stubFor(get(urlPathEqualTo("/api/appdetails"))
                .withQueryParam("appids", equalTo(appId))
                .withQueryParam("filters", equalTo("basic"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5));
    }
}
