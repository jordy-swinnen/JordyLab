package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.service.DescriptionQualityValidator;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenCase;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenFile;
import dev.jordy.jordylab.shared.ai.AiFeature;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The replay tier of the golden set (spec 013 FR-018): every question runs through the real pipeline (prompts, deriver,
 * retrieval rules, validator, messages, memory) against recorded model outputs, so the Java seam is tested
 * deterministically in CI. The live tier ({@code GoldenLiveTest}) asks the real models the same questions.
 */
class GoldenReplayTest {

    private static final GoldenFile FILE = GoldenFixtures.load();

    static Stream<GoldenCase> goldenCases() {
        return FILE.cases().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void behavesAsExpected(GoldenCase goldenCase) {
        GoldenHarness harness = new GoldenHarness(FILE);
        ReplayAiService aiService = new ReplayAiService(goldenCase.recorded().interpretation(),
                goldenCase.recorded().answer());
        LibBotService service = harness.serviceFor(goldenCase, aiService, harness.storeFor(goldenCase));

        LibBotResult result = harness.ask(service, goldenCase);

        SoftAssertions.assertSoftly(softly -> {
            harness.check(goldenCase, result, softly);
            softly.assertThat(aiService.callsTo(AiFeature.GAMECATALOG_CHAT_QUERY)).as("one interpret call").isEqualTo(1);
            softly.assertThat(aiService.callsTo(AiFeature.GAMECATALOG_CHAT_ANSWER)).as("compose calls")
                    .isEqualTo(goldenCase.expect().composeCalls() != null ? goldenCase.expect().composeCalls()
                            : goldenCase.recorded().answer() == null || goldenCase.recorded().answer().isNull() ? 0 : 1);
        });
    }

    static Stream<GoldenFixtures.DescriptionSample> descriptionSamples() {
        return GoldenFixtures.loadDescriptionSamples().stream();
    }

    @ParameterizedTest(name = "description of {0}")
    @MethodSource("descriptionSamples")
    void theQualityValidatorJudgesTheRecordedDescriptionsAsTheOwnerWould(GoldenFixtures.DescriptionSample sample) {
        DescriptionQualityValidator.Verdict verdict = new DescriptionQualityValidator()
                .check(sample.recorded(), sample.releaseYear());

        assertThat(verdict.accepted()).as(verdict.reason()).isEqualTo(sample.expectAccepted());
        if (!sample.expectAccepted()) {
            assertThat(verdict.reason()).contains(sample.reasonContains());
        }
    }

    @Test
    void everyAnsweredQuestionIsCountedOnceAndNothingElseIs() {
        GoldenCase fourPeople = FILE.cases().stream().filter(candidate -> candidate.message().contains("four people"))
                .findFirst().orElseThrow();
        GoldenHarness harness = new GoldenHarness(FILE);
        LibBotService service = harness.serviceFor(fourPeople, new ReplayAiService(
                fourPeople.recorded().interpretation(), fourPeople.recorded().answer()), harness.storeFor(fourPeople));

        harness.ask(service, fourPeople);

        assertThat(harness.published()).hasSize(1);
        assertThat(harness.published().get(0).userSubject()).isEqualTo(GoldenHarness.USER);
        assertThat(harness.published().get(0).admin()).isFalse();
    }

    @Test
    void theFollowUpIsSentTheEarlierExchangeWithTheGamesItNamed() {
        GoldenCase followUp = FILE.cases().stream().filter(candidate -> candidate.message().startsWith("which of those"))
                .findFirst().orElseThrow();
        GoldenHarness harness = new GoldenHarness(FILE);
        ReplayAiService aiService = new ReplayAiService(followUp.recorded().interpretation(),
                followUp.recorded().answer());
        LibBotService service = harness.serviceFor(followUp, aiService, harness.storeFor(followUp));

        harness.ask(service, followUp);

        String sent = aiService.lastMessagesTo(AiFeature.GAMECATALOG_CHAT_QUERY).toString();
        assertThat(sent).contains("party games for four?").contains("Gang Beasts [id 00000000-0000-0000-0000-000000000004]");
    }

    @Test
    void theLibraryRowsReachTheAnswerPromptAsDelimitedData() {
        GoldenCase sixPeople = FILE.cases().stream().filter(candidate -> candidate.message().contains("6 people here tonight"))
                .findFirst().orElseThrow();
        GoldenHarness harness = new GoldenHarness(FILE);
        ReplayAiService aiService = new ReplayAiService(sixPeople.recorded().interpretation(),
                sixPeople.recorded().answer());
        LibBotService service = harness.serviceFor(sixPeople, aiService, harness.storeFor(sixPeople));

        harness.ask(service, sixPeople);

        String systemPrompt = aiService.lastMessagesTo(AiFeature.GAMECATALOG_CHAT_ANSWER).get(0).getText();
        assertThat(systemPrompt).contains("BEGIN DATA").contains("END DATA").contains("Jackbox Party Pack 9")
                .contains("up to 8 local players").doesNotContain("Overcooked").doesNotContain("Mystery Brawler");
    }
}
