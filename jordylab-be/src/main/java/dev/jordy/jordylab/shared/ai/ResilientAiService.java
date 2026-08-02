package dev.jordy.jordylab.shared.ai;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResilientAiService {

    private final AiModuleConfig aiModuleConfig;
    private final ProviderHealthCache providerHealthCache;
    private final AnthropicChatModel anthropicChatModel;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiCallResult call(String moduleName, String systemPrompt, String userPrompt) {
        AiModuleConfig.ModuleProvider config = aiModuleConfig.getModuleConfig(moduleName);
        if (config == null) {
            log.warn("No AI provider configured for module: {}", moduleName);

            return AiCallResult.failure(moduleName, "unknown", "unknown", ProviderFailureReason.UNKNOWN);
        }

        if (!providerHealthCache.isHealthy(config.provider())) {
            log.warn("Provider {} is unhealthy for module {}", config.provider(), moduleName);

            return AiCallResult.failure(moduleName, config.provider(), config.model(), ProviderFailureReason.UNREACHABLE);
        }

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));
            ChatResponse response = callWithTimeout(prompt);
            String content = Objects.requireNonNull(response.getResult()).getOutput().getText();

            providerHealthCache.recordSuccess(config.provider());
            log.info("AI call succeeded: module={}, provider={}, model={}", moduleName, config.provider(), config.model());

            return AiCallResult.success(moduleName, config.provider(), config.model(), content);
        } catch (TimeoutException exception) {
            providerHealthCache.recordFailure(config.provider());
            log.error("AI call timed out: module={}, provider={}", moduleName, config.provider(), exception);

            return AiCallResult.failure(moduleName, config.provider(), config.model(), ProviderFailureReason.TIMEOUT);
        } catch (Exception exception) {
            providerHealthCache.recordFailure(config.provider());
            ProviderFailureReason reason = mapExceptionToReason(exception);
            log.error("AI call failed: module={}, provider={}, reason={}", moduleName, config.provider(), reason, exception);

            return AiCallResult.failure(moduleName, config.provider(), config.model(), reason);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private ChatResponse callWithTimeout(Prompt prompt) throws Exception {
        Future<ChatResponse> future = executor.submit(() -> anthropicChatModel.call(prompt));

        try {
            return future.get(aiModuleConfig.callTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);

            throw exception;
        }
    }

    private ProviderFailureReason mapExceptionToReason(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnknownHostException || current instanceof ConnectException) {
                return ProviderFailureReason.UNREACHABLE;
            }

            if (current instanceof HttpStatusCodeException httpException) {
                int statusCode = httpException.getStatusCode().value();
                if (statusCode == 429) {
                    return ProviderFailureReason.RATE_LIMITED;
                }
                if (statusCode == 401 || statusCode == 403) {
                    return ProviderFailureReason.AUTH_FAILED;
                }
            }

            current = current.getCause();
        }

        return ProviderFailureReason.UNKNOWN;
    }
}
