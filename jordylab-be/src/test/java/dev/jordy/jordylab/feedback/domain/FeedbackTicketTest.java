package dev.jordy.jordylab.feedback.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FeedbackTicketTest {

    private static FeedbackTicket.FeedbackTicketBuilder aNewTicket() {
        return FeedbackTicket.builder()
                .app(FeedbackApp.GAME_CATALOG)
                .type(FeedbackType.BUG_REPORT)
                .title("Grid loses my scroll position")
                .description("When I open a game and go back, the grid jumps to the top.")
                .senderSubject("7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f")
                .senderDisplayName("Ada Palmer")
                .state(FeedbackTicketState.NEW);
    }

    @Test
    void buildsANewTicketWithEveryField() {
        FeedbackTicket ticket = aNewTicket().build();

        assertSoftly(softly -> {
            softly.assertThat(ticket.getId()).isNotNull();
            softly.assertThat(ticket.getApp()).isEqualTo(FeedbackApp.GAME_CATALOG);
            softly.assertThat(ticket.getType()).isEqualTo(FeedbackType.BUG_REPORT);
            softly.assertThat(ticket.getTitle()).isEqualTo("Grid loses my scroll position");
            softly.assertThat(ticket.getDescription())
                    .isEqualTo("When I open a game and go back, the grid jumps to the top.");
            softly.assertThat(ticket.getSenderSubject()).isEqualTo("7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f");
            softly.assertThat(ticket.getSenderDisplayName()).isEqualTo("Ada Palmer");
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.NEW);
            softly.assertThat(ticket.getReply()).isNull();
            softly.assertThat(ticket.getPrivateNote()).isNull();
            softly.assertThat(ticket.getBacklogTicketId()).isNull();
            softly.assertThat(ticket.getBacklogTicketUrl()).isNull();
            softly.assertThat(ticket.getAcceptError()).isNull();
        });
    }

    @Test
    void trimsTitleAndDescription() {
        FeedbackTicket ticket = aNewTicket().title("  padded  ").description("  padded text  ").build();

        assertSoftly(softly -> {
            softly.assertThat(ticket.getTitle()).isEqualTo("padded");
            softly.assertThat(ticket.getDescription()).isEqualTo("padded text");
        });
    }

    @Test
    void buildWithoutApp() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().app(null).build());
    }

    @Test
    void buildWithoutType() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().type(null).build());
    }

    @Test
    void buildWithoutTitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().title(null).build());
    }

    @Test
    void buildWithBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().title("   ").build());
    }

    @Test
    void buildWithTooLongTitle() {
        String overlong = "x".repeat(FeedbackTicket.MAX_TITLE_LENGTH + 1);

        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().title(overlong).build());
    }

    @Test
    void buildWithoutDescription() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().description(null).build());
    }

    @Test
    void buildWithBlankDescription() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().description("   ").build());
    }

    @Test
    void buildWithTooLongDescription() {
        String overlong = "x".repeat(FeedbackTicket.MAX_DESCRIPTION_LENGTH + 1);

        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().description(overlong).build());
    }

    @Test
    void buildWithoutSender() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().senderSubject(null).build());
    }

    @Test
    void buildWithoutState() {
        assertThatIllegalArgumentException().isThrownBy(() -> aNewTicket().state(null).build());
    }

    @Test
    void movesANewTicketToBacklog() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.moveToBacklog();

        assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.BACKLOG);
    }

    @Test
    void backlogTicketCanBeAcceptedLater() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.moveToBacklog();

        ticket.markAccepted("issue-id", "https://linear.app/jordylab/issue/JOR-99");

        assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
    }

    @Test
    void backlogTicketCanBeRejectedLater() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.moveToBacklog();

        ticket.reject();

        assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.REJECTED);
    }

    @Test
    void rejectsANewTicket() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.reject();

        assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.REJECTED);
    }

    @Test
    void rejectedIsFinal() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.reject();

        assertThatIllegalStateException().isThrownBy(ticket::moveToBacklog);
        assertThatIllegalStateException().isThrownBy(ticket::reject);
        assertThatIllegalStateException().isThrownBy(() -> ticket.markAccepted("id", "url"));
        assertThatIllegalStateException().isThrownBy(() -> ticket.recordAcceptError("boom"));
    }

    @Test
    void acceptedIsFinal() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.markAccepted("issue-id", "https://linear.app/jordylab/issue/JOR-99");

        assertThatIllegalStateException().isThrownBy(ticket::moveToBacklog);
        assertThatIllegalStateException().isThrownBy(ticket::reject);
        assertThatIllegalStateException().isThrownBy(() -> ticket.markAccepted("other", "other-url"));
    }

    @Test
    void acceptingStoresTheIssueAndClearsAPreviousError() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.recordAcceptError("Linear timed out");

        ticket.markAccepted("issue-id", "https://linear.app/jordylab/issue/JOR-99");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(ticket.getBacklogTicketId()).isEqualTo("issue-id");
            softly.assertThat(ticket.getBacklogTicketUrl()).isEqualTo("https://linear.app/jordylab/issue/JOR-99");
            softly.assertThat(ticket.getAcceptError()).isNull();
        });
    }

    @Test
    void recordsAnAcceptErrorWithoutMovingTheState() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.recordAcceptError("Linear is unreachable");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getAcceptError()).isEqualTo("Linear is unreachable");
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.NEW);
        });
    }

    @Test
    void truncatesAnOverlongAcceptError() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.recordAcceptError("x".repeat(FeedbackTicket.MAX_ACCEPT_ERROR_LENGTH + 50));

        assertThat(ticket.getAcceptError()).hasSize(FeedbackTicket.MAX_ACCEPT_ERROR_LENGTH);
    }

    @Test
    void updatesBothAdminTextsWholesale() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.updateTexts("Thanks — accepted.", "Fix is planned.");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getReply()).isEqualTo("Thanks — accepted.");
            softly.assertThat(ticket.getPrivateNote()).isEqualTo("Fix is planned.");
        });
    }

    @Test
    void clearingATextRequiresAnEmptyStringNotAbsence() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.updateTexts("reply", "note");

        ticket.updateTexts("", "  ");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getReply()).isNull();
            softly.assertThat(ticket.getPrivateNote()).isNull();
        });
    }

    @Test
    void textsCanBeUpdatedInAnyState() {
        FeedbackTicket ticket = aNewTicket().build();
        ticket.reject();

        ticket.updateTexts("Thanks for the report.", "Not planned.");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getReply()).isEqualTo("Thanks for the report.");
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.REJECTED);
        });
    }

    @Test
    void anonymisesTheSender() {
        FeedbackTicket ticket = aNewTicket().build();

        ticket.anonymiseSender();

        assertThat(ticket.getSenderDisplayName()).isNull();
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(FeedbackTicket.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
