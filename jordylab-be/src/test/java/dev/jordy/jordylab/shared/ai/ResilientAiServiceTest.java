package dev.jordy.jordylab.shared.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientAiServiceTest {

    private static final AiFeature FEATURE = AiFeature.GAMECATALOG_ENRICHMENT;
    private static final String GATEWAY_MODEL = "anthropic/claude-haiku-4.5";
    private static final String SYSTEM_PROMPT = "system";
    private static final String USER_PROMPT = "user";
    private static final String AI_OUTPUT = "AI output";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Prompt GATEWAY_PROMPT = new Prompt(
            List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(USER_PROMPT)),
            OpenAiChatOptions.builder().model(GATEWAY_MODEL).build());
    private static final Prompt FALLBACK_PROMPT = new Prompt(
            List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(USER_PROMPT)),
            AnthropicChatOptions.builder().model(AiPropertiesTestBuilder.FALLBACK_MODEL).build());

    @Mock
    private OpenAiChatModel gatewayChatModel;

    @Mock
    private AnthropicChatModel fallbackChatModel;

    @Mock
    private AiModelResolver modelResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ProviderHealthCache healthCache;
    private ResilientAiService service;

    private void serviceWith(AiProperties properties) {
        healthCache = new ProviderHealthCache(properties, CLOCK);
        service = new ResilientAiService(properties, healthCache, modelResolver, gatewayChatModel, fallbackChatModel,
                eventPublisher, meterRegistry, CLOCK);
    }

    @AfterEach
    void shutDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private AiCallCompleted publishedEvent() {
        ArgumentCaptor<AiCallCompleted> eventCaptor = ArgumentCaptor.forClass(AiCallCompleted.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        return eventCaptor.getValue();
    }

    private double calls(String provider, String outcome, boolean fallback) {
        return meterRegistry.counter("jordylab.ai.calls", "feature", FEATURE.key(), "provider", provider,
                "outcome", outcome, "fallback", String.valueOf(fallback)).count();
    }

    @Test
    void answersFromTheGatewayWithTheFeaturesOwnModel() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result).isEqualTo(AiCallResult.success(FEATURE, "openrouter", GATEWAY_MODEL, AI_OUTPUT, false));
        assertThat(publishedEvent()).isEqualTo(
                new AiCallCompleted(FEATURE, "openrouter", GATEWAY_MODEL, true, false, null, NOW));
        assertThat(calls("openrouter", "success", false)).isEqualTo(1.0);
        verifyNoInteractions(fallbackChatModel);
    }

    static Stream<Arguments> gatewayFailures() {
        return Stream.of(
                Arguments.of(new RuntimeException("connect", new ConnectException("refused")),
                        ProviderFailureReason.UNREACHABLE),
                Arguments.of(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", null, null,
                        null), ProviderFailureReason.RATE_LIMITED),
                Arguments.of(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "bad key", null, null, null),
                        ProviderFailureReason.AUTH_FAILED),
                Arguments.of(HttpClientErrorException.create(HttpStatus.PAYMENT_REQUIRED, "Insufficient credits", null,
                        null, null), ProviderFailureReason.INSUFFICIENT_CREDITS),
                Arguments.of(HttpClientErrorException.create("x is not a valid model ID", HttpStatus.BAD_REQUEST,
                        "Bad Request", null, null, null), ProviderFailureReason.MODEL_NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("gatewayFailures")
    void retriesOnceOnTheFallbackWhenTheGatewayFails(RuntimeException failure, ProviderFailureReason reason) {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenThrow(failure);
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(ResilientAiService.reasonFor(failure)).isEqualTo(reason);
        assertThat(result).isEqualTo(new AiCallResult(true, FEATURE, "anthropic",
                AiPropertiesTestBuilder.FALLBACK_MODEL, AI_OUTPUT, null, true));
        assertThat(calls("anthropic", "success", true)).isEqualTo(1.0);
    }

    @Test
    void retriesOnTheFallbackWhenTheGatewayTimesOut() {
        serviceWith(AiPropertiesTestBuilder.anAiProperties(30, 1, "gateway-key"));
        CountDownLatch neverReleased = new CountDownLatch(1);
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenAnswer(invocation -> {
            neverReleased.await();

            return answer("too late");
        });
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isTrue();
            softly.assertThat(result.provider()).isEqualTo("anthropic");
            softly.assertThat(result.fallbackUsed()).isTrue();
            softly.assertThat(healthCache.isHealthy("openrouter")).isFalse();
        });
    }

    @Test
    void anUnknownModelDoesNotMarkTheGatewayUnhealthyForOtherFeatures() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenThrow(HttpClientErrorException.create(
                "x is not a valid model ID", HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenReturn(answer(AI_OUTPUT));

        service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(healthCache.isHealthy("openrouter")).isTrue();
    }

    @Test
    void goesStraightToTheFallbackWithoutAGatewayKey() {
        serviceWith(AiPropertiesTestBuilder.anAiProperties(30, 120, ""));
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.provider()).isEqualTo("anthropic");
        verifyNoInteractions(gatewayChatModel);
    }

    @Test
    void skipsAnUnhealthyGateway() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        healthCache.recordFailure("openrouter");
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.fallbackUsed()).isTrue();
        verify(gatewayChatModel, never()).call(GATEWAY_PROMPT);
    }

    @Test
    void failsExplicitlyWhenBothProvidersFail() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", null, null, null));
        when(fallbackChatModel.call(FALLBACK_PROMPT)).thenThrow(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "bad key", null, null, null));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result).isEqualTo(AiCallResult.failure(FEATURE, "anthropic",
                AiPropertiesTestBuilder.FALLBACK_MODEL, ProviderFailureReason.AUTH_FAILED, true));
        assertThat(publishedEvent()).isEqualTo(new AiCallCompleted(FEATURE, "anthropic",
                AiPropertiesTestBuilder.FALLBACK_MODEL, false, true, ProviderFailureReason.AUTH_FAILED, NOW));
        assertThat(calls("anthropic", "failure", true)).isEqualTo(1.0);
    }

    @Test
    void joinsEveryGenerationThatCarriesText() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("")), new Generation(new AssistantMessage(AI_OUTPUT)))));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.content()).isEqualTo(AI_OUTPUT);
    }
}
