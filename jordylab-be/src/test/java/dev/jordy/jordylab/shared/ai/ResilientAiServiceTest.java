package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientAiServiceTest {

    private static final String MODULE_NAME = "fna";
    private static final String PROVIDER = "anthropic";
    private static final String MODEL = "claude-sonnet-5";
    private static final String SYSTEM_PROMPT = "system";
    private static final String USER_PROMPT = "user";
    private static final String AI_OUTPUT = "AI output";
    private static final int CALL_TIMEOUT_SECONDS = 2;

    private static final Prompt EXPECTED_PROMPT = new Prompt(List.of(
            new SystemMessage(SYSTEM_PROMPT),
            new UserMessage(USER_PROMPT)
    ));

    @Mock
    private AnthropicChatModel anthropicChatModel;

    @Mock
    private ProviderHealthCache providerHealthCache;

    @Mock
    private AiModuleConfig aiModuleConfig;

    private ResilientAiService service;

    @BeforeEach
    void setUp() {
        service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
    }

    @Test
    void returnsSuccessWithProviderAttribution() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(AI_OUTPUT)))));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isTrue();
            softly.assertThat(result.module()).isEqualTo(MODULE_NAME);
            softly.assertThat(result.provider()).isEqualTo(PROVIDER);
            softly.assertThat(result.model()).isEqualTo(MODEL);
            softly.assertThat(result.content()).isEqualTo(AI_OUTPUT);
            softly.assertThat(result.failureReason()).isNull();
        });
    }

    @Test
    void returnsFailureWhenHealthCacheUnhealthy() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(false);

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNREACHABLE);
        });
    }

    @Test
    void returnsFailureWhenModuleUnknown() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(null);

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNKNOWN);
        });
    }

    @Test
    void returnsFailureAndInvalidatesHealthOnRuntimeException() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT)).thenThrow(new RuntimeException("connection refused"));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNKNOWN);
        });
        verify(providerHealthCache).recordFailure(PROVIDER);
    }

    @Test
    void returnsUnreachableWhenCauseIsConnectException() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenThrow(new RuntimeException("wrapped", new ConnectException("refused")));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNREACHABLE);
    }

    @Test
    void returnsRateLimitedOnHttp429() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.RATE_LIMITED);
    }

    @Test
    void returnsAuthFailedOnHttp401() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.AUTH_FAILED);
    }

    @Test
    void returnsAuthFailedOnHttp403() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.AUTH_FAILED);
    }

    @Test
    void returnsFailureAndCancelsFutureOnTimeout() throws InterruptedException {
        AtomicBoolean workerInterrupted = new AtomicBoolean(false);
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(1);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT)).thenAnswer(invocation -> {
            try {
                Thread.sleep(1100);
            } catch (InterruptedException exception) {
                workerInterrupted.set(true);
                Thread.currentThread().interrupt();
            }

            return new ChatResponse(List.of(new Generation(new AssistantMessage("late"))));
        });

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.TIMEOUT);
        });

        long deadline = System.currentTimeMillis() + 500;
        while (!workerInterrupted.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(workerInterrupted.get()).isTrue();
    }

    @Test
    void consultsHealthCacheBeforeCall() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(false);

        service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        verify(providerHealthCache).isHealthy(PROVIDER);
    }

    @Test
    void neverThrows() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT)).thenThrow(new RuntimeException("boom"));

        AiCallResult result = service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result).isNotNull();
    }

    @Test
    void recordsSuccessInHealthCache() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(AI_OUTPUT)))));

        service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        verify(providerHealthCache).recordSuccess(PROVIDER);
    }

    @Test
    void recordsFailureInHealthCache() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT)).thenThrow(new RuntimeException("boom"));

        service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        verify(providerHealthCache).recordFailure(PROVIDER);
    }

    @Test
    void passesCorrectPromptContent() {
        when(aiModuleConfig.getModuleConfig(MODULE_NAME)).thenReturn(new AiModuleConfig.ModuleProvider(PROVIDER, MODEL));
        when(aiModuleConfig.callTimeoutSeconds()).thenReturn(CALL_TIMEOUT_SECONDS);
        when(providerHealthCache.isHealthy(PROVIDER)).thenReturn(true);
        when(anthropicChatModel.call(EXPECTED_PROMPT))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(AI_OUTPUT)))));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        service.call(MODULE_NAME, SYSTEM_PROMPT, USER_PROMPT);

        verify(anthropicChatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).isEqualTo(EXPECTED_PROMPT);
    }
}
