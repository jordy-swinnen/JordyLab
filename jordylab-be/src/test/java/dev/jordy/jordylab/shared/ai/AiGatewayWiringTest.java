package dev.jordy.jordylab.shared.ai;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

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
 * Both chat models must exist side by side — OpenRouter (OpenAI-compatible) as primary, Anthropic as fallback — which
 * only works while {@code spring.ai.model.chat} is left unset (006 research §1.1/§4.1). The OpenAI client must also
 * call {@code <base-url>/chat/completions} with the base URL's {@code /v1} kept.
 */
@WireMockTest(httpPort = 9995)
class AiGatewayWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class, ChatObservationAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class, AnthropicChatAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.openai.base-url=http://localhost:9995/api/v1",
                    "spring.ai.openai.api-key=test-gateway-key",
                    "spring.ai.anthropic.api-key=test-anthropic-key");

    @Test
    void bothChatModelsExistWithoutAModelSelector() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OpenAiChatModel.class);
            assertThat(context).hasSingleBean(AnthropicChatModel.class);
        });
    }

    @Test
    void startsWithThePlaceholderKeyWhenNoGatewayKeyIsSet() {
        runner.withPropertyValues("spring.ai.openai.api-key=unset").run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(OpenAiChatModel.class));
    }

    @Test
    void theGatewayCallGoesToTheV1ChatCompletionsPathWithTheChosenModel() {
        stubFor(post(urlPathEqualTo("/api/v1/chat/completions")).willReturn(okJson("""
                {"id":"gen-1","object":"chat.completion","created":1790000000,"model":"anthropic/claude-haiku-4.5",
                 "choices":[{"index":0,"finish_reason":"stop",
                             "message":{"role":"assistant","content":"Hello from the gateway"}}],
                 "usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}
                """)));

        runner.run(context -> {
            OpenAiChatModel gateway = context.getBean(OpenAiChatModel.class);

            String text = gateway.call(new Prompt(List.of(new UserMessage("hi")),
                    OpenAiChatOptions.builder().model("anthropic/claude-haiku-4.5").build()))
                    .getResult().getOutput().getText();

            assertThat(text).isEqualTo("Hello from the gateway");
        });
        verify(postRequestedFor(urlPathEqualTo("/api/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-gateway-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("anthropic/claude-haiku-4.5"))));
    }
}
