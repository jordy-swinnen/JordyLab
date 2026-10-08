package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import dev.jordy.jordylab.shared.ai.StructuredOutput;
import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Step 4 of LibBot (spec 013 research A1, A6): the second model call, which writes the answer from the retrieved rows
 * only. The rows are delimited data, the rules say to answer from them alone, and the result is checked afterwards by
 * {@link LibBotAnswerValidator}. The composed answer also reports the model that really answered.
 */
@Component
@RequiredArgsConstructor
public class AnswerComposer {

    /** The composed answer and the call that produced it (for authorship and cost). */
    public record Composition(LibBotAnswer answer, AiCallResult call) {
    }

    private final ResilientAiService aiService;
    private final LibBotPrompts prompts;
    private final LibBotProperties properties;

    public Composition compose(Language language, String question, String appliedDescription,
            List<Candidate> candidates, List<ConversationTurn> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompts.answer(languageName(language), appliedDescription,
                properties.maxReferences(), candidates.stream().map(AnswerComposer::row)
                        .collect(Collectors.joining("\n")))));
        messages.addAll(QuestionInterpreter.HistoryMessages.of(history));
        messages.add(new UserMessage(question));
        StructuredOutput<LibBotAnswer> output = aiService.callStructured(AiFeature.GAMECATALOG_CHAT_ANSWER, messages,
                LibBotAnswer.class);
        if (!output.success()) {
            throw new LibBotUnavailableException("The answer could not be written: " + output.call().failureReason());
        }

        return new Composition(output.value(), output.call());
    }

    private String languageName(Language language) {
        return language == Language.NL ? "Dutch" : "English";
    }

    /** One catalog row; facts that are not known are left out rather than guessed. */
    static String row(Candidate candidate) {
        List<String> facts = new ArrayList<>();
        if (candidate.maxLocalPlayers() != null) {
            facts.add("up to " + candidate.maxLocalPlayers() + " local players");
        } else if (Boolean.TRUE.equals(candidate.localMultiplayer())) {
            facts.add("local multiplayer");
        }
        if (Boolean.TRUE.equals(candidate.singlePlayer())) {
            facts.add("single player");
        }
        if (Boolean.TRUE.equals(candidate.onlineMultiplayer())) {
            facts.add("online multiplayer");
        }
        if (candidate.genres() != null) {
            facts.add(candidate.genres());
        }
        if (candidate.releaseYear() != null) {
            facts.add(String.valueOf(candidate.releaseYear()));
        }
        if (candidate.developer() != null) {
            facts.add("by " + candidate.developer());
        }
        String votes = "want " + candidate.wantVotes() + ", liked " + candidate.likedVotes() + ", disliked "
                + candidate.dislikedVotes();

        if (!candidate.romCopies().isEmpty()) {
            facts.add("emulated: " + candidate.romCopies().stream().map(copy -> copy.machine() + " "
                    + copy.status().name().toLowerCase(java.util.Locale.ROOT)).collect(Collectors.joining(", ")));
        }
        if (candidate.myMark() != null) {
            votes += ", the asker marked it: " + candidate.myMark().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }

        return candidate.gameId() + " | " + candidate.title() + " | " + String.join(", ", candidate.platforms())
                + " | " + String.join("; ", facts) + " | " + votes + " | "
                + (candidate.description() == null ? "" : candidate.description().replace('\n', ' '));
    }
}
