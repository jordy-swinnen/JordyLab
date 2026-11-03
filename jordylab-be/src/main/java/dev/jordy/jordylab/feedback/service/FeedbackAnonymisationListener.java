package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A removed account's tickets stay as history, without personal data (spec 017 edge case): the
 * sender's display-name snapshot is dropped, the feedback itself is kept for the admin.
 */
@Service
@RequiredArgsConstructor
public class FeedbackAnonymisationListener {

    private final FeedbackTicketRepository ticketRepository;

    @ApplicationModuleListener
    void on(UserAccessRemoved event) {
        List<FeedbackTicket> tickets = ticketRepository.findBySenderSubject(event.userSubject());
        tickets.forEach(FeedbackTicket::anonymiseSender);
        ticketRepository.saveAll(tickets);
    }
}
