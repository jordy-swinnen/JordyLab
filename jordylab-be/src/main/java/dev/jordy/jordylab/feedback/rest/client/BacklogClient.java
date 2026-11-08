package dev.jordy.jordylab.feedback.rest.client;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.service.BacklogUnavailableException;

import java.util.Optional;

/**
 * The backlog an accepted idea-box ticket lands in (spec 022, contracts/kaneo-client.md). Two
 * implementations sit behind {@code jordylab.feedback.backlog.provider}: {@link KaneoClient} (the
 * default) and {@link LinearClient}, kept until the follow-up pull request that removes Linear.
 * Every failure is a {@link BacklogUnavailableException}.
 */
public interface BacklogClient {

    /**
     * Creates the one backlog ticket for an accepted ticket, labelled with its App and Type.
     */
    BacklogTicket createTicket(FeedbackTicket ticket, String privateNote);

    /**
     * The adopt-on-retry lookup by the ticket's title: a previous accept may have created the ticket before failing
     * locally. Only ever called for a ticket that carries an {@code acceptError}. Takes the whole ticket because
     * an adopted backlog ticket still needs its App and Type labels attached.
     */
    Optional<BacklogTicket> findTicket(FeedbackTicket ticket);

    /**
     * The created ticket's identity, as stored on the feedback ticket ({@code backlog_ticket_id} and url).
     */
    record BacklogTicket(String id, String url) {
    }
}
