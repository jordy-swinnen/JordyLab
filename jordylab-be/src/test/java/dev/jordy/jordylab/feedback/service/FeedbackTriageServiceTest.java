package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin half of US2: the inbox list with its filter, the Backlog/Reject transitions and the two
 * admin texts. Terminal-state transitions fail fast (the controller maps them to 409 INVALID_STATE).
 */
@ExtendWith(MockitoExtension.class)
class FeedbackTriageServiceTest {

    @Mock
    private FeedbackTicketRepository ticketRepository;

    private FeedbackTriageService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackTriageService(ticketRepository);
    }

    @Test
    void listsTheWholeInboxNewestFirstWithoutAFilter() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findAllByOrderByCreatedDateDesc()).thenReturn(List.of(ticket));

        assertThat(service.inbox(null)).containsExactly(ticket);
    }

    @Test
    void listsOneStateWhenFiltered() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.BACKLOG)
                .build();
        when(ticketRepository.findAllByStateOrderByCreatedDateDesc(FeedbackTicketState.BACKLOG))
                .thenReturn(List.of(ticket));

        assertThat(service.inbox(FeedbackTicketState.BACKLOG)).containsExactly(ticket);
    }

    @Test
    void movesANewTicketToBacklog() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        FeedbackTicket updated = service.moveToBacklog(FeedbackTicketTestBuilder.DEFAULT_ID);

        assertThat(updated.getState()).isEqualTo(FeedbackTicketState.BACKLOG);
    }

    @Test
    void aBacklogTicketCanStillBeRejected() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.BACKLOG)
                .build();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        assertThat(service.reject(FeedbackTicketTestBuilder.DEFAULT_ID).getState())
                .isEqualTo(FeedbackTicketState.REJECTED);
    }

    @Test
    void rejectingATerminalTicketFailsFast() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.REJECTED)
                .build();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reject(FeedbackTicketTestBuilder.DEFAULT_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void movesToBacklogOnlyFromNew() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.BACKLOG)
                .build();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.moveToBacklog(FeedbackTicketTestBuilder.DEFAULT_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatesBothAdminTextsWholesale() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        FeedbackTicket updated = service.updateTexts(
                FeedbackTicketTestBuilder.DEFAULT_ID, "Thanks — accepted.", "Fix is planned.");

        assertSoftly(softly -> {
            softly.assertThat(updated.getReply()).isEqualTo("Thanks — accepted.");
            softly.assertThat(updated.getPrivateNote()).isEqualTo("Fix is planned.");
        });
    }

    @Test
    void anUnknownTicketIsNotFound() {
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveToBacklog(FeedbackTicketTestBuilder.DEFAULT_ID))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void aTransitionLoadsTheTicketById() {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(ticketRepository.findById(FeedbackTicketTestBuilder.DEFAULT_ID)).thenReturn(Optional.of(ticket));

        service.moveToBacklog(FeedbackTicketTestBuilder.DEFAULT_ID);

        verify(ticketRepository).findById(FeedbackTicketTestBuilder.DEFAULT_ID);
    }
}
