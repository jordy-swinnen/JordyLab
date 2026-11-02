package dev.jordy.jordylab.feedback.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.jordy.jordylab.feedback.FeedbackProperties;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.service.LinearUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The Linear GraphQL contract (contracts/linear-client.md): the exact mutation body and label ids,
 * the guest-marked description with the source line last, cached lookups, the adopt-on-retry
 * search, and every failure becoming {@link LinearUnavailableException}.
 */
@WireMockTest(httpPort = 9990)
class LinearClientTest {

    private static final String GRAPHQL_URL = "http://localhost:9990/graphql";
    private static final String API_KEY = "test-personal-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LinearClient client;

    @BeforeEach
    void setUp() {
        client = new LinearClient(properties(API_KEY), objectMapper);
        client.init();
    }

    @Test
    void createsTheTriageIssueWithLabelsAndTheGuestMarkedDescription() {
        stubLookups();
        stubCreateIssue();
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();

        LinearClient.LinearIssue issue = client.createIssue(ticket, "Repro'd on CachyOS too.");

        assertSoftly(softly -> {
            softly.assertThat(issue.id()).isEqualTo("issue-1");
            softly.assertThat(issue.url()).isEqualTo("https://linear.app/jordylab/issue/JOR-101");
        });
        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withHeader("Authorization", equalTo(API_KEY))
                .withRequestBody(matchingJsonPath("$.query", containing("issueCreate(input: $input)")))
                .withRequestBody(matchingJsonPath("$.variables.input.teamId", equalTo("team-1")))
                .withRequestBody(matchingJsonPath("$.variables.input.stateId", equalTo("state-triage")))
                .withRequestBody(matchingJsonPath("$.variables.input.title",
                        equalTo(FeedbackTicketTestBuilder.DEFAULT_TITLE)))
                .withRequestBody(matchingJsonPath("$.variables.input.labelIds[0]", equalTo("label-app")))
                .withRequestBody(matchingJsonPath("$.variables.input.labelIds[1]", equalTo("label-bug"))));

