package dev.jordy.jordylab.feedback.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.feedback.FeedbackProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The provider switch (spec 022, R16): exactly one backlog client exists, Kaneo by default and Linear only when
 * the setting says so. Production keeps {@code linear} through the deploy until the cutover.
 */
class BacklogClientSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KaneoClient.class, LinearClient.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(FeedbackProperties.class, () -> new FeedbackProperties(5, "Europe/Brussels", null, null, null));

    @Test
    void kaneoIsSelectedWhenTheProviderIsNotSet() {
        runner.run(context -> assertOnlySelected(context, KaneoClient.class, LinearClient.class));
    }

    @Test
    void kaneoIsSelectedWhenTheProviderIsKaneo() {
        runner.withPropertyValues("jordylab.feedback.backlog.provider=kaneo")
                .run(context -> assertOnlySelected(context, KaneoClient.class, LinearClient.class));
    }

    @Test
    void linearIsSelectedWhenTheProviderIsLinear() {
        runner.withPropertyValues("jordylab.feedback.backlog.provider=linear")
                .run(context -> assertOnlySelected(context, LinearClient.class, KaneoClient.class));
    }

    private static void assertOnlySelected(AssertableApplicationContext context,
            Class<? extends BacklogClient> selected, Class<? extends BacklogClient> notSelected) {
        assertSoftly(softly -> {
            softly.assertThat(context.getBeansOfType(BacklogClient.class)).hasSize(1);
            softly.assertThat(context.getBeansOfType(selected)).hasSize(1);
            softly.assertThat(context.getBeansOfType(notSelected)).isEmpty();
        });
    }
}
