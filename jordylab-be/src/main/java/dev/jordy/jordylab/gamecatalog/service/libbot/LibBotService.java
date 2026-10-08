package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.LibBotMessageAnswered;
import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import dev.jordy.jordylab.gamecatalog.service.libbot.AnswerComposer.Composition;
import dev.jordy.jordylab.gamecatalog.service.libbot.QuestionInterpretation.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * LibBot (spec 013): interpret, derive, retrieve, compose, in that order, with the rules in Java and the model only
 * extracting facts and writing prose. No transaction spans the model calls. Every outcome except a failure is a reply
 * the person can read, and only a successful reply is counted against a guest's allowance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibBotService {

    static final int MAX_ATTACHED_GAMES = 5;

    /** What one question carries in. */
    public record Ask(String userSubject, boolean admin, String conversationId, String message,
            List<UUID> attachedGameIds) {

        public Ask {
            attachedGameIds = attachedGameIds == null ? List.of() : List.copyOf(attachedGameIds);
        }
    }

    private final QuestionInterpreter interpreter;
    private final ConstraintDeriver deriver;
    private final CandidateSource retriever;
    private final AnswerComposer composer;
    private final LibBotAnswerValidator validator;
    private final LibBotMessages messages;
    private final ConversationStore conversationStore;
    private final LibBotProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactions;

    public LibBotResult ask(Ask ask, Consumer<LibBotStage> onStage) {
        return ask(ask, onStage, () -> true);
    }

    /**
     * {@code stillConnected} is asked once the reply exists: a question whose reader went away is neither remembered nor
     * counted against a guest's allowance (spec 013 FR-013).
     */
    public LibBotResult ask(Ask ask, Consumer<LibBotStage> onStage, BooleanSupplier stillConnected) {
        List<Candidate> attached = attachedGames(ask.attachedGameIds(), ask.userSubject());
        List<ConversationTurn> history = conversationStore.history(ask.userSubject(), ask.conversationId());
        onStage.accept(LibBotStage.UNDERSTANDING);
        LibraryVocabulary vocabulary = retriever.vocabulary();
        QuestionInterpretation interpretation = interpreter.interpret(ask.message(), history, vocabulary, attached);

        LibBotResult result = answerWithoutSearch(interpretation)
                .orElseGet(() -> search(ask, interpretation, vocabulary, attached, history, onStage));

        if (stillConnected.getAsBoolean()) {
            remember(ask, result);
            publishAnswered(ask);
        }

        return result;
    }

    /** Fails fast, before an event stream opens, when an attached game cannot be used. */
    public void requireAttachable(List<UUID> attachedGameIds, String userSubject) {
        attachedGames(attachedGameIds, userSubject);
    }

    private Optional<LibBotResult> answerWithoutSearch(QuestionInterpretation interpretation) {
        Language language = interpretation.language();
        if (language == Language.OTHER) {
            return Optional.of(LibBotResult.plain(LibBotOutcome.OUT_OF_SCOPE, Language.EN,
                    messages.rephrase()));
        }
        if (interpretation.intent() == Intent.NEEDS_CLARIFICATION) {
            return Optional.of(LibBotResult.plain(LibBotOutcome.CLARIFY, language,
                    replyOr(interpretation.reply(), messages.clarify(language))));
        }
        if (interpretation.intent() == Intent.OUT_OF_SCOPE) {
            return Optional.of(LibBotResult.plain(LibBotOutcome.OUT_OF_SCOPE, language,
                    replyOr(interpretation.reply(), messages.outOfScope(language))));
        }
        if (retriever.visibleGameCount() == 0) {
            return Optional.of(LibBotResult.plain(LibBotOutcome.EMPTY_LIBRARY, language,
                    messages.emptyLibrary(language)));
        }

        return Optional.empty();
    }

    private LibBotResult search(Ask ask, QuestionInterpretation interpretation, LibraryVocabulary vocabulary,
            List<Candidate> attached, List<ConversationTurn> history, Consumer<LibBotStage> onStage) {
        Language language = interpretation.language();
        List<Candidate> referenced = referencedGames(interpretation, attached, ask.userSubject());
        Derivation derivation = deriver.derive(interpretation.facts(), vocabulary);
        List<String> applied = messages.applied(derivation.applied(), language);
        onStage.accept(LibBotStage.SEARCHING);
        boolean askingAboutGames = interpretation.intent() == Intent.GAME_QUESTION && !referenced.isEmpty();
        RetrievalResult retrieval = askingAboutGames ? new RetrievalResult(List.of(), 0, List.of(), false)
                : retriever.retrieve(derivation.constraints(), ask.userSubject());
        List<Candidate> candidates = merge(referenced, retrieval.confirmed());
        LibBotResult.Unknown unknown = retrieval.unknownCount() == 0 ? null : new LibBotResult.Unknown(
                retrieval.unknownCount(), messages.unknownNote(retrieval.unknownCount(),
                        derivation.constraints().minLocalPlayers(), language));
        if (candidates.isEmpty()) {
            return new LibBotResult(LibBotOutcome.NO_MATCH, language, noMatchText(derivation, retrieval, language),
                    applied, unknown, List.of());
        }
        onStage.accept(LibBotStage.WRITING);
        Composition composition = composer.compose(language, standalone(interpretation, ask), String.join(", ",
                applied.isEmpty() ? List.of("none") : applied), candidates, history);
        List<Candidate> references = validator.validReferences(composition.answer(), candidates);
        LibBotOutcome outcome = references.isEmpty() ? LibBotOutcome.NO_MATCH : LibBotOutcome.ANSWERED;

        return new LibBotResult(outcome, language, composition.answer().text(), applied, unknown, references);
    }

    private String noMatchText(Derivation derivation, RetrievalResult retrieval, Language language) {
        if (derivation.partyExceedsKnownGroups()) {
            return messages.noMatchGroupTooLarge(derivation.constraints().minLocalPlayers(),
                    derivation.largestKnownGroup(), language);
        }

        return retrieval.unknownCount() > 0 ? messages.noMatchUnknownOnly(language) : messages.noMatch(language);
    }

    private List<Candidate> attachedGames(List<UUID> attachedGameIds, String userSubject) {
        List<UUID> distinct = attachedGameIds.stream().distinct().toList();
        if (distinct.size() > MAX_ATTACHED_GAMES) {
            throw new LibBotAttachmentException("At most " + MAX_ATTACHED_GAMES + " games can be attached");
        }
        List<Candidate> visible = retriever.describeVisible(distinct, userSubject);
        if (visible.size() != distinct.size()) {
            throw new LibBotAttachmentException("An attached game does not exist or is not visible");
        }

        return visible;
    }

    private List<Candidate> referencedGames(QuestionInterpretation interpretation, List<Candidate> attached,
            String userSubject) {
        List<UUID> ids = new ArrayList<>(attached.stream().map(Candidate::gameId).toList());
        ids.addAll(interpretation.facts().referencedGameIds());
        List<UUID> limited = ids.stream().distinct().limit(MAX_ATTACHED_GAMES).toList();

        return limited.equals(attached.stream().map(Candidate::gameId).toList()) ? attached
                : retriever.describeVisible(limited, userSubject);
    }

    /** Referenced games first, then the search results, each game once, within the candidate limit. */
    private List<Candidate> merge(List<Candidate> referenced, List<Candidate> found) {
        Map<UUID, Candidate> merged = new LinkedHashMap<>();
        referenced.forEach(candidate -> merged.putIfAbsent(candidate.gameId(), candidate));
        found.forEach(candidate -> merged.putIfAbsent(candidate.gameId(), candidate));

        return merged.values().stream().limit(properties.maxCandidates()).toList();
    }

    private String standalone(QuestionInterpretation interpretation, Ask ask) {
        String rewritten = interpretation.standaloneQuestion();

        return rewritten == null || rewritten.isBlank() ? ask.message() : rewritten;
    }

    private String replyOr(String reply, String fallback) {
        return reply == null || reply.isBlank() ? fallback : reply;
    }

    private void remember(Ask ask, LibBotResult result) {
        List<ConversationTurn.CitedGame> cited = result.references().stream()
                .map(game -> new ConversationTurn.CitedGame(game.gameId(), game.title())).toList();
        conversationStore.append(ask.userSubject(), ask.conversationId(),
                new ConversationTurn(ask.message(), result.text(), cited, result.outcome()));
    }

    /** Published inside a short transaction so the Modulith event registry records it for the guest-allowance listener. */
    private void publishAnswered(Ask ask) {
        transactions.executeWithoutResult(status -> eventPublisher.publishEvent(
                new LibBotMessageAnswered(ask.userSubject(), ask.admin())));
    }
}
