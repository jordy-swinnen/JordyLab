package dev.jordy.jordylab.shared.ai;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * LibBot's semantic index (spec 013, research A3) embeds game text through the same OpenRouter gateway as chat. The
 * OpenAI starter's embedding client must call {@code <base-url>/embeddings} with the base URL's {@code /v1} kept, send
 * the configured model and dimensions, and surface the usage the token counters need.
 */
@WireMockTest(httpPort = 9994)
class AiEmbeddingGatewayWiringTest {

    private static final String EMBEDDING_MODEL = "openai/text-embedding-3-small";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.openai.base-url=http://localhost:9994/api/v1",
                    "spring.ai.openai.api-key=test-gateway-key",
                    "spring.ai.openai.embedding.options.model=" + EMBEDDING_MODEL,
                    "spring.ai.openai.embedding.options.dimensions=1536");

    @Test
    void theEmbeddingCallGoesToTheV1EmbeddingsPathWithTheChosenModelAndDimensions() {
        stubFor(post(urlPathEqualTo("/api/v1/embeddings")).willReturn(okJson("""
                {"object":"list","model":"openai/text-embedding-3-small",
                 "data":[{"object":"embedding","index":0,"embedding":[0.25,-0.5,0.75]}],
                 "usage":{"prompt_tokens":5,"total_tokens":5}}
                """)));

        runner.run(context -> {
            EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

            float[] vector = embeddingModel.embed("A co-op puzzle game for four players");

            assertThat(vector).containsExactly(0.25f, -0.5f, 0.75f);
        });
        verify(postRequestedFor(urlPathEqualTo("/api/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer test-gateway-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(EMBEDDING_MODEL)))
                .withRequestBody(matchingJsonPath("$.dimensions", equalTo("1536"))));
    }

    @Test
    void theEmbeddingResponseCarriesTheTokenUsage() {
        stubFor(post(urlPathEqualTo("/api/v1/embeddings")).willReturn(okJson("""
                {"object":"list","model":"openai/text-embedding-3-small",
                 "data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                 "usage":{"prompt_tokens":7,"total_tokens":7}}
                """)));

        runner.run(context -> {
            EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);

            Integer promptTokens = embeddingModel.embedForResponse(java.util.List.of("hello"))
                    .getMetadata().getUsage().getPromptTokens();

            assertThat(promptTokens).isEqualTo(7);
        });
    }
}
