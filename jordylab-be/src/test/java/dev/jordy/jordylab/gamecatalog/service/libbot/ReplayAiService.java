package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import dev.jordy.jordylab.shared.ai.StructuredOutput;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.ai.chat.messages.Message;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Plays back recorded model outputs instead of calling a model: the interpretation for the interpret call, the answer for
 * the compose call. Every recorded output is parsed and validated exactly like a live one, so a stale recording fails
 * here. Remembers what each call was sent, so tests can assert on prompts and history.
 */
class ReplayAiService extends ResilientAiService {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode interpretation;
    private final JsonNode answer;
    private ProviderFailureReason failure;
    private final Map<AiFeature, Integer> calls = new EnumMap<>(AiFeature.class);
    private final Map<AiFeature, List<Message>> lastMessages = new EnumMap<>(AiFeature.class);

    ReplayAiService(JsonNode interpretation, JsonNode answer) {
        super(null, null, null, null, null, null, null, null, null, null);
        this.interpretation = interpretation;
        this.answer = answer;
    }

    /** Makes every structured call fail with the given reason, like a model that never answers in shape. */
    ReplayAiService failingWith(ProviderFailureReason reason) {
        this.failure = reason;

        return this;
    }

    int callsTo(AiFeature feature) {
        return calls.getOrDefault(feature, 0);
    }

    List<Message> lastMessagesTo(AiFeature feature) {
        return lastMessages.get(feature);
    }

    @Override
    public <T> StructuredOutput<T> callStructured(AiFeature feature, List<Message> messages, Class<T> type) {
        calls.merge(feature, 1, Integer::sum);
        lastMessages.put(feature, messages);
        if (failure != null) {
            return new StructuredOutput<>(null, AiCallResult.failure(feature, "replay", "replay-model", failure, true));
        }
        JsonNode recorded = feature == AiFeature.GAMECATALOG_CHAT_QUERY ? interpretation : answer;
        if (recorded == null || recorded.isNull()) {
            throw new AssertionError("No recorded output for " + feature.key() + ", yet LibBot called the model");
        }
        try {
            T value = mapper.treeToValue(recorded, type);
            if (!VALIDATOR.validate(value).isEmpty()) {
                throw new AssertionError("Recorded output for " + feature.key() + " is invalid: "
                        + VALIDATOR.validate(value));
            }

            return new StructuredOutput<>(value, AiCallResult.success(feature, "replay", "replay-model", "{}", false));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("Recorded output for " + feature.key() + " cannot be parsed", exception);
        }
    }
}
