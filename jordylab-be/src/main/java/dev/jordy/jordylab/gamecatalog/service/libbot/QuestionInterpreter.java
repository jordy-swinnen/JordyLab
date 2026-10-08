package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import dev.jordy.jordylab.shared.ai.StructuredOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Step 1 of LibBot (spec 013 research A1): the first model call, which only extracts what was said. Runs at temperature
 * 0 (configured per feature) and returns a validated {@link QuestionInterpretation} or fails with
 * {@link LibBotUnavailableException}.
 */
@Component
@RequiredArgsConstructor
public class QuestionInterpreter {

    private final ResilientAiService aiService;
    private final LibBotPrompts prompts;

    public QuestionInterpretation interpret(String message, List<ConversationTurn> history, LibraryVocabulary vocabulary,
            List<Candidate> attachedGames) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompts.interpret(joined(vocabulary.platforms()), joined(vocabulary.places()),
                attachedGames.isEmpty() ? "none" : attachedGames.stream()
                        .map(game -> game.gameId() + " | " + game.title()).collect(Collectors.joining("\n")))));
        messages.addAll(HistoryMessages.of(history));
        messages.add(new UserMessage(message));
        StructuredOutput<QuestionInterpretation> output = aiService.callStructured(
                AiFeature.GAMECATALOG_CHAT_QUERY, messages, QuestionInterpretation.class);
        if (!output.success()) {
            AiCallResult call = output.call();
            throw new LibBotUnavailableException("The question could not be interpreted: " + call.failureReason());
        }

        return output.value();
    }

    private String joined(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    /** Earlier exchanges as chat messages, with the games each answer named so "those" can be resolved to ids. */
    static final class HistoryMessages {

        private HistoryMessages() {
        }

        static List<Message> of(List<ConversationTurn> history) {
            List<Message> messages = new ArrayList<>();
            for (ConversationTurn turn : history) {
                messages.add(new UserMessage(turn.userText()));
                String named = turn.cited().isEmpty() ? "" : "\n(Games named: " + turn.cited().stream()
                        .map(game -> game.title() + " [id " + game.gameId() + "]").collect(Collectors.joining(", "))
                        + ")";
                messages.add(new AssistantMessage(turn.answerText() + named));
            }

            return messages;
        }
    }
}
