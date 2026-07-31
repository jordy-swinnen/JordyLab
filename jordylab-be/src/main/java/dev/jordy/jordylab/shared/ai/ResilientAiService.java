package dev.jordy.jordylab.shared.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class ResilientAiService {

    private final AnthropicChatModel anthropicChatModel;
    private final String modelName;

    public ResilientAiService(
            AnthropicChatModel anthropicChatModel,
            @Value("${spring.ai.anthropic.chat.options.model}") String modelName
    ) {
        this.anthropicChatModel = anthropicChatModel;
        this.modelName = modelName;
    }

    public String call(String systemPrompt, String userPrompt) {
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            ChatResponse response = anthropicChatModel.call(prompt);

            return Objects.requireNonNull(response.getResult()).getOutput().getText();
        } catch (Exception exception) {
            log.error("AI call failed: {}", exception.getMessage(), exception);

            throw new RuntimeException("AI call failed", exception);
        }
    }

    public String getLastUsedModel() {
        return modelName;
    }
}
