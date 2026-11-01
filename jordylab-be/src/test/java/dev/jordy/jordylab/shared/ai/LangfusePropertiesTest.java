package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangfusePropertiesTest {

    @Test
    void needsBothKeysToExport() {
        assertThat(new LangfuseProperties("pk", "sk", null, false).configured()).isTrue();
        assertThat(new LangfuseProperties("pk", "", null, false).configured()).isFalse();
        assertThat(new LangfuseProperties(null, null, null, false).configured()).isFalse();
    }

    @Test
    void sendsToTheOtlpTracesPathOfTheConfiguredBase() {
        assertThat(new LangfuseProperties("pk", "sk", "https://eu.example/", false).tracesEndpoint())
                .isEqualTo("https://eu.example/api/public/otel/v1/traces");
        assertThat(new LangfuseProperties("pk", "sk", null, false).tracesEndpoint())
                .isEqualTo("https://cloud.langfuse.com/api/public/otel/v1/traces");
    }
}
