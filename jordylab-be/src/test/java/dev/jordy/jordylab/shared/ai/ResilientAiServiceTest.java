package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientAiServiceTest {

    @Mock
    private AnthropicChatModel anthropicChatModel;

    @Test
    void delegatesCallAndReturnsText() {
        ResilientAiService service = new ResilientAiService(anthropicChatModel, "claude-sonnet");

        Generation generation = new Generation(new AssistantMessage("AI output"));
        ChatResponse response = new ChatResponse(List.of(generation));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(anthropicChatModel.call(promptCaptor.capture())).thenReturn(response);

        String result = service.call("system", "user");

        assertThat(result).isEqualTo("AI output");
        assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);
    }

    @Test
    void wrapsExceptionInRuntimeException() {
        ResilientAiService service = new ResilientAiService(anthropicChatModel, "claude-sonnet");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(anthropicChatModel.call(promptCaptor.capture())).thenThrow(new RuntimeException("connection timeout"));

        assertThatThrownBy(() -> service.call("system", "user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AI call failed");
    }

    @Test
    void getLastUsedModelReturnsConfiguredModelName() {
        ResilientAiService service = new ResilientAiService(anthropicChatModel, "claude-sonnet-4-20250514");

        assertThat(service.getLastUsedModel()).isEqualTo("claude-sonnet-4-20250514");
    }
}
