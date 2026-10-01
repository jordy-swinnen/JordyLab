package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9996)
class IgdbClientTest {

    private static final String BASE = "http://localhost:9996";

    private IgdbClient igdbClient;

    @BeforeEach
    void setUp() {
        igdbClient = new IgdbClient(new ObjectMapper());
        igdbClient.clientId = "client-id";
        igdbClient.clientSecret = "client-secret";
        igdbClient.apiBaseUrl = BASE + "/v4";
        igdbClient.tokenUrl = BASE + "/oauth2/token";
        igdbClient.timeoutMs = 2000;
        igdbClient.init();
        stubToken();
    }

    @Test
    void findsAllExactNameMatchesAndFiltersOutTheRest() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("""
                [ {"id": 123, "name": "Super Mario World"}, {"id": 999, "name": "Super Mario World 2"} ]
                """)));

        List<Long> ids = igdbClient.findGameIdsByTitle("Super Mario World");

        assertThat(ids).containsExactly(123L);
        verify(postRequestedFor(urlPathEqualTo("/v4/games"))
                .withHeader("Client-ID", equalTo("client-id"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(containing("game_type = 0")));
    }

    @Test
    void noExactNameMatchIsEmpty() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("""
                [ {"id": 123, "name": "Something Else"} ]
                """)));

        assertThat(igdbClient.findGameIdsByTitle("Super Mario World")).isEmpty();
    }

    @Test
    void resolvesToTheSameNamedEntryThatActuallyHasModes() {
        // IGDB holds several "GoldenEye 007" entries; only the N64 original carries modes.
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("""
                [ {"id": 350140, "name": "GoldenEye 007"}, {"id": 1638, "name": "GoldenEye 007"} ]
                """)));
        stubFor(post(urlPathEqualTo("/v4/multiplayer_modes")).willReturn(json("""
                [ {"game": 1638, "platform": 4, "splitscreen": true, "offlinemax": 4} ]
                """)));

        Optional<IgdbClient.MultiplayerMode> mode = igdbClient.resolveMultiplayerMode("GoldenEye 007");

        assertSoftly(softly -> {
            softly.assertThat(mode).isPresent();
            softly.assertThat(mode.get().localMultiplayer()).isTrue();
            softly.assertThat(mode.get().splitScreen()).isTrue();
            softly.assertThat(mode.get().maxLocalPlayers()).isEqualTo(4);
        });
    }

    @Test
    void aggregatesMultiplayerModesAcrossPlatformRows() {
        stubFor(post(urlPathEqualTo("/v4/multiplayer_modes")).willReturn(json("""
                [
                  {"game": 123, "platform": 19, "offlinecoop": true, "offlinecoopmax": 2, "splitscreen": true},
                  {"game": 123, "platform": 6, "offlinecoop": true, "offlinemax": 4, "onlinecoop": true}
                ]
                """)));

        Map<Long, IgdbClient.MultiplayerMode> modes = igdbClient.fetchMultiplayerModes(List.of(123L));

        IgdbClient.MultiplayerMode mode = modes.get(123L);
        assertSoftly(softly -> {
            softly.assertThat(mode).isNotNull();
            softly.assertThat(mode.localMultiplayer()).isTrue();
            softly.assertThat(mode.splitScreen()).isTrue();
            softly.assertThat(mode.onlineMultiplayer()).isTrue();
            softly.assertThat(mode.maxLocalPlayers()).isEqualTo(4);
        });
    }

    @Test
    void offlineMaxWithoutALocalModeIsNotReportedAsLocalPlayers() {
        stubFor(post(urlPathEqualTo("/v4/multiplayer_modes")).willReturn(json("""
                [ {"game": 1070, "platform": 19, "offlinemax": 2, "offlinecoop": false, "splitscreen": false} ]
                """)));

        IgdbClient.MultiplayerMode mode = igdbClient.fetchMultiplayerModes(List.of(1070L)).get(1070L);

        assertSoftly(softly -> {
            softly.assertThat(mode).isNotNull();
            softly.assertThat(mode.localMultiplayer()).isFalse();
            softly.assertThat(mode.maxLocalPlayers()).isNull();
        });
    }

    @Test
    void unauthorizedResponseRefreshesTheTokenAndRetries() {
        stubFor(post(urlPathEqualTo("/v4/games"))
                .willReturn(aResponse().withStatus(401))
                .willReturn(json("""
                        [ {"id": 123, "name": "Portal 2"} ]
                        """)));

        assertThat(igdbClient.findGameIdsByTitle("Portal 2")).containsExactly(123L);
    }

    @Test
    void unconfiguredClientReturnsEmptyWithoutCalling() {
        igdbClient.clientId = "";

        assertThat(igdbClient.isConfigured()).isFalse();
        assertThat(igdbClient.findGameIdsByTitle("Portal 2")).isEmpty();
        assertThat(igdbClient.fetchMultiplayerModes(List.of(1L))).isEmpty();
        assertThat(igdbClient.resolveMultiplayerMode("Portal 2")).isEmpty();
    }

    @Test
    void serverFailureIsEmpty() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(aResponse().withStatus(500)));

        assertThat(igdbClient.findGameIdsByTitle("Portal 2")).isEmpty();
    }

    @Test
    void searchSwitchGamesReturnsSwitchMatchesWithImageUrls() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("""
                [
                  {
                    "id": 1111,
                    "name": "Mario Kart 8 Deluxe",
                    "first_release_date": 1492819200,
                    "genres": [{"name": "Racing"}],
                    "involved_companies": [{"company": {"name": "Nintendo EPD"}}],
                    "cover": {"image_id": "cover123"},
                    "artworks": [{"image_id": "art123"}]
                  }
                ]
                """)));

        List<IgdbClient.SwitchSearchResult> results = igdbClient.searchSwitchGames("mario kart");

        assertThat(results).hasSize(1);
        IgdbClient.SwitchSearchResult result = results.get(0);
        assertSoftly(softly -> {
            softly.assertThat(result.igdbGameId()).isEqualTo(1111L);
            softly.assertThat(result.title()).isEqualTo("Mario Kart 8 Deluxe");
            softly.assertThat(result.releaseYear()).isEqualTo(2017);
            softly.assertThat(result.genres()).containsExactly("Racing");
            softly.assertThat(result.developer()).isEqualTo("Nintendo EPD");
            softly.assertThat(result.coverUrl()).contains("cover123");
            softly.assertThat(result.bannerUrl()).contains("art123");
        });
        verify(postRequestedFor(urlPathEqualTo("/v4/games"))
                .withRequestBody(containing("platforms = (130)")));
    }

    @Test
    void searchSwitchGamesKeepsPortsAndExpandedGamesButNotDlcOrBundles() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("[]")));

        igdbClient.searchSwitchGames("mario kart 8 deluxe");

        verify(postRequestedFor(urlPathEqualTo("/v4/games"))
                .withRequestBody(containing("platforms = (130)"))
                .withRequestBody(containing("game_type = (0,4,8,9,10,11)")));
    }

    @Test
    void searchSwitchGamesEmptyWhenUnconfigured() {
        igdbClient.clientId = "";

        assertThat(igdbClient.searchSwitchGames("mario kart")).isEmpty();
    }

    @Test
    void fetchSwitchGameDetailsReturnsMetadataAndMultiplayer() {
        stubFor(post(urlPathEqualTo("/v4/games")).willReturn(json("""
                [
                  {
                    "id": 1111,
                    "name": "Mario Kart 8 Deluxe",
                    "first_release_date": 1492819200,
                    "genres": [{"name": "Racing"}],
                    "involved_companies": [{"company": {"name": "Nintendo EPD"}}],
                    "cover": {"image_id": "cover123"}
                  }
                ]
                """)));
        stubFor(post(urlPathEqualTo("/v4/multiplayer_modes")).willReturn(json("""
                [ {"game": 1111, "platform": 130, "offlinecoop": true, "offlinecoopmax": 4, "splitscreen": true} ]
                """)));

        Optional<IgdbClient.SwitchGameDetails> details = igdbClient.fetchSwitchGameDetails(1111L);

        assertThat(details).isPresent();
        assertSoftly(softly -> {
            softly.assertThat(details.get().title()).isEqualTo("Mario Kart 8 Deluxe");
            softly.assertThat(details.get().multiplayerMode()).isNotNull();
            softly.assertThat(details.get().multiplayerMode().localMultiplayer()).isTrue();
            softly.assertThat(details.get().multiplayerMode().maxLocalPlayers()).isEqualTo(4);
            softly.assertThat(details.get().multiplayerMode().splitScreen()).isTrue();
        });
    }

    @Test
    void buildImageUrlReturnsNullForBlankImageId() {
        assertThat(igdbClient.buildImageUrl("", IgdbClient.ImageSize.COVER_BIG)).isNull();
        assertThat(igdbClient.buildImageUrl(null, IgdbClient.ImageSize.COVER_BIG)).isNull();
    }

    private void stubToken() {
        stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(json("""
                {"access_token": "test-token", "expires_in": 3600, "token_type": "bearer"}
                """)));
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
