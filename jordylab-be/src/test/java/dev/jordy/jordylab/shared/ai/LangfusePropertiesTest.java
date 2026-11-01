package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangfusePropertiesTest {

    @Test
    void sendsToTheOtlpTracesPathOfTheConfiguredBase() {
        assertThat(new LangfuseProperties("pk", "sk", "https://eu.example/", false).tracesEndpoint())
                .isEqualTo("https://eu.example/api/public/otel/v1/traces");
        assertThat(new LangfuseProperties("pk", "sk", null, false).tracesEndpoint())
                .isEqualTo("https://cloud.langfuse.com/api/public/otel/v1/traces");
    }
}
