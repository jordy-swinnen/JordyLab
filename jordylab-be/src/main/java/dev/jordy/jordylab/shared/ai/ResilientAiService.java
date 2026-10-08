package dev.jordy.jordylab.shared.ai;

import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import jakarta.validation.Validator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * The one way JordyLab calls an AI model (006 US4, FR-011–FR-013). Each {@link AiFeature} resolves its own model; the
 * call goes to the OpenRouter gateway first and, on any failure (unreachable, timeout, rate limit, auth, unknown model,
 * empty answer), is retried once on the Anthropic fallback. Never throws: the result says which provider and model
 * answered and whether the fallback was used. Every call publishes {@link AiCallCompleted} and counts
 * {@code jordylab.ai.calls} (tags feature/provider/outcome/fallback) and the tokens it used on
 * {@code jordylab.ai.tokens}.
 *
 * <p>{@link #callStructured} asks for a typed answer: the schema is appended to the prompt, the answer is parsed and
 * validated, an invalid one gets exactly one repair request, and one that is still invalid counts as a provider failure
 * ({@link ProviderFailureReason#INVALID_OUTPUT}) so the fallback model gets its turn. {@link #embed} turns texts into
 * vectors through the same gateway (no fallback: the search index must come from one model).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResilientAiService {

    static final String GATEWAY = "openrouter";
    static final String FALLBACK = "anthropic";

    private final AiProperties properties;
    private final ProviderHealthCache providerHealthCache;
    private final AiModelResolver modelResolver;
    private final OpenAiChatModel gatewayChatModel;
    private final AnthropicChatModel fallbackChatModel;
    private final EmbeddingModel embeddingModel;
    private final Validator validator;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    // One virtual thread per call: cheap, so an SDK call that ignores the timeout's interrupt can't pile up platform
    // threads; future.cancel(true) still interrupts it.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiCallResult call(AiFeature feature, String systemPrompt, String userPrompt) {
        return call(feature, List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
    }

    /** A call with a whole conversation: system message first, then the earlier turns, then the new question. */
    public AiCallResult call(AiFeature feature, List<Message> messages) {
        return execute(feature, messages, null).call();
    }

    /**
     * A call whose answer must be a {@code type}: the JSON schema is appended to the last user message, the answer is
     * parsed and checked with Jakarta Validation, and an invalid one is repaired once before the fallback is tried.
     */
    public <T> StructuredOutput<T> callStructured(AiFeature feature, List<Message> messages, Class<T> type) {
        StructuredVerifier<T> verifier = new StructuredVerifier<>(type, validator);
        Execution<T> execution = execute(feature, verifier.withSchema(messages), verifier);

        return new StructuredOutput<>(execution.value(), execution.call());
    }

    /**
     * Embeds every text in one gateway call, in input order. Callers batch (the provider caps the input size) and treat
     * {@link ProviderFailureReason#NOT_CONFIGURED} as "no semantic search right now", not as an error.
     */
    public AiEmbeddingResult embed(List<String> texts) {
        String model = properties.embeddingModel();
        AiEmbeddingResult result = embedOnce(texts, model);
        meterRegistry.counter("jordylab.ai.calls",
                "feature", AiFeature.GAMECATALOG_EMBEDDING.key(),
                "provider", GATEWAY,
                "outcome", result.success() ? "success" : "failure",
                "fallback", "false").increment();
        countTokens(AiFeature.GAMECATALOG_EMBEDDING, GATEWAY, result.inputTokens(), null);
        eventPublisher.publishEvent(new AiCallCompleted(AiFeature.GAMECATALOG_EMBEDDING, GATEWAY, model,
                result.success(), false, result.failureReason(), Instant.now(clock), result.answeredModel(),
                result.inputTokens(), null));

        return result;
    }

    /** The configured embedding model id, so callers can tell which model made a stored vector. */
    public String embeddingModelName() {
        return properties.embeddingModel();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private <T> Execution<T> execute(AiFeature feature, List<Message> messages, StructuredVerifier<T> verifier) {
        Execution<T> execution = tryGateway(feature, messages, verifier);
        if (!execution.call().success()) {
            String fallbackModel = properties.fallback().model();
            log.warn("AI call falling back: feature={}, gateway reason={}, fallback model={}", feature.key(),
                    execution.call().failureReason(), fallbackModel);
            AnthropicChatOptions.Builder options = AnthropicChatOptions.builder().model(fallbackModel);
            Execution<T> fallback = attempt(feature, FALLBACK, fallbackModel, fallbackChatModel, options.build(),
                    messages, verifier);
            execution = new Execution<>(fallback.call().withFallbackUsed(), fallback.value());
        }
        record(execution.call());

        return execution;
    }

    private <T> Execution<T> tryGateway(AiFeature feature, List<Message> messages, StructuredVerifier<T> verifier) {
        String model = modelResolver.resolveModel(feature);
        if (!properties.gateway().configured()) {
            return new Execution<>(
                    AiCallResult.failure(feature, GATEWAY, model, ProviderFailureReason.AUTH_FAILED, false), null);
        }
        if (!providerHealthCache.isHealthy(GATEWAY)) {
            return new Execution<>(
                    AiCallResult.failure(feature, GATEWAY, model, ProviderFailureReason.UNREACHABLE, false), null);
        }
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(model);
        properties.temperature(feature).ifPresent(options::temperature);

        return attempt(feature, GATEWAY, model, gatewayChatModel, options.build(), messages, verifier);
    }

    private <T> Execution<T> attempt(AiFeature feature, String provider, String model, ChatModel chatModel,
            ChatOptions options, List<Message> messages, StructuredVerifier<T> verifier) {
        AiCallResult first = callOnce(feature, provider, model, chatModel, options, messages);
        if (verifier == null || !first.success()) {
            return new Execution<>(first, null);
        }
        Verdict<T> verdict = verifier.verify(first.content());
        if (verdict.valid()) {
            return new Execution<>(first, verdict.value());
        }
        log.warn("AI answer was not valid, asking once for a repair: feature={}, provider={}, model={}, problem={}",
                feature.key(), provider, model, verdict.problem());
        List<Message> repairMessages = new ArrayList<>(messages);
        repairMessages.add(new AssistantMessage(first.content()));
        repairMessages.add(new UserMessage(verifier.repairRequest(verdict.problem())));
        AiCallResult second = callOnce(feature, provider, model, chatModel, options, repairMessages);
        if (!second.success()) {
            return new Execution<>(second.plusUsageOf(first), null);
        }
        AiCallResult combined = second.plusUsageOf(first);
        Verdict<T> repaired = verifier.verify(second.content());
        if (repaired.valid()) {
            return new Execution<>(combined, repaired.value());
        }
        log.error("AI answer still not valid after a repair: feature={}, provider={}, model={}, problem={}",
                feature.key(), provider, model, repaired.problem());

        return new Execution<>(combined.asInvalidOutput(), null);
    }

    private AiCallResult callOnce(AiFeature feature, String provider, String model, ChatModel chatModel,
            ChatOptions options, List<Message> messages) {
        Prompt prompt = new Prompt(messages, options);
        try {
            ChatResponse response = callWithTimeout(() -> chatModel.call(prompt));
            String content = extractText(response);
            if (!StringUtils.hasText(content)) {
                log.error("AI call returned no text: feature={}, provider={}, model={}", feature.key(), provider,
                        model);

                return AiCallResult.failure(feature, provider, model, ProviderFailureReason.UNKNOWN, false);
            }
            providerHealthCache.recordSuccess(provider);
            log.info("AI call succeeded: feature={}, provider={}, model={}", feature.key(), provider, model);
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();

            return AiCallResult.success(feature, provider, model, answeredModel(response, model), content,
                    tokens(usage == null ? null : usage.getPromptTokens()),
                    tokens(usage == null ? null : usage.getCompletionTokens()));
        } catch (Exception exception) {
            ProviderFailureReason reason = classifyFailure(feature, provider, model, exception);

            return AiCallResult.failure(feature, provider, model, reason, false);
        }
    }

    private AiEmbeddingResult embedOnce(List<String> texts, String model) {
        if (!properties.gateway().configured()) {
            return AiEmbeddingResult.failure(GATEWAY, model, ProviderFailureReason.NOT_CONFIGURED);
        }
        if (!providerHealthCache.isHealthy(GATEWAY)) {
            return AiEmbeddingResult.failure(GATEWAY, model, ProviderFailureReason.UNREACHABLE);
        }
        try {
            EmbeddingResponse response = callWithTimeout(() -> embeddingModel.embedForResponse(texts));
            List<float[]> vectors = response.getResults().stream()
                    .sorted(Comparator.comparing(Embedding::getIndex))
                    .map(Embedding::getOutput)
                    .toList();
            if (vectors.size() != texts.size()) {
                log.error("Embedding call returned {} vector(s) for {} text(s): model={}", vectors.size(), texts.size(),
                        model);

                return AiEmbeddingResult.failure(GATEWAY, model, ProviderFailureReason.UNKNOWN);
            }
            providerHealthCache.recordSuccess(GATEWAY);
            String reported = response.getMetadata() == null ? null : response.getMetadata().getModel();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();

            return new AiEmbeddingResult(true, vectors, GATEWAY, model,
                    StringUtils.hasText(reported) ? reported : model,
                    tokens(usage == null ? null : usage.getPromptTokens()), null);
        } catch (Exception exception) {
            return AiEmbeddingResult.failure(GATEWAY, model,
                    classifyFailure(AiFeature.GAMECATALOG_EMBEDDING, GATEWAY, model, exception));
        }
    }

    private ProviderFailureReason classifyFailure(AiFeature feature, String provider, String model,
            Exception exception) {
        if (exception instanceof TimeoutException) {
            providerHealthCache.recordFailure(provider);
            log.error("AI call timed out: feature={}, provider={}, model={}", feature.key(), provider, model);

            return ProviderFailureReason.TIMEOUT;
        }
        ProviderFailureReason reason = reasonFor(exception);
        // A bad model id is a configuration problem, not an outage: the provider stays healthy for other features.
        if (reason != ProviderFailureReason.MODEL_NOT_FOUND) {
            providerHealthCache.recordFailure(provider);
        }
        log.error("AI call failed: feature={}, provider={}, model={}, reason={}", feature.key(), provider, model, reason,
                exception);

        return reason;
    }

    private <T> T callWithTimeout(Callable<T> task) throws Exception {
        Future<T> future = executor.submit(task);
        try {
            return future.get(properties.callTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);

            throw exception;
        }
    }

    private void record(AiCallResult result) {
        meterRegistry.counter("jordylab.ai.calls",
                "feature", result.feature().key(),
                "provider", result.provider(),
                "outcome", result.success() ? "success" : "failure",
                "fallback", String.valueOf(result.fallbackUsed())).increment();
        countTokens(result.feature(), result.provider(), result.inputTokens(), result.outputTokens());
        eventPublisher.publishEvent(new AiCallCompleted(result.feature(), result.provider(), result.model(),
                result.success(), result.fallbackUsed(), result.failureReason(), Instant.now(clock),
                result.answeredModel(), result.inputTokens(), result.outputTokens()));
    }

    private void countTokens(AiFeature feature, String provider, Integer inputTokens, Integer outputTokens) {
        if (inputTokens != null) {
            meterRegistry.counter("jordylab.ai.tokens", "feature", feature.key(), "provider", provider,
                    "direction", "input").increment(inputTokens);
        }
        if (outputTokens != null) {
            meterRegistry.counter("jordylab.ai.tokens", "feature", feature.key(), "provider", provider,
                    "direction", "output").increment(outputTokens);
        }
    }

    /** The model the provider says it used (a router picks one); the requested model when it says nothing. */
    private static String answeredModel(ChatResponse response, String requestedModel) {
        String reported = response.getMetadata() == null ? null : response.getMetadata().getModel();

        return StringUtils.hasText(reported) ? reported : requestedModel;
    }

    /** A provider that reports no usage shows zeros: that means unknown, not free. */
    private static Integer tokens(Integer reported) {
        return reported == null || reported <= 0 ? null : reported;
    }

    // With thinking enabled the model returns a text-less thinking generation before the answer,
    // so getResult() (the first generation) is blank — join every generation that carries text.
    private String extractText(ChatResponse response) {
        return response.getResults().stream()
                .map(generation -> generation.getOutput().getText())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    static ProviderFailureReason reasonFor(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnknownHostException || current instanceof ConnectException) {
                return ProviderFailureReason.UNREACHABLE;
            }
            Integer status = statusOf(current);
            if (status != null) {
                return reasonForStatus(status, current.getMessage());
            }
            current = current.getCause();
        }

        return ProviderFailureReason.UNKNOWN;
    }

    private static Integer statusOf(Throwable exception) {
        if (exception instanceof OpenAIServiceException openAi) {
            return openAi.statusCode();
        }
        if (exception instanceof AnthropicServiceException anthropic) {
            return anthropic.statusCode();
        }
        if (exception instanceof HttpStatusCodeException http) {
            return http.getStatusCode().value();
        }

        return null;
    }

    private static ProviderFailureReason reasonForStatus(int status, String message) {
        boolean unknownModel = status == 404 || (status == 400 && message != null
                && message.toLowerCase(Locale.ROOT).contains("not a valid model"));
        if (unknownModel) {
            return ProviderFailureReason.MODEL_NOT_FOUND;
        }
        if (status == 402) {
            return ProviderFailureReason.INSUFFICIENT_CREDITS;
        }
        if (status == 429) {
            return ProviderFailureReason.RATE_LIMITED;
        }
        if (status == 401 || status == 403) {
            return ProviderFailureReason.AUTH_FAILED;
        }

        return ProviderFailureReason.UNKNOWN;
    }

    private record Execution<T>(AiCallResult call, T value) {
    }

    private record Verdict<T>(T value, String problem) {

        boolean valid() {
            return value != null;
        }
    }

    /**
     * Turns the model's text into a validated {@code T}. Problems are described without echoing the model's text, so they
     * are safe to log and to send back in the repair request.
     */
    private static final class StructuredVerifier<T> {

        private static final int MAX_PROBLEM_LENGTH = 400;

        private final Class<T> type;
        private final Validator validator;
        private final BeanOutputConverter<T> converter;

        private StructuredVerifier(Class<T> type, Validator validator) {
            this.type = type;
            this.validator = validator;
            this.converter = new BeanOutputConverter<>(type);
        }

        List<Message> withSchema(List<Message> messages) {
            List<Message> prepared = new ArrayList<>(messages);
            int lastIndex = prepared.size() - 1;
            if (lastIndex >= 0 && prepared.get(lastIndex) instanceof UserMessage lastUserMessage) {
                prepared.set(lastIndex, new UserMessage(lastUserMessage.getText() + "\n\n" + converter.getFormat()));
            } else {
                prepared.add(new UserMessage(converter.getFormat()));
            }

            return prepared;
        }

        Verdict<T> verify(String content) {
            T parsed;
            try {
                parsed = converter.convert(content);
            } catch (RuntimeException exception) {
                return new Verdict<>(null, "the reply was not valid JSON for " + type.getSimpleName() + " ("
                        + exception.getClass().getSimpleName() + ")");
            }
            if (parsed == null) {
                return new Verdict<>(null, "the reply was empty");
            }
            List<String> violations = validator.validate(parsed).stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .toList();
            if (!violations.isEmpty()) {
                return new Verdict<>(null, String.join("; ", violations));
            }

            return new Verdict<>(parsed, null);
        }

        String repairRequest(String problem) {
            String shortened = problem.length() > MAX_PROBLEM_LENGTH ? problem.substring(0, MAX_PROBLEM_LENGTH) : problem;

            return "Your previous reply was not usable: " + shortened + ". Reply again with ONLY the JSON object that "
                    + "matches the schema, with no commentary and no code fences.";
        }
    }
}