        JsonNode description = lastRequestDescription();
        assertSoftly(softly -> {
            softly.assertThat(description.asText()).startsWith(FeedbackTicketTestBuilder.DEFAULT_DESCRIPTION);
            softly.assertThat(description.asText())
                    .contains("Written by a guest in the Game Catalog app's feedback form.");
            softly.assertThat(description.asText()).contains("Note from Jordy: Repro'd on CachyOS too.");
            softly.assertThat(description.asText()).endsWith("Source: idea box");
        });
    }

    @Test
    void aTicketWithoutAPrivateNoteHasNoNoteSectionAndStillEndsWithTheSourceLine() {
        stubLookups();
        stubCreateIssue();

        client.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null);

        String description = lastRequestDescription().asText();
        assertSoftly(softly -> {
            softly.assertThat(description).doesNotContain("Note from Jordy:");
            softly.assertThat(description).endsWith("Source: idea box");
        });
    }

    @Test
    void cachesTheTeamStateAndLabelLookupsAcrossCreates() {
        stubLookups();
        stubCreateIssue();

        client.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null);
        client.createIssue(FeedbackTicketTestBuilder.aFeedbackTicket().id(null).title("Second ticket").build(), null);

        verify(1, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("teams("))));
        verify(2, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueCreate"))));
    }

    @Test
    void fallsBackToThePlatformLabelWhenTheAppLabelIsMissing() {
        stubTeamAndStates();
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueLabels")))
                .willReturn(okJson("""
                        {"data":{"issueLabels":{"nodes":[{"id":"label-bug","name":"Bug"},
                        {"id":"label-platform","name":"Platform"}]}}}
                        """)));
        stubCreateIssue();

        client.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null);

        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.variables.input.labelIds[0]", equalTo("label-platform"))));
    }

    @Test
    void anUnconfiguredKeyFailsFastWithoutACall() {
        LinearClient unconfigured = new LinearClient(properties(""), objectMapper);
        unconfigured.init();

        assertThatThrownBy(() -> unconfigured.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(LinearUnavailableException.class)
                .hasMessageContaining("LINEAR_API_KEY");
        verify(0, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    void aGraphQlErrorBecomesUnavailable() {
        stubTeamAndStates();
        stubLabels();
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueCreate")))
                .willReturn(okJson("""
                        {"errors":[{"message":"Field 'title' is required"}]}
                        """)));

        assertThatThrownBy(() -> client.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(LinearUnavailableException.class)
                .hasMessageContaining("Field 'title' is required");
    }

    @Test
    void aMissingTriageStateBecomesUnavailable() {
        stubTeamAndStates();
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("workflowStates")))
                .willReturn(okJson("""
                        {"data":{"workflowStates":{"nodes":[{"id":"state-other","name":"Backlog"}]}}}
                        """)));
        stubLabels();

        assertThatThrownBy(() -> client.createIssue(FeedbackTicketTestBuilder.aDefaultFeedbackTicket(), null))
                .isInstanceOf(LinearUnavailableException.class)
                .hasMessageContaining("Triage");
    }

    @Test
    void findsAnIssueForTheAdoptOnRetryPath() {
        stubTeamAndStates();
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issues(")))
                .willReturn(okJson("""
                        {"data":{"issues":{"nodes":[{"id":"issue-2","url":"https://linear.app/jordylab/issue/JOR-102"}]}}}
                        """)));

        Optional<LinearClient.LinearIssue> found = client.findIssue(FeedbackTicketTestBuilder.DEFAULT_TITLE);

        assertThat(found).contains(new LinearClient.LinearIssue("issue-2",
                "https://linear.app/jordylab/issue/JOR-102"));
        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("Source: idea box")))
                .withRequestBody(matchingJsonPath("$.variables.title", equalTo(FeedbackTicketTestBuilder.DEFAULT_TITLE))));
    }

    @Test
    void findsNothingWhenNoIssueMatches() {
        stubTeamAndStates();
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issues(")))
                .willReturn(okJson("""
                        {"data":{"issues":{"nodes":[]}}}
                        """)));

        assertThat(client.findIssue(FeedbackTicketTestBuilder.DEFAULT_TITLE)).isEmpty();
    }

    private void stubLookups() {
        stubTeamAndStates();
        stubLabels();
    }

    private void stubTeamAndStates() {
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("teams(")))
                .willReturn(okJson("""
                        {"data":{"teams":{"nodes":[{"id":"team-1"}]}}}
                        """)));
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("workflowStates")))
                .willReturn(okJson("""
                        {"data":{"workflowStates":{"nodes":[{"id":"state-other","name":"Backlog"},
                        {"id":"state-triage","name":"Triage"}]}}}
                        """)));
    }

    private void stubLabels() {
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueLabels")))
                .willReturn(okJson("""
                        {"data":{"issueLabels":{"nodes":[{"id":"label-app","name":"Game Catalog"},
                        {"id":"label-bug","name":"Bug"},{"id":"label-feature","name":"Feature"},
                        {"id":"label-improvement","name":"Improvement"},{"id":"label-idea","name":"Idea"},
                        {"id":"label-platform","name":"Platform"}]}}}
                        """)));
    }

    private void stubCreateIssue() {
        stubFor(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueCreate")))
                .willReturn(okJson("""
                        {"data":{"issueCreate":{"success":true,"issue":{"id":"issue-1",
                        "url":"https://linear.app/jordylab/issue/JOR-101"}}}}
                        """)));
    }

    private JsonNode lastRequestDescription() {
        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("issueCreate"))));
        LoggedRequest last = requests.get(requests.size() - 1);
        try {
            return objectMapper.readTree(last.getBodyAsString())
                    .path("variables").path("input").path("description");
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private static FeedbackProperties properties(String apiKey) {
        return new FeedbackProperties(5, "Europe/Brussels",
                new FeedbackProperties.Linear(GRAPHQL_URL, apiKey, "JOR", 2000, 5000));
    }
}
