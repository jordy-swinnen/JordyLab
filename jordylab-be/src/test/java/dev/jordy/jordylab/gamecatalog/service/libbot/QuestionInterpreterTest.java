package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class QuestionInterpreterTest {

    private static final UUID MARIO_KART = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LibraryVocabulary VOCABULARY = new LibraryVocabulary(
            List.of("PlayStation 5", "Steam"), List.of("Living room PC"), 8);

    private final ObjectMapper mapper = new ObjectMapper();
    private LibBotPrompts prompts;

    @BeforeEach
    void setUp() throws IOException {
        prompts = new LibBotPrompts();
        prompts.interpretResource = new ClassPathResource("prompts/gamecatalog/libbot-interpret.st");
        prompts.answerResource = new ClassPathResource("prompts/gamecatalog/libbot-answer.st");
        prompts.init();
    }

    private ReplayAiService recorded(String interpretationJson) throws IOException {
        return new ReplayAiService(mapper.readTree(interpretationJson), null);
    }

    @Test
    void sendsThePromptTheEarlierExchangesAndTheQuestionInThatOrder() throws IOException {
        ReplayAiService aiService = recorded("""
                {"intent":"FOLLOW_UP","language":"en","standaloneQuestion":"Which of Mario Kart 8 Deluxe are installed?",
                 "facts":{"installStatus":"INSTALLED"}}
                """);
        ConversationTurn earlier = new ConversationTurn("racing games?", "Mario Kart 8 Deluxe is a good one.",
                List.of(new ConversationTurn.CitedGame(MARIO_KART, "Mario Kart 8 Deluxe")), LibBotOutcome.ANSWERED);
        Candidate attached = Candidate.builder().gameId(MARIO_KART).title("Mario Kart 8 Deluxe").platforms(List.of())
                .build();

        QuestionInterpretation interpretation = new QuestionInterpreter(aiService, prompts)
                .interpret("which of those are installed?", List.of(earlier), VOCABULARY, List.of(attached));

        List<Message> sent = aiService.lastMessagesTo(AiFeature.GAMECATALOG_CHAT_QUERY);
        assertSoftly(softly -> {
            softly.assertThat(interpretation.intent()).isEqualTo(QuestionInterpretation.Intent.FOLLOW_UP);
            softly.assertThat(sent).hasSize(4);
            softly.assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
            softly.assertThat(sent.get(0).getText()).contains("PlayStation 5, Steam").contains("Living room PC")
                    .contains(MARIO_KART + " | Mario Kart 8 Deluxe").doesNotContain("<platforms>");
            softly.assertThat(sent.get(1)).isInstanceOf(UserMessage.class);
            softly.assertThat(sent.get(2)).isInstanceOf(AssistantMessage.class);
            softly.assertThat(sent.get(2).getText()).contains("Mario Kart 8 Deluxe [id " + MARIO_KART + "]");
            softly.assertThat(sent.get(3).getText()).isEqualTo("which of those are installed?");
        });
    }

    @Test
    void anAbsentAttachmentAndAnEmptyLibraryAreWrittenAsNone() throws IOException {
        ReplayAiService aiService = recorded("""
                {"intent":"LIBRARY_QUERY","language":"en","facts":{}}
                """);

        new QuestionInterpreter(aiService, prompts).interpret("hi", List.of(), new LibraryVocabulary(null, null, null),
                List.of());

        assertThat(aiService.lastMessagesTo(AiFeature.GAMECATALOG_CHAT_QUERY).get(0).getText())
                .contains("Known platforms in the library: none").contains("BEGIN DATA\nnone\nEND DATA");
    }

    @Test
    void aFailedCallBecomesLibBotUnavailable() throws IOException {
        ReplayAiService aiService = recorded("{}").failingWith(ProviderFailureReason.INVALID_OUTPUT);

        assertThatThrownBy(() -> new QuestionInterpreter(aiService, prompts).interpret("hi", List.of(), VOCABULARY,
                List.of())).isInstanceOf(LibBotUnavailableException.class).hasMessageContaining("INVALID_OUTPUT");
    }

    @Test
    void aClarifyOrDeclineWithoutAReplyIsRejectedByValidation() {
        QuestionInterpretation withoutReply = new QuestionInterpretation(QuestionInterpretation.Intent.OUT_OF_SCOPE,
                Language.EN, null, null, null);

        assertThat(withoutReply.isReplyPresentWhenRequired()).isFalse();
    }
}
