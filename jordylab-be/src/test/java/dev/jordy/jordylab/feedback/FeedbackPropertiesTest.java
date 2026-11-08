package dev.jordy.jordylab.feedback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The feedback settings' defaults and blank handling (spec 022, data-model section 2): an unset or blank value takes
 * the documented default, so a missing environment variable never leaves a client without its settings.
 */
class FeedbackPropertiesTest {

    @Test
    void unsetBlocksTakeTheDocumentedDefaults() {
        FeedbackProperties properties = new FeedbackProperties(0, " ", null, null, null);

        assertSoftly(softly -> {
            softly.assertThat(properties.dailyTicketLimit()).isEqualTo(5);
            softly.assertThat(properties.dailyLimitZone()).isEqualTo("Europe/Brussels");
            softly.assertThat(properties.backlog().provider()).isEqualTo("kaneo");
            softly.assertThat(properties.linear().baseUrl()).isEqualTo("https://api.linear.app/graphql");
            softly.assertThat(properties.linear().teamKey()).isEqualTo("JOR");
            softly.assertThat(properties.kaneo().projectKey()).isEqualTo("JOR");
            softly.assertThat(properties.kaneo().triageColumn()).isEqualTo("triage");
            softly.assertThat(properties.kaneo().ticketUrlTemplate())
                    .isEqualTo("{baseUrl}/dashboard/workspace/{workspaceId}/project/{projectId}/board");
            softly.assertThat(properties.kaneo().connectTimeoutMs()).isEqualTo(5000);
            softly.assertThat(properties.kaneo().readTimeoutMs()).isEqualTo(15000);
        });
    }

    @Test
    void blankValuesTakeTheDefaultsAndExplicitValuesAreKept() {
        FeedbackProperties properties = new FeedbackProperties(3, "UTC",
                new FeedbackProperties.Backlog(" "),
                null,
                new FeedbackProperties.Kaneo("https://kaneo.example", "key", "ws-1", "OPS", "  ", "", 1000, 2000));

        assertSoftly(softly -> {
            softly.assertThat(properties.dailyTicketLimit()).isEqualTo(3);
            softly.assertThat(properties.dailyLimitZone()).isEqualTo("UTC");
            softly.assertThat(properties.backlog().provider()).isEqualTo("kaneo");
            softly.assertThat(properties.kaneo().baseUrl()).isEqualTo("https://kaneo.example");
            softly.assertThat(properties.kaneo().workspaceId()).isEqualTo("ws-1");
            softly.assertThat(properties.kaneo().projectKey()).isEqualTo("OPS");
            softly.assertThat(properties.kaneo().triageColumn()).isEqualTo("triage");
            softly.assertThat(properties.kaneo().connectTimeoutMs()).isEqualTo(1000);
            softly.assertThat(properties.kaneo().readTimeoutMs()).isEqualTo(2000);
        });
    }

    @Test
    void theLinearProviderIsKeptWhenSet() {
        FeedbackProperties properties = new FeedbackProperties(5, "Europe/Brussels",
                new FeedbackProperties.Backlog("linear"), null, null);

        assertThat(properties.backlog().provider()).isEqualTo("linear");
    }
}
