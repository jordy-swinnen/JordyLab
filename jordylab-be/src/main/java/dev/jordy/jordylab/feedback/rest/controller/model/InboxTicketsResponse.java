package dev.jordy.jordylab.feedback.rest.controller.model;

import java.util.List;

public record InboxTicketsResponse(List<InboxTicketView> tickets) {
}
