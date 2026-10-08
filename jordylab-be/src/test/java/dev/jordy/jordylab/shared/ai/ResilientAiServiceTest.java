package dev.jordy.jordylab.shared.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
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
import static org.mockito.ArgumentMatchers.argThat;
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

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /** What a structured call must come back as: a typed, validated record. */
    record Pick(@NotBlank String title, @Min(1) int players) {
    }

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
    private EmbeddingModel embeddingModel;

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
                embeddingModel, VALIDATOR, eventPublisher, meterRegistry, CLOCK);
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
                new AiCallCompleted(FEATURE, "openrouter", GATEWAY_MODEL, true, false, null, NOW, GATEWAY_MODEL, null,
                        null));
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
                AiPropertiesTestBuilder.FALLBACK_MODEL, AI_OUTPUT, null, true, AiPropertiesTestBuilder.FALLBACK_MODEL, null, null));
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
                AiPropertiesTestBuilder.FALLBACK_MODEL, false, true, ProviderFailureReason.AUTH_FAILED, NOW, null, null, null));
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

    // ---------------------------------------------------------------- conversations, usage, the answering model

    @Test
    void sendsTheWholeConversationInOrder() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        List<Message> conversation = List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage("first question"),
                new AssistantMessage("first answer"), new UserMessage("second question"));
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(new Prompt(conversation, OpenAiChatOptions.builder().model(GATEWAY_MODEL).build())))
                .thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, conversation);

        assertThat(result.content()).isEqualTo(AI_OUTPUT);
    }

    @Test
    void reportsTheModelTheProviderActuallyUsedAndTheTokensItSpent() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn("jev-router");
        when(gatewayChatModel.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(USER_PROMPT)),
                OpenAiChatOptions.builder().model("jev-router").build())))
                .thenReturn(answerWithUsage(AI_OUTPUT, "anthropic/claude-sonnet-5", 120, 45));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.model()).isEqualTo("jev-router");
            softly.assertThat(result.answeredModel()).isEqualTo("anthropic/claude-sonnet-5");
            softly.assertThat(result.inputTokens()).isEqualTo(120);
            softly.assertThat(result.outputTokens()).isEqualTo(45);
            softly.assertThat(publishedEvent().answeredModel()).isEqualTo("anthropic/claude-sonnet-5");
            softly.assertThat(tokens("openrouter", "input")).isEqualTo(120.0);
            softly.assertThat(tokens("openrouter", "output")).isEqualTo(45.0);
        });
    }

    @Test
    void theAnsweringModelIsTheSelectedOneWhenTheProviderReportsNone() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(GATEWAY_PROMPT)).thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertSoftly(softly -> {
            softly.assertThat(result.answeredModel()).isEqualTo(GATEWAY_MODEL);
            softly.assertThat(result.inputTokens()).isNull();
            softly.assertThat(result.outputTokens()).isNull();
        });
    }

    @Test
    void aConfiguredTemperatureIsSentToBothProviders() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiPropertiesWithTemperature(FEATURE, 0.0));
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(USER_PROMPT)),
                OpenAiChatOptions.builder().model(GATEWAY_MODEL).temperature(0.0).build())))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow", null, null, null));
        when(fallbackChatModel.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(USER_PROMPT)),
                AnthropicChatOptions.builder().model(AiPropertiesTestBuilder.FALLBACK_MODEL).temperature(0.0).build())))
                .thenReturn(answer(AI_OUTPUT));

        AiCallResult result = service.call(FEATURE, SYSTEM_PROMPT, USER_PROMPT);

        assertThat(result.success()).isTrue();
    }

    // ---------------------------------------------------------------- structured output

    private static final String QUESTION = "Pick a game";
    private static final String VALID_PICK = "{\"title\":\"Overcooked\",\"players\":4}";

    private static List<Message> askToPick() {
        return List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(QUESTION));
    }

    /** The prompt a structured call sends first: the question with the schema appended to the last user message. */
    private static List<Message> withSchema() {
        return List.of(new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(QUESTION + "\n\n" + new BeanOutputConverter<>(Pick.class).getFormat()));
    }

    private static boolean isFirstAsk(Prompt prompt) {
        // null-safe: Mockito evaluates earlier matchers with null while a later stub is being registered
        return prompt != null && prompt.getInstructions().size() == 2;
    }

    private static boolean isRepairRequest(Prompt prompt) {
        return prompt != null && prompt.getInstructions().size() == 4;
    }

    @Test
    void aStructuredCallParsesAndValidatesTheAnswer() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(new Prompt(withSchema(), OpenAiChatOptions.builder().model(GATEWAY_MODEL).build())))
                .thenReturn(answer(VALID_PICK));

        StructuredOutput<Pick> output = service.callStructured(FEATURE, askToPick(), Pick.class);

        assertSoftly(softly -> {
            softly.assertThat(output.success()).isTrue();
            softly.assertThat(output.value()).isEqualTo(new Pick("Overcooked", 4));
            softly.assertThat(output.call().provider()).isEqualTo("openrouter");
        });
    }

    @Test
    void aStructuredAnswerInsideACodeFenceIsStillAccepted() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(new Prompt(withSchema(), OpenAiChatOptions.builder().model(GATEWAY_MODEL).build())))
                .thenReturn(answer("```json\n" + VALID_PICK + "\n```"));

        assertThat(service.callStructured(FEATURE, askToPick(), Pick.class).value())
                .isEqualTo(new Pick("Overcooked", 4));
    }

    @Test
    void anInvalidStructuredAnswerGetsOneRepairRequestQuotingTheProblem() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk)))
                .thenReturn(answerWithUsage("{\"title\":\"Overcooked\",\"players\":0}", GATEWAY_MODEL, 100, 10));
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest)))
                .thenReturn(answerWithUsage(VALID_PICK, GATEWAY_MODEL, 140, 12));

        StructuredOutput<Pick> output = service.callStructured(FEATURE, askToPick(), Pick.class);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(gatewayChatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());
        Prompt repair = promptCaptor.getAllValues().get(1);
        assertSoftly(softly -> {
            softly.assertThat(output.value()).isEqualTo(new Pick("Overcooked", 4));
            softly.assertThat(repair.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
            softly.assertThat(repair.getInstructions().get(3).getText()).contains("players")
                    .contains("must be greater than or equal to 1");
            softly.assertThat(output.call().inputTokens()).isEqualTo(240);
            softly.assertThat(output.call().outputTokens()).isEqualTo(22);
            softly.assertThat(output.call().fallbackUsed()).isFalse();
        });
        verifyNoInteractions(fallbackChatModel);
    }

    @Test
    void aStillInvalidAnswerFallsBackToTheOtherProvider() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer("not json at all"));
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest)))
                .thenReturn(answer("still not json"));
        when(fallbackChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer(VALID_PICK));

        StructuredOutput<Pick> output = service.callStructured(FEATURE, askToPick(), Pick.class);

        assertSoftly(softly -> {
            softly.assertThat(output.value()).isEqualTo(new Pick("Overcooked", 4));
            softly.assertThat(output.call().provider()).isEqualTo("anthropic");
            softly.assertThat(output.call().fallbackUsed()).isTrue();
        });
    }

    @Test
    void whenNoProviderAnswersInShapeTheCallFailsWithInvalidOutput() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer("nope"));
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest))).thenReturn(answer("nope"));
        when(fallbackChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer("nope"));
        when(fallbackChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest))).thenReturn(answer("nope"));

        StructuredOutput<Pick> output = service.callStructured(FEATURE, askToPick(), Pick.class);

        assertSoftly(softly -> {
            softly.assertThat(output.success()).isFalse();
            softly.assertThat(output.value()).isNull();
            softly.assertThat(output.call().failureReason()).isEqualTo(ProviderFailureReason.INVALID_OUTPUT);
            softly.assertThat(output.call().content()).isNull();
            softly.assertThat(publishedEvent().failureReason()).isEqualTo(ProviderFailureReason.INVALID_OUTPUT);
            softly.assertThat(healthCache.isHealthy("openrouter")).isTrue();
        });
    }

    @Test
    void aStructuredAnswerThatIsNeverLoggedOrReturnedAsContentWhenInvalid() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(modelResolver.resolveModel(FEATURE)).thenReturn(GATEWAY_MODEL);
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer("secret text"));
        when(gatewayChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest))).thenReturn(answer("secret text"));
        when(fallbackChatModel.call(argThat(ResilientAiServiceTest::isFirstAsk))).thenReturn(answer("secret text"));
        when(fallbackChatModel.call(argThat(ResilientAiServiceTest::isRepairRequest))).thenReturn(answer("secret text"));

        StructuredOutput<Pick> output = service.callStructured(FEATURE, askToPick(), Pick.class);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(gatewayChatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).getInstructions().get(3).getText()).doesNotContain("secret");
        assertThat(output.call().content()).isNull();
    }

    // ---------------------------------------------------------------- embeddings

    private static EmbeddingResponse embeddings(String model, Integer promptTokens, Embedding... embeddings) {
        return new EmbeddingResponse(List.of(embeddings), new EmbeddingResponseMetadata(model,
                new DefaultUsage(promptTokens, 0)));
    }

    @Test
    void embedsEveryTextAndReturnsTheVectorsInInputOrder() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(embeddingModel.embedForResponse(List.of("first", "second"))).thenReturn(embeddings(
                "openai/text-embedding-3-small", 9, new Embedding(new float[] {0.2f, 0.2f}, 1),
                new Embedding(new float[] {0.1f, 0.1f}, 0)));

        AiEmbeddingResult result = service.embed(List.of("first", "second"));

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isTrue();
            softly.assertThat(result.vectors()).hasSize(2);
            softly.assertThat(result.vectors().get(0)).containsExactly(0.1f, 0.1f);
            softly.assertThat(result.vectors().get(1)).containsExactly(0.2f, 0.2f);
            softly.assertThat(result.model()).isEqualTo(AiPropertiesTestBuilder.EMBEDDING_MODEL);
            softly.assertThat(result.inputTokens()).isEqualTo(9);
            softly.assertThat(tokensFor(AiFeature.GAMECATALOG_EMBEDDING, "input")).isEqualTo(9.0);
            softly.assertThat(publishedEvent().feature()).isEqualTo(AiFeature.GAMECATALOG_EMBEDDING);
            softly.assertThat(publishedEvent().success()).isTrue();
        });
        verifyNoInteractions(fallbackChatModel);
    }

    @Test
    void embeddingWithoutAGatewayKeyIsNotConfiguredAndNeverCallsTheProvider() {
        serviceWith(AiPropertiesTestBuilder.anAiProperties(30, 120, ""));

        AiEmbeddingResult result = service.embed(List.of("first"));

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.NOT_CONFIGURED);
            softly.assertThat(publishedEvent().failureReason()).isEqualTo(ProviderFailureReason.NOT_CONFIGURED);
        });
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void aFailingEmbeddingCallReportsTheReasonAndMarksTheGatewayUnhealthy() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(embeddingModel.embedForResponse(List.of("first"))).thenThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", null, null, null));

        AiEmbeddingResult result = service.embed(List.of("first"));

        assertSoftly(softly -> {
            softly.assertThat(result.success()).isFalse();
            softly.assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.RATE_LIMITED);
            softly.assertThat(healthCache.isHealthy("openrouter")).isFalse();
        });
    }

    @Test
    void aProviderThatReturnsTheWrongNumberOfVectorsIsAFailure() {
        serviceWith(AiPropertiesTestBuilder.aDefaultAiProperties());
        when(embeddingModel.embedForResponse(List.of("first", "second"))).thenReturn(
                embeddings("openai/text-embedding-3-small", 4, new Embedding(new float[] {0.1f}, 0)));

        AiEmbeddingResult result = service.embed(List.of("first", "second"));

        assertThat(result.failureReason()).isEqualTo(ProviderFailureReason.UNKNOWN);
    }

    private static ChatResponse answerWithUsage(String text, String model, int promptTokens, int completionTokens) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder().model(model)
                        .usage(new DefaultUsage(promptTokens, completionTokens)).build())
                .build();
    }

    private double tokens(String provider, String direction) {
        return tokensFor(FEATURE, direction, provider);
    }

    private double tokensFor(AiFeature feature, String direction) {
        return tokensFor(feature, direction, "openrouter");
    }

    private double tokensFor(AiFeature feature, String direction, String provider) {
        return meterRegistry.counter("jordylab.ai.tokens", "feature", feature.key(), "provider", provider,
                "direction", direction).count();
    }
}
