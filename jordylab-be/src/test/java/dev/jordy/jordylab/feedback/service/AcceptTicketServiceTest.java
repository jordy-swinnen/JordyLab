package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import dev.jordy.jordylab.feedback.rest.client.BacklogClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * US3 at the service level: idempotency (a stored issue id never calls Linear again), the terminal
 * guard running before any Linear call, the recorded failure that keeps a ticket retryable, and the
 * adopt-on-retry path. The full persistence flow lives in {@code FeedbackModuleTest}.
 */
@ExtendWith(MockitoExtension.class)
class AcceptTicketServiceTest {

    private static final BacklogClient.BacklogTicket ISSUE =
            new BacklogClient.BacklogTicket("issue-1", "https://linear.app/jordylab/issue/JOR-101");

    @Mock
    private FeedbackTicketRepository ticketRepository;

    @Mock
    private BacklogClient backlogClient;

    private AcceptTicketService service;

    @BeforeEach
    void setUp() {
        service = new AcceptTicketService(ticketRepository, backlogClient);
    }

    @Test
    void anAlreadyAcceptedTicketShortCircuitsWithoutCallingLinearAgain() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.ACCEPTED)
                .backlogTicketId("issue-1")
                .backlogTicketUrl("https://linear.app/jordylab/issue/JOR-101")
                .build();
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));

        FeedbackTicket result = service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, "another note");

        assertThat(result).isSameAs(ticket);
        verifyNoInteractions(backlogClient);
    }

    @Test
    void aRejectedTicketIsRefusedBeforeAnyLinearCall() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.REJECTED)
                .build();
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(backlogClient);
    }

    @Test
    void refusesAnOverlongAcceptNoteBeforeTouchingLinear() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));
        String overlong = "x".repeat(FeedbackTicket.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, overlong))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(backlogClient);
    }

    @Test
    void anUnknownTicketIsNotFound() {
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, null))
                .isInstanceOf(TicketNotFoundException.class);
        verifyNoInteractions(backlogClient);
    }

    @Test
    void acceptsTheTicketWithThePrivateNoteAndStoresTheIssue() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));
        when(backlogClient.createTicket(ticket, "Repro'd on CachyOS too.")).thenReturn(ISSUE);

        FeedbackTicket result = service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, "Repro'd on CachyOS too.");

        assertSoftly(softly -> {
            softly.assertThat(result.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(result.getBacklogTicketId()).isEqualTo("issue-1");
            softly.assertThat(result.getBacklogTicketUrl()).isEqualTo("https://linear.app/jordylab/issue/JOR-101");
            softly.assertThat(result.getPrivateNote()).isEqualTo("Repro'd on CachyOS too.");
        });
        verify(backlogClient).createTicket(ticket, "Repro'd on CachyOS too.");
    }

    @Test
    void aLinearFailureIsRecordedOnTheTicketAndRethrown() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));
        when(backlogClient.createTicket(ticket, null))
                .thenThrow(new BacklogUnavailableException("Linear is unreachable"));

        assertThatThrownBy(() -> service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, null))
                .isInstanceOf(BacklogUnavailableException.class);
        assertSoftly(softly -> {
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.NEW);
            softly.assertThat(ticket.getAcceptError()).isEqualTo("Linear is unreachable");
        });
    }

    @Test
    void aRetryAfterAFailureAdoptsTheIssueInsteadOfCreatingASecondOne() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        ticket.recordAcceptError("timed out after Linear accepted");
        when(ticketRepository.findByIdForAccept(FeedbackTicketTestBuilder.DEFAULT_ID))
                .thenReturn(Optional.of(ticket));
        when(backlogClient.findTicket(ticket)).thenReturn(Optional.of(ISSUE));

        FeedbackTicket result = service.accept(FeedbackTicketTestBuilder.DEFAULT_ID, null);

        assertSoftly(softly -> {
            softly.assertThat(result.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(result.getBacklogTicketId()).isEqualTo("issue-1");
            softly.assertThat(result.getAcceptError()).isNull();
        });
        verify(backlogClient).findTicket(ticket);
        verify(backlogClient, never()).createTicket(eq(ticket), isNull());
    }
}
