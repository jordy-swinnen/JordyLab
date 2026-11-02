package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackAnonymisationListenerTest {

    @Mock
    private FeedbackTicketRepository ticketRepository;

    private FeedbackAnonymisationListener listener;

    @BeforeEach
    void setUp() {
        listener = new FeedbackAnonymisationListener(ticketRepository);
    }

    @Test
    void clearsTheDisplayNameOnEveryTicketOfTheRemovedSender() {
        FeedbackTicket first = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        FeedbackTicket second = FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .build();
        when(ticketRepository.findBySenderSubject(FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT))
                .thenReturn(List.of(first, second));

        listener.on(new UserAccessRemoved(FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT));

        assertSoftly(softly -> {
            softly.assertThat(first.getSenderDisplayName()).isNull();
            softly.assertThat(second.getSenderDisplayName()).isNull();
            softly.assertThat(first.getSenderSubject()).isEqualTo(FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT);
        });
        verify(ticketRepository).saveAll(List.of(first, second));
    }
}
