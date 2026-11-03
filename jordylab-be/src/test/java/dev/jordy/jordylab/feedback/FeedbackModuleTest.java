package dev.jordy.jordylab.feedback;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackType;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import dev.jordy.jordylab.feedback.rest.client.LinearClient;
import dev.jordy.jordylab.feedback.service.AcceptTicketService;
import dev.jordy.jordylab.feedback.service.FeedbackLimitReachedException;
import dev.jordy.jordylab.feedback.service.FeedbackSubmissionService;
import dev.jordy.jordylab.feedback.service.FeedbackTriageService;
import dev.jordy.jordylab.feedback.service.LinearUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The module integration test (US1–US3): real persistence, the real services, and only the
 * outbound Linear client mocked. Proves the daily budget against actual rows, sender isolation,
 * the triage state machine, accept idempotency (double accept → one issue) and the retry path that
 * adopts an issue a failed attempt already created (FR-009/FR-019, SC-002).
 */
@ApplicationModuleTest
@Testcontainers
class FeedbackModuleTest {

    private static final String SENDER = "7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f";
    private static final String OTHER_SENDER = "2b4f6a8c-3d5e-4f60-9a1b-2c3d4e5f6a7b";
    private static final List<GrantedAuthority> GUEST = List.of(
            new SimpleGrantedAuthority("ROLE_guest"),
            new SimpleGrantedAuthority("ROLE_app-gamecatalog"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private FeedbackSubmissionService submissionService;

    @Autowired
    private FeedbackTriageService triageService;

    @Autowired
    private AcceptTicketService acceptTicketService;

    @Autowired
    private FeedbackTicketRepository ticketRepository;

    @MockitoBean
    private LinearClient linearClient;

    @MockitoBean
    private NtfyClient ntfyClient;

    @BeforeEach
    void cleanTickets() {
        ticketRepository.deleteAll();
    }

    @Test
    void submittingPushesExactlyOneNotificationWithoutTheTicketText() {
        submit(SENDER);

        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(ntfyClient).publish(
                org.mockito.ArgumentMatchers.eq("New feedback ticket"),
                bodyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("/settings/inbox"));
        assertThat(bodyCaptor.getValue()).isEqualTo("Ada Palmer sent a Bug report for the Game Catalog app.");
        org.mockito.Mockito.verifyNoMoreInteractions(ntfyClient);
    }

    @Test
    void everyGuestSeesOnlyTheirOwnTickets() {
        FeedbackTicket mine = submit(SENDER);
        FeedbackTicket theirs = submit(OTHER_SENDER);

        assertSoftly(softly -> {
            softly.assertThat(submissionService.ownTickets(SENDER))
                    .extracting(FeedbackTicket::getId)
                    .containsExactly(mine.getId());
            softly.assertThat(submissionService.ownTickets(OTHER_SENDER))
                    .extracting(FeedbackTicket::getId)
                    .containsExactly(theirs.getId());
        });
    }

    @Test
    void theSixthTicketOfTheDayIsRefused() {
        for (int submitted = 0; submitted < 5; submitted++) {
            submit(SENDER);
        }

        assertThatThrownBy(() -> submit(SENDER)).isInstanceOf(FeedbackLimitReachedException.class);
        assertThat(ticketRepository.count()).isEqualTo(5);
    }

    @Test
    void triageMovesTicketsAndRejectedIsFinal() {
        FeedbackTicket ticket = submit(SENDER);

        FeedbackTicket backlogged = triageService.moveToBacklog(ticket.getId());

        assertThat(backlogged.getState()).isEqualTo(FeedbackTicketState.BACKLOG);
        assertThat(triageService.inbox(FeedbackTicketState.BACKLOG))
                .extracting(FeedbackTicket::getId)
                .containsExactly(ticket.getId());

        FeedbackTicket rejected = triageService.reject(ticket.getId());

        assertThat(rejected.getState()).isEqualTo(FeedbackTicketState.REJECTED);
        assertThatThrownBy(() -> triageService.reject(ticket.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(triageService.inbox(null)).hasSize(1);
    }

    @Test
    void acceptingTwiceCreatesExactlyOneIssue() {
        FeedbackTicket ticket = submit(SENDER);
        ArgumentCaptor<FeedbackTicket> attemptedCaptor = ArgumentCaptor.forClass(FeedbackTicket.class);
        when(linearClient.createIssue(attemptedCaptor.capture(), eq("Repro'd on CachyOS too.")))
                .thenReturn(new LinearClient.LinearIssue("issue-1", "https://linear.app/jordylab/issue/JOR-101"));

        FeedbackTicket accepted = acceptTicketService.accept(ticket.getId(), "Repro'd on CachyOS too.");
        FeedbackTicket secondClick = acceptTicketService.accept(ticket.getId(), null);

        assertSoftly(softly -> {
            softly.assertThat(accepted.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(accepted.getLinearIssueId()).isEqualTo("issue-1");
            softly.assertThat(accepted.getLinearIssueUrl()).isEqualTo("https://linear.app/jordylab/issue/JOR-101");
            softly.assertThat(accepted.getPrivateNote()).isEqualTo("Repro'd on CachyOS too.");
            softly.assertThat(secondClick.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(secondClick.getLinearIssueId()).isEqualTo("issue-1");
        });
        verify(linearClient, times(1)).createIssue(attemptedCaptor.capture(), eq("Repro'd on CachyOS too."));
    }

    @Test
    void aFailedAcceptKeepsTheTicketRetryableAndTheRetryAdoptsTheCreatedIssue() {
        FeedbackTicket ticket = submit(SENDER);
        ArgumentCaptor<FeedbackTicket> attemptedCaptor = ArgumentCaptor.forClass(FeedbackTicket.class);
        when(linearClient.createIssue(attemptedCaptor.capture(), isNull()))
                .thenThrow(new LinearUnavailableException("Linear is unreachable"));

        assertThatThrownBy(() -> acceptTicketService.accept(ticket.getId(), null))
                .isInstanceOf(LinearUnavailableException.class);

        FeedbackTicket afterFailure = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(afterFailure.getState()).isEqualTo(FeedbackTicketState.NEW);
            softly.assertThat(afterFailure.getAcceptError()).isEqualTo("Linear is unreachable");
        });

        when(linearClient.findIssue(ticket.getTitle())).thenReturn(
                Optional.of(new LinearClient.LinearIssue("issue-2", "https://linear.app/jordylab/issue/JOR-102")));

        FeedbackTicket retried = acceptTicketService.accept(ticket.getId(), null);

        assertSoftly(softly -> {
            softly.assertThat(retried.getState()).isEqualTo(FeedbackTicketState.ACCEPTED);
            softly.assertThat(retried.getLinearIssueId()).isEqualTo("issue-2");
            softly.assertThat(retried.getAcceptError()).isNull();
        });
        verify(linearClient).findIssue(ticket.getTitle());
        verify(linearClient, times(1)).createIssue(attemptedCaptor.capture(), isNull());
    }

    private FeedbackTicket submit(String sender) {
        return submissionService.submit(sender, "Ada Palmer", GUEST, FeedbackApp.GAME_CATALOG,
                FeedbackType.BUG_REPORT, "Grid loses my scroll position",
                "When I open a game and go back, the grid jumps to the top.");
    }

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.data.jpa.repository.config.EnableJpaAuditing
    static class ClockTestConfiguration {
        @org.springframework.context.annotation.Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
