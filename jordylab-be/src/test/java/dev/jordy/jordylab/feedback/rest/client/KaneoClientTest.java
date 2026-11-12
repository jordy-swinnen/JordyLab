package dev.jordy.jordylab.feedback.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import dev.jordy.jordylab.feedback.FeedbackProperties;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.service.BacklogUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The Kaneo REST contract (contracts/kaneo-client.md): the task body, the two label attaches, the
 * cached project, column and workspace lookups, the adopt-on-retry search, and every failure
 * becoming {@link BacklogUnavailableException}. The response bodies asserted here are the shapes the
 * trial observed; the live check in quickstart.md step 2 confirms them against the real instance.
 */
@WireMockTest(httpPort = 9991)
class KaneoClientTest {

    private static final String BASE_URL = "http://localhost:9991";
    private static final String API_KEY = "test-kaneo-key";
    private static final String BOARD_URL = BASE_URL + "/dashboard/workspace/ws-1/project/proj-1/board";
    private static final String PROJECT_PATH = "/api/project";
    private static final String TASK_PATH = "/api/task/proj-1";
    private static final String APP_ATTACH_PATH = "/api/label/label-app/task";
    private static final String TYPE_ATTACH_PATH = "/api/label/label-bug/task";
    private static final String COLUMNS_BODY = """
            [{"id":"col-1","slug":"todo","name":"Todo"},{"id":"col-2","slug":"triage","name":"Triage"}]
            """;
    private static final String LABELS_BODY = """
            [{"id":"label-app","name":"Game Catalog"},{"id":"label-bug","name":"Bug"},
            {"id":"label-row-7","name":"Bug","taskId":"task-7"}]
            """;
    private static final String SEARCH_HITS = """
            {"tasks":[
              {"id":"task-9","number":123,"title":"Grid loses my scroll position",
               "description":"When I open a game and go back.\\n\\nSource: idea box"},
              {"id":"task-10","number":122,"title":"Grid loses my scroll position","description":"Other"}]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KaneoClient client;

    @BeforeEach
    void setUp() {
        client = new KaneoClient(properties(API_KEY), objectMapper);
        client.init();
    }

    @Test
    void createsTheTriageTaskWithItsTwoLabelsAndTheGuestMarkedDescription() throws Exception {
        stubLookups();
        stubFor(post(urlEqualTo(TASK_PATH)).willReturn(okJson("{\"id\":\"task-1\",\"number\":124}")));
        stubFor(put(urlEqualTo(APP_ATTACH_PATH)).willReturn(okJson("{\"id\":\"row-1\"}")));
        stubFor(put(urlEqualTo(TYPE_ATTACH_PATH)).willReturn(okJson("{\"id\":\"row-2\"}")));
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();

        BacklogClient.BacklogTicket created = client.createTicket(ticket, "Repro'd on CachyOS too.");

        assertSoftly(softly -> {
            softly.assertThat(created.id()).isEqualTo("task-1");
            softly.assertThat(created.url()).isEqualTo(BOARD_URL);
        });
        JsonNode body = requestBody(findAll(postRequestedFor(urlEqualTo(TASK_PATH))));
        assertSoftly(softly -> {
            softly.assertThat(body.path("title").asText()).isEqualTo(FeedbackTicketTestBuilder.DEFAULT_TITLE);
            softly.assertThat(body.path("priority").asText()).isEqualTo("no-priority");
            softly.assertThat(body.path("status").asText()).isEqualTo("triage");
            softly.assertThat(body.path("description").asText())
                    .contains("Note from Jordy: Repro'd on CachyOS too.")
                    .endsWith("\n\nSource: idea box");
        });
        verify(postRequestedFor(urlEqualTo(TASK_PATH)).withHeader("Authorization", equalTo("Bearer " + API_KEY)));
        verify(putRequestedFor(urlEqualTo(APP_ATTACH_PATH)).withRequestBody(equalToJson("{\"taskId\":\"task-1\"}")));
        verify(putRequestedFor(urlEqualTo(TYPE_ATTACH_PATH)).withRequestBody(equalToJson("{\"taskId\":\"task-1\"}")));
    }

    @Test
    void createsNothingWhenTheTypeLabelIsMissing() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(okJson("[{\"id\":\"proj-1\",\"slug\":\"JOR\"}]")));
        stubFor(get(urlEqualTo("/api/column/proj-1")).willReturn(okJson(COLUMNS_BODY)));
        stubFor(get(urlEqualTo("/api/label/workspace/ws-1")).willReturn(okJson("[{\"id\":\"label-app\",\"name\":\"Game Catalog\"}]")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("Bug");
        verify(0, postRequestedFor(urlEqualTo(TASK_PATH)));
    }

    @Test
    void fallsBackToThePlatformLabelWhenTheAppLabelIsMissing() throws Exception {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(okJson("[{\"id\":\"proj-1\",\"slug\":\"JOR\"}]")));
        stubFor(get(urlEqualTo("/api/column/proj-1")).willReturn(okJson(COLUMNS_BODY)));
        stubFor(get(urlEqualTo("/api/label/workspace/ws-1")).willReturn(okJson("""
                [{"id":"label-bug","name":"Bug"},{"id":"label-platform","name":"Platform"}]
                """)));
        stubFor(post(urlEqualTo(TASK_PATH)).willReturn(okJson("{\"id\":\"task-1\",\"number\":124}")));
        stubFor(put(urlEqualTo("/api/label/label-platform/task")).willReturn(okJson("{\"id\":\"row-1\"}")));
        stubFor(put(urlEqualTo(TYPE_ATTACH_PATH)).willReturn(okJson("{\"id\":\"row-2\"}")));

        client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null);

        verify(putRequestedFor(urlEqualTo("/api/label/label-platform/task")));
    }

    @Test
    void failsBeforeAnyCallWhenTheKeyIsNotConfigured() {
        KaneoClient unconfigured = new KaneoClient(properties(""), objectMapper);
        unconfigured.init();

        assertThatThrownBy(() -> unconfigured.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("KANEO_API_KEY");
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void anUnauthorisedKeyIsUnavailableAndTheResponseBodyIsNotEchoed() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"secret detail\",\"code\":\"UNAUTHORIZED\"}")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("secret detail");
        verify(0, postRequestedFor(urlEqualTo(TASK_PATH)));
    }

    @Test
    void aRateLimitIsUnavailableAndNotRetriedInsideTheRequest() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", "30")
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"slow down\",\"code\":\"RATE_LIMITED\"}")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("HTTP 429");
        verify(1, getRequestedFor(urlPathEqualTo(PROJECT_PATH)));
    }

    @Test
    void aServerErrorIsUnavailable() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void aTimeoutIsUnavailable() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(2000)
                .withBody("[]")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class);
    }

    @Test
    void aMissingTriageColumnIsUnavailableBeforeAnyWrite() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(okJson("[{\"id\":\"proj-1\",\"slug\":\"JOR\"}]")));
        stubFor(get(urlEqualTo("/api/column/proj-1")).willReturn(okJson("[{\"id\":\"col-1\",\"slug\":\"todo\"}]")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("triage");
        verify(0, postRequestedFor(urlEqualTo(TASK_PATH)));
    }

    @Test
    void aMissingProjectIsUnavailable() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).willReturn(okJson("[{\"id\":\"proj-2\",\"slug\":\"OTHER\"}]")));

        assertThatThrownBy(() -> client.createTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(BacklogUnavailableException.class)
                .hasMessageContaining("JOR");
    }

    @Test
    void theAdoptPathFindsTheSameTitleWithTheMarkerAndAttachesItsMissingLabels() throws Exception {
        stubLookups();
        stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", equalTo(FeedbackTicketTestBuilder.DEFAULT_TITLE))
                .withQueryParam("type", equalTo("tasks"))
                .willReturn(okJson(SEARCH_HITS)));
        stubFor(put(urlEqualTo(APP_ATTACH_PATH)).willReturn(okJson("{\"id\":\"row-1\"}")));
        stubFor(put(urlEqualTo(TYPE_ATTACH_PATH)).willReturn(okJson("{\"id\":\"row-2\"}")));

        Optional<BacklogClient.BacklogTicket> found =
                client.findTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket());

        assertThat(found).contains(new BacklogClient.BacklogTicket("task-9", BOARD_URL));
        verify(putRequestedFor(urlEqualTo(APP_ATTACH_PATH)).withRequestBody(equalToJson("{\"taskId\":\"task-9\"}")));
        verify(putRequestedFor(urlEqualTo(TYPE_ATTACH_PATH)).withRequestBody(equalToJson("{\"taskId\":\"task-9\"}")));
    }

    @Test
    void theAdoptPathDoesNotAttachLabelsTheTaskAlreadyCarries() {
        stubLookups();
        stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson(SEARCH_HITS)));
        stubFor(get(urlEqualTo("/api/label/workspace/ws-1")).willReturn(okJson("""
                [{"id":"label-app","name":"Game Catalog"},{"id":"label-bug","name":"Bug"},
                {"id":"row-a","name":"Game Catalog","taskId":"task-9"},{"id":"row-b","name":"Bug","taskId":"task-9"}]
                """)));

        client.findTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket());

        verify(0, putRequestedFor(urlEqualTo(APP_ATTACH_PATH)));
        verify(0, putRequestedFor(urlEqualTo(TYPE_ATTACH_PATH)));
    }

    @Test
    void theAdoptPathReturnsNothingWhenNoTaskCarriesTheMarker() {
        stubLookups();
        stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
                {"tasks":[{"id":"task-10","number":122,"title":"Grid loses my scroll position","description":"Other"}]}
                """)));

        assertThat(client.findTicket(FeedbackTicketTestBuilder.aDefaultFeedbackTicket())).isEmpty();
        verify(0, putRequestedFor(urlEqualTo(APP_ATTACH_PATH)));
    }

    private static void stubLookups() {
        stubFor(get(urlPathEqualTo(PROJECT_PATH)).withQueryParam("workspaceId", equalTo("ws-1"))
                .willReturn(okJson("[{\"id\":\"proj-1\",\"slug\":\"JOR\"},{\"id\":\"proj-2\",\"slug\":\"OTHER\"}]")));
        stubFor(get(urlEqualTo("/api/column/proj-1")).willReturn(okJson(COLUMNS_BODY)));
        stubFor(get(urlEqualTo("/api/label/workspace/ws-1")).willReturn(okJson(LABELS_BODY)));
    }

    private static ResponseDefinitionBuilder okJson(String body) {
        return aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private JsonNode requestBody(List<LoggedRequest> requests) throws Exception {
        return objectMapper.readTree(requests.get(0).getBodyAsString());
    }

    private static FeedbackProperties properties(String apiKey) {
        return new FeedbackProperties(5, "Europe/Brussels", null, null,
                new FeedbackProperties.Kaneo(BASE_URL, apiKey, "ws-1", "JOR", "triage", null, 2000, 300));
    }
}
