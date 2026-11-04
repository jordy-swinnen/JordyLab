package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The admin side of the idea box (spec 017 US2): the inbox list, the Backlog/Reject decisions and
 * the two admin texts. Transitions and their guards live on the entity; this service loads,
 * delegates and returns the updated aggregate.
 */
@Service
@RequiredArgsConstructor
public class FeedbackTriageService {

    private final FeedbackTicketRepository ticketRepository;

    public List<FeedbackTicket> inbox(FeedbackTicketState state) {
        return state == null
                ? ticketRepository.findAllByOrderByCreatedDateDesc()
                : ticketRepository.findAllByStateOrderByCreatedDateDesc(state);
    }

    @Transactional
    public FeedbackTicket moveToBacklog(UUID id) {
        FeedbackTicket ticket = require(id);
        ticket.moveToBacklog();

        return ticket;
    }

    @Transactional
    public FeedbackTicket reject(UUID id) {
        FeedbackTicket ticket = require(id);
        ticket.reject();

        return ticket;
    }

    @Transactional
    public FeedbackTicket updateTexts(UUID id, String reply, String privateNote) {
        validateTexts(reply, privateNote);
        FeedbackTicket ticket = require(id);
        ticket.updateTexts(reply, privateNote);

        return ticket;
    }

    private void validateTexts(String reply, String privateNote) {
        if (reply == null || privateNote == null
                || reply.length() > FeedbackTicket.MAX_TEXT_LENGTH
                || privateNote.length() > FeedbackTicket.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("reply and privateNote are required and bounded by the text limit");
        }
    }

    private FeedbackTicket require(UUID id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("ticket not found: " + id));
    }
}
