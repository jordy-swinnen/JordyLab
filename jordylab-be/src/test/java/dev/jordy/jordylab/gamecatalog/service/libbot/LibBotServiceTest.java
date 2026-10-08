package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenCase;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenFile;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** The orchestration rules around the golden pipeline: failures, attachments, memory isolation and length. */
class LibBotServiceTest {

    private static final GoldenFile FILE = GoldenFixtures.load();

    private static GoldenCase caseContaining(String text) {
        return FILE.cases().stream().filter(candidate -> candidate.message().contains(text)).findFirst().orElseThrow();
    }

    private final GoldenCase fourPeople = caseContaining("four people");
    private final GoldenHarness harness = new GoldenHarness(FILE);

    private ReplayAiService replay() {
        return new ReplayAiService(fourPeople.recorded().interpretation(), fourPeople.recorded().answer());
    }

    private LibBotService.Ask ask(String user, String conversation, List<UUID> attached) {
        return new LibBotService.Ask(user, false, conversation, fourPeople.message(), attached);
    }

    @Test
    void aFailedInterpretationIsUnavailableAndNeitherCountedNorRemembered() {
        ConversationStore store = harness.storeFor(fourPeople);
        LibBotService service = harness.serviceFor(fourPeople, replay().failingWith(ProviderFailureReason.TIMEOUT), store);

        assertThatThrownBy(() -> service.ask(ask("alice", "c1", List.of()), stage -> { }))
                .isInstanceOf(LibBotUnavailableException.class);

        assertSoftly(softly -> {
            softly.assertThat(harness.published()).isEmpty();
            softly.assertThat(store.history("alice", "c1")).isEmpty();
        });
    }

    @Test
    void anAttachedGameThatIsNotVisibleIsRejectedBeforeAnyModelCall() {
        ReplayAiService aiService = replay();
        LibBotService service = harness.serviceFor(fourPeople, aiService, harness.storeFor(fourPeople));

        assertThatThrownBy(() -> service.ask(ask("alice", "c1", List.of(UUID.randomUUID())), stage -> { }))
                .isInstanceOf(LibBotAttachmentException.class);

        assertThat(aiService.callsTo(dev.jordy.jordylab.shared.ai.AiFeature.GAMECATALOG_CHAT_QUERY)).isZero();
    }

    @Test
    void moreThanFiveAttachedGamesAreRejected() {
        LibBotService service = harness.serviceFor(fourPeople, replay(), harness.storeFor(fourPeople));
        List<UUID> six = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            six.add(UUID.randomUUID());
        }

        assertThatThrownBy(() -> service.ask(ask("alice", "c1", six), stage -> { }))
                .isInstanceOf(LibBotAttachmentException.class).hasMessageContaining("At most 5");
    }

    @Test
    void stagesAreReportedInOrder() {
        LibBotService service = harness.serviceFor(fourPeople, replay(), harness.storeFor(fourPeople));
        List<LibBotStage> stages = new ArrayList<>();

        service.ask(ask("alice", "c1", List.of()), stages::add);

        assertThat(stages).containsExactly(LibBotStage.UNDERSTANDING, LibBotStage.SEARCHING, LibBotStage.WRITING);
    }

    @Test
    void aQuestionThatNeverSearchesReportsOnlyTheFirstStage() {
        GoldenCase cats = caseContaining("cats");
        LibBotService service = harness.serviceFor(cats, new ReplayAiService(cats.recorded().interpretation(), null),
                harness.storeFor(cats));
        List<LibBotStage> stages = new ArrayList<>();

        service.ask(new LibBotService.Ask("alice", false, "c1", cats.message(), List.of()), stages::add);

        assertThat(stages).containsExactly(LibBotStage.UNDERSTANDING);
    }

    @Test
    void twoUsersWithTheSameConversationIdNeverShareMemory() {
        ConversationStore store = harness.storeFor(fourPeople);
        LibBotService service = harness.serviceFor(fourPeople, replay(), store);

        service.ask(ask("alice", "shared-id", List.of()), stage -> { });

        assertSoftly(softly -> {
            softly.assertThat(store.history("alice", "shared-id")).hasSize(1);
            softly.assertThat(store.history("bob", "shared-id")).isEmpty();
        });
    }

    @Test
    void aVeryLongConversationKeepsWorkingAndRemembersOnlyTheLastTen() {
        ConversationStore store = harness.storeFor(fourPeople);

        for (int turn = 1; turn <= 25; turn++) {
            LibBotService service = harness.serviceFor(fourPeople, replay(), store);
            LibBotResult result = service.ask(ask("alice", "long", List.of()), stage -> { });
            assertThat(result.outcome()).isEqualTo(LibBotOutcome.ANSWERED);
        }

        assertThat(store.history("alice", "long")).hasSize(10);
    }

    @Test
    void aFailedAnswerCompositionIsUnavailableAndNotCounted() {
        ReplayAiService composeFails = new ReplayAiService(fourPeople.recorded().interpretation(), null);
        LibBotService service = harness.serviceFor(fourPeople, composeFails, harness.storeFor(fourPeople));

        assertThatThrownBy(() -> service.ask(ask("alice", "c1", List.of()), stage -> { }))
                .isInstanceOf(AssertionError.class);

        assertThat(harness.published()).isEmpty();
    }

    @Test
    void anAdminQuestionIsPublishedAsAdmin() {
        LibBotService service = harness.serviceFor(fourPeople, replay(), harness.storeFor(fourPeople));

        service.ask(new LibBotService.Ask("root", true, "c1", fourPeople.message(), List.of()), stage -> { });

        assertThat(harness.published()).singleElement().satisfies(event -> assertThat(event.admin()).isTrue());
    }
}
