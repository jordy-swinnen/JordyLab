package dev.jordy.jordylab.feedback.domain.repository;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DataJpaTest
@Testcontainers
@Import(JpaConfiguration.class)
class FeedbackRepositoryTest {

    private static final String OTHER_SENDER = "2b4f6a8c-3d5e-4f60-9a1b-2c3d4e5f6a7b";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private FeedbackTicketRepository ticketRepository;

    @BeforeEach
    void cleanTickets() {
        ticketRepository.deleteAll();
    }

    @Test
    void savesATicketAndFindsItBySenderNewestFirst() throws InterruptedException {
        FeedbackTicket older = ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .title("Older ticket")
                .build());
        Thread.sleep(5);
        FeedbackTicket newer = ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .title("Newer ticket")
                .build());
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .senderSubject(OTHER_SENDER)
                .build());

        List<FeedbackTicket> tickets = ticketRepository
                .findBySenderSubjectOrderByCreatedDateDesc(FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT);

        assertThat(tickets).extracting(FeedbackTicket::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void countsTheSendersTicketsAfterAThresholdForTheDailyLimit() {
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket().id(null).build());
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .senderSubject(OTHER_SENDER)
                .build());

        long today = ticketRepository.countBySenderSubjectAndCreatedDateAfter(
                FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT, Instant.now().minusSeconds(60));
        long afterTheWindow = ticketRepository.countBySenderSubjectAndCreatedDateAfter(
                FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT, Instant.now().plusSeconds(60));

        assertSoftly(softly -> {
            softly.assertThat(today).isEqualTo(1L);
            softly.assertThat(afterTheWindow).isZero();
        });
    }

    @Test
    void findsTheInboxNewestFirstAndFiltersByState() throws InterruptedException {
        FeedbackTicket newTicket = ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .title("New one")
                .build());
        Thread.sleep(5);
        FeedbackTicket backlogTicket = ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .title("Backlogged one")
                .state(FeedbackTicketState.BACKLOG)
                .build());

        assertSoftly(softly -> {
            softly.assertThat(ticketRepository.findAllByOrderByCreatedDateDesc())
                    .extracting(FeedbackTicket::getId)
                    .containsExactly(backlogTicket.getId(), newTicket.getId());
            softly.assertThat(ticketRepository.findAllByStateOrderByCreatedDateDesc(FeedbackTicketState.NEW))
                    .extracting(FeedbackTicket::getId)
                    .containsExactly(newTicket.getId());
            softly.assertThat(ticketRepository.findAllByStateOrderByCreatedDateDesc(FeedbackTicketState.BACKLOG))
                    .extracting(FeedbackTicket::getId)
                    .containsExactly(backlogTicket.getId());
        });
    }

    @Test
    void loadsOneTicketUnderTheAcceptLock() {
        FeedbackTicket saved = ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket().id(null).build());

        Optional<FeedbackTicket> locked = ticketRepository.findByIdForAccept(saved.getId());

        assertThat(locked).isPresent();
        assertThat(locked.orElseThrow().getTitle()).isEqualTo(FeedbackTicketTestBuilder.DEFAULT_TITLE);
    }

    @Test
    void findsEveryTicketOfASenderForAnonymisation() {
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket().id(null).build());
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .state(FeedbackTicketState.REJECTED)
                .build());
        ticketRepository.save(FeedbackTicketTestBuilder.aFeedbackTicket()
                .id(null)
                .senderSubject(OTHER_SENDER)
                .build());

        List<FeedbackTicket> tickets = ticketRepository
                .findBySenderSubject(FeedbackTicketTestBuilder.DEFAULT_SENDER_SUBJECT);

        assertThat(tickets).hasSize(2);
    }
}
