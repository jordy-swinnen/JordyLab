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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientAiServiceTest {

    @Mock
    private AnthropicChatModel anthropicChatModel;

    @Mock
    private ProviderHealthCache providerHealthCache;

    @Mock
    private AiModuleConfig aiModuleConfig;

    @Test
    void returnsSuccessWithProviderAttribution() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("AI output")))));

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result.success()).isTrue();
        assertThat(result.module()).isEqualTo("fna");
        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.model()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(result.content()).isEqualTo("AI output");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void returnsFailureWhenHealthCacheUnhealthy() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(false);

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNREACHABLE);
    }

    @Test
    void returnsFailureWhenModuleUnknown() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(null);

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNKNOWN);
    }

    @Test
    void returnsFailureAndInvalidatesHealthOnRuntimeException() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNKNOWN);
    }

    @Test
    void returnsFailureAndInvalidatesHealthOnTimeout() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(1);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(2000);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("late"))));
        });

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.TIMEOUT);
    }

    @Test
    void consultsHealthCacheBeforeCall() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("AI output")))));

        service.call("fna", "system", "user");

        assertThat(providerHealthCache.isHealthy("anthropic")).isTrue();
    }

    @Test
    void neverThrows() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        AiCallResult result = service.call("fna", "system", "user");

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
    }

    @Test
    void recordsSuccessInHealthCache() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("AI output")))));

        service.call("fna", "system", "user");

        verify(providerHealthCache).recordSuccess("anthropic");
    }

    @Test
    void recordsFailureInHealthCache() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        service.call("fna", "system", "user");

        verify(providerHealthCache).recordFailure("anthropic");
    }

    @Test
    void passesCorrectPromptContent() {
        ResilientAiService service = new ResilientAiService(aiModuleConfig, providerHealthCache, anthropicChatModel);
        when(aiModuleConfig.getModuleConfig("fna")).thenReturn(new AiModuleConfig.ModuleProvider("anthropic", "claude-sonnet-4-20250514"));
        when(aiModuleConfig.healthCheckTimeoutSeconds()).thenReturn(2);
        when(providerHealthCache.isHealthy("anthropic")).thenReturn(true);
        when(anthropicChatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("AI output")))));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        service.call("fna", "system", "user");

        verify(anthropicChatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);
    }
}
