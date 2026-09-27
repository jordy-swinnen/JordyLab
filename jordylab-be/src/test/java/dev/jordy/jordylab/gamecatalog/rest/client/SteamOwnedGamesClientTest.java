package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9997)
class SteamOwnedGamesClientTest {

    private static final String BASE_URL = "http://localhost:9997";

    private SteamOwnedGamesClient steamOwnedGamesClient;

    @BeforeEach
    void setUp() {
        steamOwnedGamesClient = new SteamOwnedGamesClient(properties(), new ObjectMapper());
        steamOwnedGamesClient.baseUrl = BASE_URL;
        steamOwnedGamesClient.apiKey = "test-key";
        steamOwnedGamesClient.steamId = "76561198000000000";
        steamOwnedGamesClient.init();
    }

    @Test
    void parsesOwnedGamesFromANormalResponse() {
        stubOwnedGames("""
                { "response": { "game_count": 2, "games": [
                  { "appid": 400, "name": "Portal", "playtime_forever": 0 },
                  { "appid": 620, "name": "Portal 2", "playtime_forever": 12 }
                ] } }
                """);

        Optional<List<SteamOwnedGamesClient.OwnedGame>> owned = steamOwnedGamesClient.fetchOwnedGames();

        assertThat(owned).isPresent();
        assertSoftly(softly -> {
            softly.assertThat(owned.get()).hasSize(2);
            softly.assertThat(owned.get().get(0).appId()).isEqualTo("400");
            softly.assertThat(owned.get().get(0).name()).isEqualTo("Portal");
            softly.assertThat(owned.get().get(1).appId()).isEqualTo("620");
        });
        verify(getRequestedFor(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1"))
                .withQueryParam("key", equalTo("test-key"))
                .withQueryParam("steamid", equalTo("76561198000000000"))
                .withQueryParam("include_appinfo", equalTo("true"))
                .withQueryParam("include_played_free_games", equalTo("true")));
    }

    @Test
    void missingGamesArrayIsAPresentEmptyList() {
        // Documented shape for a private/empty library: no `games` key under `response`.
        stubOwnedGames("""
                { "response": { "game_count": 0 } }
                """);

        Optional<List<SteamOwnedGamesClient.OwnedGame>> owned = steamOwnedGamesClient.fetchOwnedGames();

        assertThat(owned).contains(List.of());
    }

    @Test
    void nonArrayGamesIsEmpty() {
        stubOwnedGames("""
                { "response": { "games": "not-an-array" } }
                """);

        assertThat(steamOwnedGamesClient.fetchOwnedGames()).isEmpty();
    }

    @Test
    void entriesWithoutAnAppIdAreSkipped() {
        stubOwnedGames("""
                { "response": { "games": [
                  { "name": "No App Id" },
                  { "appid": 400, "name": "Portal" }
                ] } }
                """);

        Optional<List<SteamOwnedGamesClient.OwnedGame>> owned = steamOwnedGamesClient.fetchOwnedGames();

        assertThat(owned).isPresent();
        assertThat(owned.get()).extracting(SteamOwnedGamesClient.OwnedGame::appId).containsExactly("400");
    }

    @Test
    void httpErrorIsEmpty() {
        stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1")).willReturn(aResponse().withStatus(500)));

        assertThat(steamOwnedGamesClient.fetchOwnedGames()).isEmpty();
    }

    @Test
    void unreadableBodyIsEmpty() {
        stubOwnedGames("not-json");

        assertThat(steamOwnedGamesClient.fetchOwnedGames()).isEmpty();
    }

    @Test
    void unconfiguredWithoutApiKeyOrSteamIdReturnsEmptyWithoutCalling() {
        steamOwnedGamesClient.apiKey = "";

        assertThat(steamOwnedGamesClient.isConfigured()).isFalse();
        assertThat(steamOwnedGamesClient.fetchOwnedGames()).isEmpty();
    }

    private void stubOwnedGames(String body) {
        stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(null, 30, null, null, null, null,
                new GameCatalogProperties.Library(720, 14, 1500, 2000));
    }
}
