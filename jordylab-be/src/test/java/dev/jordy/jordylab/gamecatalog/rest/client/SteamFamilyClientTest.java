package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9998)
class SteamFamilyClientTest {

    private static final String BASE_URL = "http://localhost:9998";
    private static final String TOKEN = "test-access-token";

    private SteamFamilyClient steamFamilyClient;

    @BeforeEach
    void setUp() {
        steamFamilyClient = new SteamFamilyClient(properties(), new ObjectMapper());
        steamFamilyClient.baseUrl = BASE_URL;
        steamFamilyClient.init();
    }

    @Test
    void fetchesSharedLibraryUsingTheGroupIdFromTheFamilyGroupResponse() {
        stubFamilyGroup("""
                { "response": { "family_groupid": "123" } }
                """);
        stubSharedApps("""
                { "response": { "apps": [
                  { "appid": 730, "name": "Counter-Strike 2", "owner_steamids": ["76500000000000001"], "exclude_reason": 0 }
                ] } }
                """);

        List<SteamFamilyClient.FamilyGame> games = steamFamilyClient.fetchSharedLibrary(TOKEN);

        assertSoftly(softly -> {
            softly.assertThat(games).hasSize(1);
            softly.assertThat(games.get(0).appId()).isEqualTo("730");
            softly.assertThat(games.get(0).name()).isEqualTo("Counter-Strike 2");
            softly.assertThat(games.get(0).isShareable()).isTrue();
            softly.assertThat(games.get(0).ownerIds()).containsExactly("76500000000000001");
        });
        verify(getRequestedFor(urlPathEqualTo("/IFamilyGroupsService/GetSharedLibraryApps/v1"))
                .withQueryParam("family_groupid", equalTo("123"))
                .withQueryParam("access_token", equalTo(TOKEN)));
    }

    @Test
    void groupIdFallsBackToAlternateFieldNames() {
        stubFamilyGroup("""
                { "response": { "family_group_id": "456" } }
                """);
        stubSharedApps("""
                { "response": { "apps": [] } }
                """);

        steamFamilyClient.fetchSharedLibrary(TOKEN);

        verify(getRequestedFor(urlPathEqualTo("/IFamilyGroupsService/GetSharedLibraryApps/v1"))
                .withQueryParam("family_groupid", equalTo("456")));
    }

    @Test
    void excludedAppIsNotShareable() {
        stubFamilyGroup("""
                { "response": { "family_groupid": "123" } }
                """);
        stubSharedApps("""
                { "response": { "apps": [
                  { "appid": 730, "name": "Excluded Title", "owner_steamids": [], "exclude_reason": 5 }
                ] } }
                """);

        List<SteamFamilyClient.FamilyGame> games = steamFamilyClient.fetchSharedLibrary(TOKEN);

        assertThat(games.get(0).isShareable()).isFalse();
    }

    @Test
    void appsEntriesWithoutAnAppIdAreSkipped() {
        stubFamilyGroup("""
                { "response": { "family_groupid": "123" } }
                """);
        stubSharedApps("""
                { "response": { "apps": [
                  { "name": "No App Id" },
                  { "appid": 730, "name": "Counter-Strike 2" }
                ] } }
                """);

        List<SteamFamilyClient.FamilyGame> games = steamFamilyClient.fetchSharedLibrary(TOKEN);

        assertThat(games).extracting(SteamFamilyClient.FamilyGame::appId).containsExactly("730");
    }

    @Test
    void missingGroupIdThrowsUnknownResponse() {
        stubFamilyGroup("""
                { "response": {} }
                """);

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("UNKNOWN_RESPONSE"));
    }

    @Test
    void nonArrayAppsThrowsUnknownResponse() {
        stubFamilyGroup("""
                { "response": { "family_groupid": "123" } }
                """);
        stubSharedApps("""
                { "response": { "apps": "not-an-array" } }
                """);

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("UNKNOWN_RESPONSE"));
    }

    @Test
    void missingResponseKeyThrowsUnknownResponse() {
        stubFamilyGroup("""
                { "not_response": {} }
                """);

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("UNKNOWN_RESPONSE"));
    }

    @Test
    void unauthorizedTokenThrowsTokenExpired() {
        stubFor(get(urlPathEqualTo("/IFamilyGroupsService/GetFamilyGroupForUser/v1"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("TOKEN_EXPIRED"));
    }

    @Test
    void forbiddenTokenThrowsTokenExpired() {
        stubFor(get(urlPathEqualTo("/IFamilyGroupsService/GetFamilyGroupForUser/v1"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("TOKEN_EXPIRED"));
    }

    @Test
    void serverErrorThrowsUnknownResponse() {
        stubFor(get(urlPathEqualTo("/IFamilyGroupsService/GetFamilyGroupForUser/v1"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> steamFamilyClient.fetchSharedLibrary(TOKEN))
                .isInstanceOf(SteamFamilyException.class)
                .satisfies(exception -> assertThat(((SteamFamilyException) exception).getErrorCode())
                        .isEqualTo("UNKNOWN_RESPONSE"));
    }

    private void stubFamilyGroup(String body) {
        stubFor(get(urlPathEqualTo("/IFamilyGroupsService/GetFamilyGroupForUser/v1")).willReturn(json(body)));
    }

    private void stubSharedApps(String body) {
        stubFor(get(urlPathEqualTo("/IFamilyGroupsService/GetSharedLibraryApps/v1")).willReturn(json(body)));
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(null, 30, null, null, null, null,
                new GameCatalogProperties.Library(720, 14, 1500, 2000));
    }
}
