package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.FeedbackProperties;
import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackType;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

/**
 * The guest side of the idea box (spec 017 US1): validate a submission against the sender's
 * allowed apps and the daily budget, store it as {@code NEW}, and announce it through
 * {@link FeedbackTicketCreated} for the admin notification.
 */
@Service
@RequiredArgsConstructor
public class FeedbackSubmissionService {

    private final FeedbackTicketRepository ticketRepository;
    private final FeedbackAppAccessService appAccessService;
    private final FeedbackProperties properties;
    private final Clock clock;

    @Transactional
    public FeedbackTicket submit(String senderSubject, String senderDisplayName,
            Collection<? extends GrantedAuthority> authorities, FeedbackApp app, FeedbackType type,
            String title, String description) {
        appAccessService.requireAllowed(app, authorities);
        long submittedToday = ticketRepository.countBySenderSubjectAndCreatedDateAfter(
                senderSubject, startOfToday());
        if (submittedToday >= properties.dailyTicketLimit()) {
            throw new FeedbackLimitReachedException("daily feedback limit reached");
        }
        FeedbackTicket ticket = FeedbackTicket.builder()
                .app(app)
                .type(type)
                .title(title)
                .description(description)
                .senderSubject(senderSubject)
                .senderDisplayName(StringUtils.hasText(senderDisplayName) ? senderDisplayName.trim() : null)
                .state(FeedbackTicketState.NEW)
                .build();

        return ticketRepository.save(ticket);
    }

    public List<FeedbackTicket> ownTickets(String senderSubject) {
        return ticketRepository.findBySenderSubjectOrderByCreatedDateDesc(senderSubject);
    }

    private Instant startOfToday() {
        ZoneId zone = ZoneId.of(properties.dailyLimitZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        return today.atStartOfDay(zone).toInstant();
    }
}
