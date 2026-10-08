package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenCase;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenFile;
import dev.jordy.jordylab.gamecatalog.service.DescriptionQualityValidator;
import dev.jordy.jordylab.shared.ai.AiCallCompleted;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live tier of the golden set (spec 013 FR-018, research A11): the same questions as {@link GoldenReplayTest}, but the
 * interpret and compose steps are answered by the real models through the real {@link ResilientAiService}. Run on demand
 * with {@code ./gradlew goldenLive} (needs OPENROUTER_API_KEY; ANTHROPIC_API_KEY enables the fallback). It prints the pass
 * rate per tag and the token cost, and fails below 90 % overall or below 100 % on the hard rules: out-of-scope questions
 * never carry references, and no reference is ever missing from the answer text. Keys and model text are never printed.
 */
@Tag("golden-live")
class GoldenLiveTest {

    private static final double REQUIRED_OVERALL = 0.90;
    private static final GoldenFile FILE = GoldenFixtures.load();

    @Configuration
    @ComponentScan(basePackageClasses = ResilientAiService.class)
    static class LiveConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /** Adds up the tokens of every AI call, for the cost line of the report. */
    record TokenCounter(AtomicInteger input, AtomicInteger output) {

        @EventListener
        void on(AiCallCompleted completed) {
            input.addAndGet(completed.inputTokens() == null ? 0 : completed.inputTokens());
            output.addAndGet(completed.outputTokens() == null ? 0 : completed.outputTokens());
        }
    }

    record Verdict(GoldenCase goldenCase, boolean passed, String failure) {
    }

    @Test
    void theRealModelsAnswerTheGoldenQuestions() {
        Assumptions.assumeTrue(StringUtils.hasText(System.getenv("OPENROUTER_API_KEY")),
                "OPENROUTER_API_KEY is not set: the live tier needs the gateway");
        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();
        List<Verdict> verdicts = new ArrayList<>();

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, ChatObservationAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class,
                        AnthropicChatAutoConfiguration.class))
                .withUserConfiguration(LiveConfiguration.class)
                .withBean(TokenCounter.class, () -> new TokenCounter(inputTokens, outputTokens))
                .run(context -> {
                    ResilientAiService aiService = context.getBean(ResilientAiService.class);
                    for (GoldenCase goldenCase : FILE.cases()) {
                        verdicts.add(runCase(goldenCase, aiService));
                    }
                });

        report(verdicts, inputTokens.get(), outputTokens.get());
        double passRate = verdicts.stream().filter(Verdict::passed).count() / (double) verdicts.size();
        List<Verdict> hardFailures = verdicts.stream()
                .filter(verdict -> !verdict.passed() && isHardRule(verdict.goldenCase())).toList();
        assertThat(hardFailures).as("hard rules (100 %)").isEmpty();
        assertThat(passRate).as("overall pass rate").isGreaterThanOrEqualTo(REQUIRED_OVERALL);
    }

    /**
     * The same quality bar for what the real model writes: for each sample game the description is generated with the real
     * enrichment prompt and judged by {@link DescriptionQualityValidator}. At least 90 % must pass on the first try.
     */
    @Test
    void theRealModelWritesStoreBlurbsThatPassTheQualityValidator() throws java.io.IOException {
        Assumptions.assumeTrue(StringUtils.hasText(System.getenv("OPENROUTER_API_KEY")),
                "OPENROUTER_API_KEY is not set: the live tier needs the gateway");
        String systemPrompt = new org.springframework.core.io.ClassPathResource("prompts/gamecatalog/enrichment.st")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<GoldenFixtures.DescriptionSample> samples = GoldenFixtures.loadDescriptionSamples().stream()
                .filter(GoldenFixtures.DescriptionSample::expectAccepted).toList();
        List<String> failures = new ArrayList<>();

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, ChatObservationAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class,
                        AnthropicChatAutoConfiguration.class))
                .withUserConfiguration(LiveConfiguration.class)
                .run(context -> {
                    ResilientAiService aiService = context.getBean(ResilientAiService.class);
                    for (GoldenFixtures.DescriptionSample sample : samples) {
                        String prompt = "Game: " + sample.title() + "\nPlatform: " + sample.platform()
                                + "\nKnown release year: " + sample.releaseYear();
                        AiCallResult result = aiService.call(AiFeature.GAMECATALOG_ENRICHMENT,
                                systemPrompt, prompt);
                        String text = result.success() ? descriptionOf(result.content()) : null;
                        DescriptionQualityValidator.Verdict verdict = new DescriptionQualityValidator()
                                .check(text, sample.releaseYear());
                        if (!verdict.accepted()) {
                            failures.add(sample.title() + ": " + verdict.reason());
                        }
                    }
                });

        System.out.printf("Description quality: %d of %d passed first time%s%n", samples.size() - failures.size(),
                samples.size(), failures.isEmpty() ? "" : " (" + String.join("; ", failures) + ")");
        assertThat((samples.size() - failures.size()) / (double) samples.size()).isGreaterThanOrEqualTo(REQUIRED_OVERALL);
    }

    private static String descriptionOf(String content) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(content.trim()).path("description").asText(null);
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    private Verdict runCase(GoldenCase goldenCase, ResilientAiService aiService) {
        GoldenHarness harness = new GoldenHarness(FILE);
        try {
            LibBotService service = harness.serviceFor(goldenCase, aiService, harness.storeFor(goldenCase));
            LibBotResult result = harness.ask(service, goldenCase);
            SoftAssertions softly = new SoftAssertions();
            harness.check(goldenCase, result, softly);
            List<String> problems = softly.errorsCollected().stream().map(Throwable::getMessage).toList();

            return new Verdict(goldenCase, problems.isEmpty(), String.join(" | ", problems));
        } catch (RuntimeException exception) {
            return new Verdict(goldenCase, false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private boolean isHardRule(GoldenCase goldenCase) {
        return goldenCase.expect().outcome() == LibBotOutcome.OUT_OF_SCOPE || goldenCase.tags().contains("owner-reported");
    }

    private void report(List<Verdict> verdicts, int inputTokens, int outputTokens) {
        Map<String, int[]> perTag = new LinkedHashMap<>();
        for (Verdict verdict : verdicts) {
            for (String tag : verdict.goldenCase().tags()) {
                int[] counts = perTag.computeIfAbsent(tag, key -> new int[2]);
                counts[0] += verdict.passed() ? 1 : 0;
                counts[1]++;
            }
        }
        System.out.println("=== LibBot golden live tier ===");
        verdicts.forEach(verdict -> System.out.println((verdict.passed() ? "PASS  " : "FAIL  ")
                + verdict.goldenCase().name() + (verdict.passed() ? "" : "\n      " + verdict.failure())));
        perTag.forEach((tag, counts) -> System.out.printf("tag %-16s %d/%d%n", tag, counts[0], counts[1]));
        System.out.printf("overall %d/%d, tokens in %d out %d%n", verdicts.stream().filter(Verdict::passed).count(),
                verdicts.size(), inputTokens, outputTokens);
    }
}
