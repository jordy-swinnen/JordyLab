package dev.jordy.jordylab.shared.ai;

import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
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
import java.util.List;
import java.util.Locale;
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
 * {@code jordylab.ai.calls} (tags feature/provider/outcome/fallback).
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
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    // One virtual thread per call: cheap, so an SDK call that ignores the timeout's interrupt can't pile up platform
    // threads; future.cancel(true) still interrupts it.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiCallResult call(AiFeature feature, String systemPrompt, String userPrompt) {
        AiCallResult result = tryGateway(feature, systemPrompt, userPrompt);
        if (!result.success()) {
            String fallbackModel = properties.fallback().model();
            log.warn("AI call falling back: feature={}, gateway reason={}, fallback model={}", feature.key(),
                    result.failureReason(), fallbackModel);
            AiCallResult fallback = attempt(feature, FALLBACK, fallbackModel, fallbackChatModel,
                    AnthropicChatOptions.builder().model(fallbackModel).build(), systemPrompt, userPrompt);
            result = new AiCallResult(fallback.success(), feature, fallback.provider(), fallback.model(),
                    fallback.content(), fallback.failureReason(), true);
        }
        record(result);

        return result;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private AiCallResult tryGateway(AiFeature feature, String systemPrompt, String userPrompt) {
        String model = modelResolver.resolveModel(feature);
        if (!properties.gateway().configured()) {
            return AiCallResult.failure(feature, GATEWAY, model, ProviderFailureReason.AUTH_FAILED, false);
        }
        if (!providerHealthCache.isHealthy(GATEWAY)) {
            return AiCallResult.failure(feature, GATEWAY, model, ProviderFailureReason.UNREACHABLE, false);
        }

        return attempt(feature, GATEWAY, model, gatewayChatModel, OpenAiChatOptions.builder().model(model).build(),
                systemPrompt, userPrompt);
    }

    private AiCallResult attempt(AiFeature feature, String provider, String model, ChatModel chatModel,
            ChatOptions options, String systemPrompt, String userPrompt) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)), options);
        try {
            String content = extractText(callWithTimeout(chatModel, prompt));
            if (!StringUtils.hasText(content)) {
                log.error("AI call returned no text: feature={}, provider={}, model={}", feature.key(), provider,
                        model);

                return AiCallResult.failure(feature, provider, model, ProviderFailureReason.UNKNOWN, false);
            }
            providerHealthCache.recordSuccess(provider);
            log.info("AI call succeeded: feature={}, provider={}, model={}", feature.key(), provider, model);

            return AiCallResult.success(feature, provider, model, content, false);
        } catch (TimeoutException exception) {
            providerHealthCache.recordFailure(provider);
            log.error("AI call timed out: feature={}, provider={}, model={}", feature.key(), provider, model);

            return AiCallResult.failure(feature, provider, model, ProviderFailureReason.TIMEOUT, false);
        } catch (Exception exception) {
            ProviderFailureReason reason = reasonFor(exception);
            // A bad model id is a configuration problem, not an outage: the provider stays healthy for other features.
            if (reason != ProviderFailureReason.MODEL_NOT_FOUND) {
                providerHealthCache.recordFailure(provider);
            }
            log.error("AI call failed: feature={}, provider={}, model={}, reason={}", feature.key(), provider, model,
                    reason, exception);

            return AiCallResult.failure(feature, provider, model, reason, false);
        }
    }

    private ChatResponse callWithTimeout(ChatModel chatModel, Prompt prompt) throws Exception {
        Future<ChatResponse> future = executor.submit(() -> chatModel.call(prompt));
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
        eventPublisher.publishEvent(new AiCallCompleted(result.feature(), result.provider(), result.model(),
                result.success(), result.fallbackUsed(), result.failureReason(), Instant.now(clock)));
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
}
