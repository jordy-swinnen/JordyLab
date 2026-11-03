package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.repository.FeedbackTicketRepository;
import dev.jordy.jordylab.feedback.rest.client.LinearClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Accepting a ticket creates exactly one Linear issue (spec 017 US3, FR-009/FR-019).
 *
 * <p>The ticket is loaded under a pessimistic row lock and the lock is held across the Linear
 * call, so a double click serialises: the second request sees the stored issue id and returns it
 * without calling Linear again. The one remaining duplicate window — Linear created the issue but
 * this transaction never stored the id (a timeout or crash) — is closed by the retry path: when
 * the ticket carries a previous {@code acceptError}, Linear is searched first (team + exact title
 * + the {@code Source: idea box} marker) and a matching issue is adopted instead of created.
 *
 * <p>The transaction deliberately does not roll back on {@link LinearUnavailableException}: the
 * failure is recorded on the ticket (and any note the admin typed is kept), so the admin sees why
 * Accept failed and can retry.
 */
@Service
@RequiredArgsConstructor
public class AcceptTicketService {

    private final FeedbackTicketRepository ticketRepository;
    private final LinearClient linearClient;

    @Transactional(noRollbackFor = LinearUnavailableException.class)
    public FeedbackTicket accept(UUID id, String privateNote) {
        FeedbackTicket ticket = ticketRepository.findByIdForAccept(id)
                .orElseThrow(() -> new TicketNotFoundException("ticket not found: " + id));
        if (ticket.getLinearIssueId() != null) {
            return ticket;
        }
        if (ticket.getState() != FeedbackTicketState.NEW && ticket.getState() != FeedbackTicketState.BACKLOG) {
            throw new IllegalStateException("only a NEW or BACKLOG ticket can be accepted");
        }
        if (privateNote != null) {
            if (privateNote.length() > FeedbackTicket.MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("privateNote is too long");
            }
            ticket.updateTexts(ticket.getReply() == null ? "" : ticket.getReply(), privateNote);
        }
        try {
            LinearClient.LinearIssue issue = ticket.getAcceptError() == null
                    ? linearClient.createIssue(ticket, ticket.getPrivateNote())
                    : linearClient.findIssue(ticket.getTitle())
                            .orElseGet(() -> linearClient.createIssue(ticket, ticket.getPrivateNote()));
            ticket.markAccepted(issue.id(), issue.url());

            return ticket;
        } catch (LinearUnavailableException exception) {
            ticket.recordAcceptError(exception.getMessage());
            throw exception;
        }
    }
}
