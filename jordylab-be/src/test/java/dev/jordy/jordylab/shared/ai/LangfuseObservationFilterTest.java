package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseObservationFilterTest {

    private static ChatModelObservationContext chatContext() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(new Prompt(List.of(new SystemMessage("be terse"), new UserMessage("hello")),
                        ChatOptions.builder().model("some/model").build()))
                .provider("openai")
                .build();
        context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("hi")))));

        return context;
    }

    @Test
    void namesTheModelSpanAfterTheProviderNotTheModel() {
        ChatModelObservationContext context = chatContext();

        new LangfuseObservationFilter(false).map(context);

        assertThat(context.getContextualName()).isEqualTo("generate-openai");
    }

    @Test
    void keepsPromptAndAnswerOffTheSpanUnlessCaptureIsOn() {
        ChatModelObservationContext context = chatContext();

        new LangfuseObservationFilter(false).map(context);

        assertThat(context.getHighCardinalityKeyValues().stream().map(keyValue -> keyValue.getKey()))
                .doesNotContain(LangfuseObservationFilter.INPUT, LangfuseObservationFilter.OUTPUT);
    }

    @Test
    void putsPromptAndAnswerOnTheSpanAsRoleAndContentWhenCaptureIsOn() {
        ChatModelObservationContext context = chatContext();

        new LangfuseObservationFilter(true).map(context);

        assertThat(context.getHighCardinalityKeyValue(LangfuseObservationFilter.INPUT).getValue())
                .contains("\"role\":\"system\"", "\"content\":\"hello\"");
        assertThat(context.getHighCardinalityKeyValue(LangfuseObservationFilter.OUTPUT).getValue())
                .isEqualTo("[{\"role\":\"assistant\",\"content\":\"hi\"}]");
    }
}
