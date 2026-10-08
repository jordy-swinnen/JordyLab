package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.jordy.jordylab.gamecatalog.LibBotMessageAnswered;
import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.Expect;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenCase;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenFile;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.assertj.core.api.SoftAssertions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds LibBot (real interpreter, composer, deriver, validator, messages, memory) over an in-memory library and a given AI
 * service, and checks one golden case's expectations. The replay tier and the live tier share it.
 */
final class GoldenHarness {

    static final LibBotProperties PROPERTIES = new LibBotProperties(10, 2, 10, 15, 5, 1000);
    static final String USER = "user-1";
    static final String CONVERSATION = "conversation-1";

    private final GoldenFile file;
    private final List<LibBotMessageAnswered> published = new ArrayList<>();

    GoldenHarness(GoldenFile file) {
        this.file = file;
    }

    List<LibBotMessageAnswered> published() {
        return published;
    }

    LibBotService serviceFor(GoldenCase goldenCase, ResilientAiService aiService, ConversationStore store) {
        List<GoldenFixtures.GoldenGame> games = "empty".equals(goldenCase.library()) ? List.of() : file.library();
        Integer largest = games.stream().map(GoldenFixtures.GoldenGame::maxLocalPlayers)
                .filter(java.util.Objects::nonNull).max(Integer::compare).orElse(null);
        CandidateSource library = new InMemoryLibrary(games, new LibraryVocabulary(file.vocabulary().platforms(),
                file.vocabulary().places(), largest));
        LibBotPrompts prompts = new LibBotPrompts();
        prompts.interpretResource = new ClassPathResource("prompts/gamecatalog/libbot-interpret.st");
        prompts.answerResource = new ClassPathResource("prompts/gamecatalog/libbot-answer.st");
        try {
            prompts.init();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        ApplicationEventPublisher publisher = event -> published.add((LibBotMessageAnswered) event);

        return new LibBotService(new QuestionInterpreter(aiService, prompts), new ConstraintDeriver(), library,
                new AnswerComposer(aiService, prompts, PROPERTIES), new LibBotAnswerValidator(PROPERTIES),
                new LibBotMessages(), store, PROPERTIES, publisher, TransactionOperations.withoutTransaction());
    }

    ConversationStore storeFor(GoldenCase goldenCase) {
        Ticker ticker = () -> 0L;
        ConversationStore store = new ConversationStore(PROPERTIES, ticker);
        store.init();
        goldenCase.conversation().forEach(turn -> store.append(USER, CONVERSATION, GoldenFixtures.toTurn(turn)));

        return store;
    }

    LibBotResult ask(LibBotService service, GoldenCase goldenCase) {
        return service.ask(new LibBotService.Ask(USER, false, CONVERSATION, goldenCase.message(),
                goldenCase.attachedGameIds()), stage -> { });
    }

    /** The behaviour every answer must show, whatever the question, plus what this case expects. */
    void check(GoldenCase goldenCase, LibBotResult result, SoftAssertions softly) {
        Expect expect = goldenCase.expect();
        softly.assertThat(result.references().size()).as("at most ten references").isLessThanOrEqualTo(10);
        if (result.outcome() != LibBotOutcome.ANSWERED) {
            softly.assertThat(result.references()).as("references only on ANSWERED").isEmpty();
        }
        String normalisedText = LibBotAnswerValidator.normalise(result.text());
        result.references().forEach(reference -> softly.assertThat(normalisedText)
                .as("reference '" + reference.title() + "' is named in the answer")
                .contains(LibBotAnswerValidator.normalise(reference.title())));
        softly.assertThat(result.outcome()).as("outcome").isEqualTo(expect.outcome());
        softly.assertThat(result.language().code()).as("language").isEqualTo(expect.language());
        if (expect.applied() != null) {
            softly.assertThat(result.applied()).as("applied requirements").containsExactlyElementsOf(expect.applied());
        }
        if (expect.minReferences() != null) {
            softly.assertThat(result.references().size()).as("minimum references")
                    .isGreaterThanOrEqualTo(expect.minReferences());
        }
        if (expect.maxReferences() != null) {
            softly.assertThat(result.references().size()).as("maximum references")
                    .isLessThanOrEqualTo(expect.maxReferences());
        }
        if (expect.referenceTitlesSubsetOf() != null) {
            softly.assertThat(result.references().stream().map(Candidate::title).toList())
                    .as("reference titles").isSubsetOf(expect.referenceTitlesSubsetOf());
        }
        if (expect.unknownCount() != null) {
            softly.assertThat(result.unknown()).as("unknown-data note present").isNotNull();
            if (result.unknown() != null) {
                softly.assertThat(result.unknown().count()).as("unknown count").isEqualTo(expect.unknownCount());
            }
        }
        if (expect.textContains() != null) {
            expect.textContains().forEach(part -> softly.assertThat(result.text()).contains(part));
        }
        if (expect.textMustNotContain() != null) {
            expect.textMustNotContain().forEach(part -> softly.assertThat(result.text()).doesNotContain(part));
        }
    }
}
