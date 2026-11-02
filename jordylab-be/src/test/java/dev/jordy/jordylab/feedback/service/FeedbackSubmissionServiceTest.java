package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.FeedbackProperties;
import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackType;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The guest half of US1: validation, the app allow-list, the five-per-day budget (reset at
 * midnight in the configured zone — the fixed clock puts the local date at 2026-11-03 in
 * Brussels), and the notification event's payload.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackSubmissionServiceTest {

    private static final Collection<GrantedAuthority> GUEST = List.of(new SimpleGrantedAuthority("ROLE_guest"));
    private static final Collection<GrantedAuthority> ADMIN = List.of(new SimpleGrantedAuthority("ROLE_admin"));
    private static final String SUBJECT = "7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f";
    // Fixed 2026-11-02T23:30Z == 2026-11-03 00:30 in Brussels, so the window starts at local midnight.
    private static final Instant WINDOW_START = Instant.parse("2026-11-02T23:00:00Z");

    @Mock
    private FeedbackTicketRepository ticketRepository;

    private FeedbackSubmissionService service;

    @BeforeEach
    void setUp() {
        FeedbackProperties properties = new FeedbackProperties(5, "Europe/Brussels", null);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), ZoneOffset.UTC);
        service = new FeedbackSubmissionService(ticketRepository, new FeedbackAppAccessService(), properties, clock);
    }

    @Test
    void submitsANewTicketAndRegistersTheNotificationEvent() {
        ArgumentCaptor<FeedbackTicket> saveCaptor = ArgumentCaptor.forClass(FeedbackTicket.class);
        when(ticketRepository.countBySenderSubjectAndCreatedDateAfter(eq(SUBJECT), eq(WINDOW_START))).thenReturn(0L);
        when(ticketRepository.save(saveCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        FeedbackTicket ticket = service.submit(SUBJECT, "Ada Palmer", GUEST, FeedbackApp.GAME_CATALOG,
                FeedbackType.BUG_REPORT, "Grid loses my scroll position", "When I open a game and go back.");

        assertSoftly(softly -> {
            softly.assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.NEW);
            softly.assertThat(ticket.getSenderSubject()).isEqualTo(SUBJECT);
            softly.assertThat(ticket.getSenderDisplayName()).isEqualTo("Ada Palmer");
            softly.assertThat(ticket.getTitle()).isEqualTo("Grid loses my scroll position");
            softly.assertThat(ticket.getDescription()).isEqualTo("When I open a game and go back.");
        });
        verify(ticketRepository).save(ticket);
    }

    @Test
    void aMissingDisplayNameFallsBackToAGuestInTheEvent() {
        ArgumentCaptor<FeedbackTicket> saveCaptor = ArgumentCaptor.forClass(FeedbackTicket.class);
        when(ticketRepository.countBySenderSubjectAndCreatedDateAfter(eq(SUBJECT), eq(WINDOW_START))).thenReturn(0L);
        when(ticketRepository.save(saveCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        FeedbackTicket ticket = service.submit(SUBJECT, "   ", GUEST, FeedbackApp.GAME_CATALOG,
                FeedbackType.IDEA, "Dark mode for the grid", "Please.");

        assertThat(ticket.getSenderDisplayName()).isNull();
    }

    @Test
    void refusesAnAppTheSenderMayNotUse() {
        assertThatThrownBy(() -> service.submit(SUBJECT, "Ada Palmer", ADMIN, FeedbackApp.GAME_CATALOG,
                FeedbackType.BUG_REPORT, "Title", "Description"))
                .isInstanceOf(FeedbackAppNotAllowedException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void refusesTheSixthTicketOfTheDay() {
        when(ticketRepository.countBySenderSubjectAndCreatedDateAfter(eq(SUBJECT), eq(WINDOW_START))).thenReturn(5L);

        assertThatThrownBy(() -> service.submit(SUBJECT, "Ada Palmer", GUEST, FeedbackApp.GAME_CATALOG,
                FeedbackType.BUG_REPORT, "Title", "Description"))
                .isInstanceOf(FeedbackLimitReachedException.class);
    }

    @Test
    void acceptsTheFifthTicketOfTheDay() {
        ArgumentCaptor<FeedbackTicket> saveCaptor = ArgumentCaptor.forClass(FeedbackTicket.class);
        when(ticketRepository.countBySenderSubjectAndCreatedDateAfter(eq(SUBJECT), eq(WINDOW_START))).thenReturn(4L);
        when(ticketRepository.save(saveCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        FeedbackTicket ticket = service.submit(SUBJECT, "Ada Palmer", GUEST, FeedbackApp.GAME_CATALOG,
                FeedbackType.IMPROVEMENT, "Keyboard shortcut", "For the grid.");

        assertThat(ticket.getState()).isEqualTo(FeedbackTicketState.NEW);
    }

    @Test
    void listsOnlyTheSendersOwnTickets() {
        FeedbackTicket own = FeedbackTicket.builder()
                .app(FeedbackApp.GAME_CATALOG)
                .type(FeedbackType.IDEA)
                .title("Title")
                .description("Description")
                .senderSubject(SUBJECT)
                .state(FeedbackTicketState.NEW)
                .build();
        when(ticketRepository.findBySenderSubjectOrderByCreatedDateDesc(SUBJECT)).thenReturn(List.of(own));

        assertThat(service.ownTickets(SUBJECT)).containsExactly(own);
    }
}
