package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class AnswerComposerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ObjectMapper mapper = new ObjectMapper();
    private LibBotPrompts prompts;

    @BeforeEach
    void setUp() throws IOException {
        prompts = new LibBotPrompts();
        prompts.interpretResource = new ClassPathResource("prompts/gamecatalog/libbot-interpret.st");
        prompts.answerResource = new ClassPathResource("prompts/gamecatalog/libbot-answer.st");
        prompts.init();
    }

    private Candidate jackbox() {
        return Candidate.builder().gameId(ID).title("Jackbox Party Pack 9").platforms(List.of("Steam"))
                .confirmed(true).wantVotes(2).likedVotes(1).dislikedVotes(0).genres("Party").releaseYear(2022)
                .maxLocalPlayers(8).localMultiplayer(true).onlineMultiplayer(true).description("Party games\nfor everyone.")
                .build();
    }

    @Test
    void theRowsStateOnlyKnownFactsAndTheVotes() {
        String row = AnswerComposer.row(jackbox());

        assertThat(row).isEqualTo(ID + " | Jackbox Party Pack 9 | Steam | up to 8 local players; online multiplayer; "
                + "Party; 2022 | want 2, liked 1, disliked 0 | Party games for everyone.");
    }

    @Test
    void unknownFactsAreLeftOutRatherThanGuessed() {
        Candidate sparse = Candidate.builder().gameId(ID).title("Mystery").platforms(List.of("Steam")).build();

        assertThat(AnswerComposer.row(sparse)).isEqualTo(ID + " | Mystery | Steam |  | want 0, liked 0, disliked 0 | ");
    }

    @Test
    void theAnswerPromptCarriesTheLanguageTheAppliedRequirementsAndTheRows() throws IOException {
        ReplayAiService aiService = new ReplayAiService(null, mapper.readTree("""
                {"text":"Jackbox Party Pack 9 fits.","recommendedGameIds":["00000000-0000-0000-0000-000000000003"]}
                """));

        AnswerComposer.Composition composition = new AnswerComposer(aiService, prompts, GoldenHarness.PROPERTIES)
                .compose(Language.NL, "wat kunnen we spelen?", "6+ lokale spelers", List.of(jackbox()), List.of());

        List<Message> sent = aiService.lastMessagesTo(AiFeature.GAMECATALOG_CHAT_ANSWER);
        assertSoftly(softly -> {
            softly.assertThat(composition.answer().text()).isEqualTo("Jackbox Party Pack 9 fits.");
            softly.assertThat(sent).hasSize(2);
            softly.assertThat(sent.get(0).getText()).contains("Answer in Dutch.").contains("6+ lokale spelers")
                    .contains("recommend at most 10 games").contains("Jackbox Party Pack 9 | Steam");
            softly.assertThat(sent.get(1).getText()).isEqualTo("wat kunnen we spelen?");
        });
    }

    @Test
    void aFailedCallBecomesLibBotUnavailable() throws IOException {
        ReplayAiService aiService = new ReplayAiService(null, null).failingWith(ProviderFailureReason.TIMEOUT);

        assertThatThrownBy(() -> new AnswerComposer(aiService, prompts, GoldenHarness.PROPERTIES)
                .compose(Language.EN, "q", "none", List.of(jackbox()), List.of()))
                .isInstanceOf(LibBotUnavailableException.class).hasMessageContaining("TIMEOUT");
    }
}
