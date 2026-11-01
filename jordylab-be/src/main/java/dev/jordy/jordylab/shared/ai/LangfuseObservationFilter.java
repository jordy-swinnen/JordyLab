package dev.jordy.jordylab.shared.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Shapes Spring AI's model spans for Langfuse. Names them after the action and provider ({@code generate-openai}), not
 * the model, so evaluators and dashboards keep matching when a model is swapped; the model stays an attribute. With
 * {@code langfuse.capture-content} on, also puts the prompt and answer on the span as
 * {@code langfuse.observation.input|output}, in the role/content shape Langfuse renders as a conversation (the Spring
 * AI {@code log-prompt}/{@code log-completion} switches, which write to the application log, stay off).
 */
@Slf4j
@RequiredArgsConstructor
final class LangfuseObservationFilter implements ObservationFilter {

    static final String INPUT = "langfuse.observation.input";
    static final String OUTPUT = "langfuse.observation.output";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean captureContent;

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof EmbeddingModelObservationContext) {
            context.setContextualName("embed-texts");

            return context;
        }
        if (!(context instanceof ChatModelObservationContext chat)) {
            return context;
        }
        context.setContextualName("generate-" + chat.getOperationMetadata().provider());
        if (!captureContent) {
            return context;
        }
        if (chat.getRequest() != null) {
            context.addHighCardinalityKeyValue(KeyValue.of(INPUT,
                    json(chat.getRequest().getInstructions().stream().map(LangfuseObservationFilter::turn)
                            .toList())));
        }
        if (chat.getResponse() != null && chat.getResponse().getResults() != null) {
            List<Turn> answers = chat.getResponse().getResults().stream()
                    .map(Generation::getOutput)
                    .filter(output -> output != null && StringUtils.hasText(output.getText()))
                    .map(output -> new Turn("assistant", output.getText()))
                    .toList();
            if (!answers.isEmpty()) {
                context.addHighCardinalityKeyValue(KeyValue.of(OUTPUT, json(answers)));
            }
        }

        return context;
    }

    static String messagesJson(List<Message> messages) {
        return json(messages.stream().map(LangfuseObservationFilter::turn).toList());
    }

    private static Turn turn(Message message) {
        return new Turn(message.getMessageType().getValue(), message.getText() == null ? "" : message.getText());
    }

    /** A record, not {@code Map.of}: its fields keep one order, so the JSON is the same on every run. */
    private record Turn(String role, String content) {
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("Could not render AI trace content as JSON: {}", exception.getMessage());

            return "[]";
        }
    }
}
