package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.PlatformEntry;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient.Outcome;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient.PauseReason;
import dev.jordy.jordylab.gamecatalog.rest.client.TheGamesDbClient.PlayerCountLookup;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9993)
@ExtendWith(OutputCaptureExtension.class)
class TheGamesDbClientTest {

    private static final String API_KEY = "test-key-0123456789abcdef";
    private static final String PATH = "/v1/Games/ByGameName";
    private static final String BODY_MARKER = "SECRET-BODY-MARKER";
    private static final int GAMECUBE = 2;
    private static final int NINTENDO_64 = 3;
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    private AtomicReference<Instant> now;
    private TheGamesDbClient client;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(NOW);
        client = clientWithKey(API_KEY);
    }

    @Test
    void oneAgreedValueIsFoundAndTheRequestCarriesEverythingTheSourceNeeds() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 6", GAMECUBE, 4), game("Mario Party 6", GAMECUBE, 4)))));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
            softly.assertThat(lookup.maxLocalPlayers()).isEqualTo(4);
            softly.assertThat(lookup.gap()).isNull();
        });
        verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("name", equalTo("Mario Party 6"))
                .withQueryParam("fields", equalTo("players"))
                .withQueryParam("filter[platform]", equalTo("2"))
                .withQueryParam("apikey", equalTo(API_KEY))
                .withHeader("User-Agent", matching("JordyLab/1\\.0.*")));
    }

    @Test
    void aTitleWithAnAmpersandIsSentIntact() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Ratchet & Clank: Up Your Arsenal", 11, 4)))));

        PlayerCountLookup lookup = client.lookup("Ratchet & Clank - Up Your Arsenal", "ratchet and clank up your arsenal",
                "PlayStation 2");

        assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
        verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("name", equalTo("Ratchet & Clank - Up Your Arsenal")));
    }

    @Test
    void aBracketedVariantOfTheSameTitleMakesTheEntriesDisagree() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 7", GAMECUBE, 7), game("Mario Party 7", GAMECUBE, 7),
                game("Mario Party 7 [Nintendo GameCube Mic Included]", GAMECUBE, 4)))));

        PlayerCountLookup lookup = client.lookup("Mario Party 7", "mario party 7", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.GAP);
            softly.assertThat(lookup.gap()).isEqualTo(PlayerCountGap.ENTRIES_DISAGREE);
            softly.assertThat(lookup.maxLocalPlayers()).isNull();
        });
    }

    @Test
    void aDifferentlyNamedBundleIsIgnored() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 7", GAMECUBE, 7), game("Mario Party 7 Microphone Bundle", GAMECUBE, 1)))));

        PlayerCountLookup lookup = client.lookup("Mario Party 7", "mario party 7", "GameCube");

        assertThat(lookup.maxLocalPlayers()).isEqualTo(7);
    }

    @Test
    void entriesWithoutAPlayersValueAreIgnored() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("WWF No Mercy", NINTENDO_64, null), game("WWF No Mercy", NINTENDO_64, 4)))));

        PlayerCountLookup lookup = client.lookup("WWF No Mercy", "wwf no mercy", "Nintendo 64");

        assertThat(lookup.maxLocalPlayers()).isEqualTo(4);
    }

    @Test
    void matchingEntriesWithoutAnyPlayersValueAreNoMatch() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("WWF No Mercy", NINTENDO_64, null)))));

        PlayerCountLookup lookup = client.lookup("WWF No Mercy", "wwf no mercy", "Nintendo 64");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.GAP);
            softly.assertThat(lookup.gap()).isEqualTo(PlayerCountGap.NO_MATCH);
        });
    }

    @Test
    void onlyEntriesOnTheRequestedConsoleCount() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Wave Race", NINTENDO_64, 4)))));

        PlayerCountLookup lookup = client.lookup("Wave Race", "wave race", "GameCube");

        assertThat(lookup.gap()).isEqualTo(PlayerCountGap.NO_MATCH);
    }

    @Test
    void aSingleValueUnderTwoIsBelowTwo() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Trackmania", GAMECUBE, 1)))));

        PlayerCountLookup lookup = client.lookup("Trackmania", "trackmania", "GameCube");

        assertThat(lookup.gap()).isEqualTo(PlayerCountGap.COUNT_BELOW_TWO);
    }

    @Test
    void aTitleThatIsOnlyLooselySimilarIsNoMatch() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 6 Deluxe Remix", GAMECUBE, 4), game("Mario Party 5", GAMECUBE, 4)))));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertThat(lookup.gap()).isEqualTo(PlayerCountGap.NO_MATCH);
    }

    @Test
    void aConsoleThatTheSourceDoesNotListIsAnsweredWithoutARequest() {
        PlayerCountLookup lookup = client.lookup("Some Game", "some game", "Neo Geo Pocket");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.GAP);
            softly.assertThat(lookup.gap()).isEqualTo(PlayerCountGap.CONSOLE_NOT_LISTED);
        });
        verify(0, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void aSecondPageIsReadOnlyWhenTheAnswerSaysThereIsOneAndNeverByTheEchoedAddress() {
        stubFor(get(urlPathEqualTo(PATH)).atPriority(2).willReturn(json(answer(900, 2_600_000,
                "\"http://localhost:9993/must-not-be-called?apikey=" + API_KEY + "\"",
                game("Mario Party 6", GAMECUBE, 4)))));
        stubFor(get(urlPathEqualTo(PATH)).atPriority(1).withQueryParam("page", equalTo("2"))
                .willReturn(json(answer(899, 2_600_000, null, game("Mario Party 6", GAMECUBE, 6)))));
        stubFor(get(urlPathEqualTo("/must-not-be-called")).willReturn(aResponse().withStatus(500)));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertThat(lookup.gap()).isEqualTo(PlayerCountGap.ENTRIES_DISAGREE);
        verify(2, getRequestedFor(urlPathEqualTo(PATH)));
        verify(0, getRequestedFor(urlPathEqualTo("/must-not-be-called")));
    }

    @Test
    void atMostThreePagesAreRead() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, "\"next\"",
                game("Mario Party 6", GAMECUBE, 4)))));

        client.lookup("Mario Party 6", "mario party 6", "GameCube");

        verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void withoutAKeyNothingIsAskedAndTheStartLogSaysSo(CapturedOutput output) {
        TheGamesDbClient unconfigured = clientWithKey("");

        PlayerCountLookup lookup = unconfigured.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(unconfigured.state().configured()).isFalse();
            softly.assertThat(output.getAll()).containsOnlyOnce("TheGamesDB not configured");
        });
        verify(0, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void aRefusedKeyPausesTheSourceForAtLeastAnHourAndLaterLookupsMakeNoRequest(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403).withBody(refusal(0))));

        PlayerCountLookup first = client.lookup("Mario Party 6", "mario party 6", "GameCube");
        PlayerCountLookup second = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(first.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(second.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pauseReason()).isEqualTo(PauseReason.KEY_REFUSED_OR_ALLOWANCE_USED);
            softly.assertThat(client.state().pausedUntil()).isEqualTo(NOW.plus(Duration.ofHours(1)));
            softly.assertThat(output.getAll()).containsOnlyOnce("TheGamesDB refused the request (HTTP 403)");
        });
        verify(1, getRequestedFor(urlPathEqualTo(PATH)));
        assertNothingSecretWasLogged(output);
    }

    @Test
    void aRefusalPausesUntilTheAdvertisedTimerWhenThatIsLongerThanAnHour() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403).withBody(refusal(7200))));

        client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertThat(client.state().pausedUntil()).isEqualTo(NOW.plus(Duration.ofSeconds(7200)));
    }

    @Test
    void theSourceIsAskedAgainOnceThePauseHasPassed() {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403).withBody(refusal(0))));
        client.lookup("Mario Party 6", "mario party 6", "GameCube");
        now.set(NOW.plus(Duration.ofHours(2)));

        client.lookup("Mario Party 6", "mario party 6", "GameCube");

        verify(2, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void anAllowanceAtTheReserveStillAnswersButPausesTheSourceUntilItRefreshes(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(20, 5000, null,
                game("Mario Party 6", GAMECUBE, 4)))));

        PlayerCountLookup first = client.lookup("Mario Party 6", "mario party 6", "GameCube");
        PlayerCountLookup second = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(first.maxLocalPlayers()).isEqualTo(4);
            softly.assertThat(second.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pauseReason()).isEqualTo(PauseReason.ALLOWANCE_LOW);
            softly.assertThat(client.state().pausedUntil()).isEqualTo(NOW.plusSeconds(5000));
            softly.assertThat(client.state().remainingAllowance()).isEqualTo(20);
        });
        verify(1, getRequestedFor(urlPathEqualTo(PATH)));
        assertNothingSecretWasLogged(output);
    }

    @Test
    void aServerErrorPausesTheSourceForFifteenMinutes(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500).withBody(BODY_MARKER)));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pauseReason()).isEqualTo(PauseReason.UNREACHABLE);
            softly.assertThat(client.state().pausedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            softly.assertThat(output.getAll()).contains("TheGamesDB answered HTTP 500");
        });
        assertNothingSecretWasLogged(output);
    }

    @Test
    void anOtherClientErrorPausesTheSourceForFifteenMinutes(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404).withBody(BODY_MARKER)));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pausedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        });
        assertNothingSecretWasLogged(output);
    }

    @Test
    void aTimeoutPausesTheSourceForFifteenMinutes(CapturedOutput output) {
        client.timeoutMs = 400;
        client.init();
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 6", GAMECUBE, 4))).withFixedDelay(1500)));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pauseReason()).isEqualTo(PauseReason.UNREACHABLE);
            softly.assertThat(output.getAll()).contains("TheGamesDB unreachable (");
        });
        assertNothingSecretWasLogged(output);
    }

    @Test
    void anUnreadableAnswerIsDeferredWithoutPausingTheSource(CapturedOutput output) {
        stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(BODY_MARKER)));

        PlayerCountLookup lookup = client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertSoftly(softly -> {
            softly.assertThat(lookup.outcome()).isEqualTo(Outcome.DEFERRED);
            softly.assertThat(client.state().pausedUntil()).isNull();
            softly.assertThat(output.getAll()).contains("TheGamesDB response unreadable");
        });
        assertNothingSecretWasLogged(output);
    }

    @Test
    void callsAreAtLeastTheMinimumIntervalApart() {
        client.minIntervalMs = 250;
        stubFor(get(urlPathEqualTo(PATH)).willReturn(json(answer(900, 2_600_000, null,
                game("Mario Party 6", GAMECUBE, 4)))));
        long start = System.nanoTime();

        client.lookup("Mario Party 6", "mario party 6", "GameCube");
        client.lookup("Mario Party 6", "mario party 6", "GameCube");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(240));
    }

    @Test
    void everyConsoleOfTheCatalogHasAnIdAndAName() {
        assertThat(TheGamesDbClient.platforms()).hasSize(32).containsKeys("GameCube", "Nintendo 64", "PlayStation",
                "PlayStation 2", "PlayStation 3");
        assertThat(TheGamesDbClient.platforms().get("PlayStation 2").id()).isEqualTo(11);
    }

    @Test
    void everyConsoleOfTheCatalogExceptSteamIsMappedAndNothingElse() {
        Set<String> catalogConsoles = PlatformCatalog.all().stream().map(PlatformEntry::name)
                .filter(name -> !PlatformCatalog.STEAM.equals(name)).collect(Collectors.toSet());

        assertThat(TheGamesDbClient.platforms().keySet()).containsExactlyInAnyOrderElementsOf(catalogConsoles);
    }

    private TheGamesDbClient clientWithKey(String key) {
        TheGamesDbClient created = new TheGamesDbClient(new ObjectMapper(), fixedClock());
        created.apiKey = key;
        created.baseUrl = "http://localhost:9993/v1";
        created.timeoutMs = 2000;
        created.minIntervalMs = 0;
        created.allowanceReserve = 20;
        created.init();

        return created;
    }

    private Clock fixedClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }

    private void assertNothingSecretWasLogged(CapturedOutput output) {
        assertSoftly(softly -> {
            softly.assertThat(output.getAll()).doesNotContain(API_KEY);
            softly.assertThat(output.getAll()).doesNotContain("apikey");
            softly.assertThat(output.getAll()).doesNotContain(BODY_MARKER);
            softly.assertThat(output.getAll()).doesNotContain("localhost:9993");
        });
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String refusal(long timerSeconds) {
        return """
                {"code":403,"status":"Invalid API key was provided.","remaining_monthly_allowance":0,"allowance_refresh_timer":%d}
                """.formatted(timerSeconds);
    }

    private static String answer(int remaining, long timerSeconds, String next, String... games) {
        return """
                {"code":200,"status":"Success","data":{"count":%d,"games":[%s]},
                 "pages":{"previous":null,"current":"http://localhost:9993%s?apikey=%s","next":%s},
                 "remaining_monthly_allowance":%d,"extra_allowance":0,"allowance_refresh_timer":%d}
                """.formatted(games.length, String.join(",", games), PATH, API_KEY, next == null ? "null" : next,
                remaining, timerSeconds);
    }

    @Language("JSON")
    private static String game(String title, int platform, Integer players) {
        String playersField = players == null ? "" : ",\"players\":" + players;

        return "{\"id\":1,\"game_title\":\"" + title.replace("\"", "\\\"") + "\",\"platform\":" + platform
                + ",\"region_id\":2" + playersField + "}";
    }
}
