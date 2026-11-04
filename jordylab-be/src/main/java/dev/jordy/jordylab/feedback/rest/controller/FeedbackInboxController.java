package dev.jordy.jordylab.feedback.rest.controller;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.rest.controller.model.AcceptRequest;
import dev.jordy.jordylab.feedback.rest.controller.model.InboxTicketView;
import dev.jordy.jordylab.feedback.rest.controller.model.InboxTicketsResponse;
import dev.jordy.jordylab.feedback.rest.controller.model.TextsUpdateRequest;
import dev.jordy.jordylab.feedback.service.AcceptTicketService;
import dev.jordy.jordylab.feedback.service.FeedbackTriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The admin's idea box (contracts/rest-api.md, spec 017 US2/US3): the inbox list, Backlog/Reject,
 * the two admin texts and Accept. {@code /api/feedback/inbox/**} requires {@code admin} in
 * {@code SecurityConfig} — no method-level checks needed here.
 */
@RestController
@RequestMapping("/api/feedback/inbox")
@RequiredArgsConstructor
public class FeedbackInboxController {

    private final FeedbackTriageService triageService;
    private final AcceptTicketService acceptTicketService;

    @GetMapping
    public InboxTicketsResponse getInbox(@RequestParam(required = false) String state) {
        List<InboxTicketView> tickets = triageService.inbox(parseState(state)).stream()
                .map(InboxTicketView::from)
                .toList();

        return new InboxTicketsResponse(tickets);
    }

    @PostMapping("/{id}/accept")
    public InboxTicketView accept(@PathVariable UUID id, @RequestBody(required = false) AcceptRequest request) {
        FeedbackTicket ticket = acceptTicketService.accept(id, request == null ? null : request.privateNote());

        return InboxTicketView.from(ticket);
    }

    @PostMapping("/{id}/backlog")
    public InboxTicketView moveToBacklog(@PathVariable UUID id) {
        return InboxTicketView.from(triageService.moveToBacklog(id));
    }

    @PostMapping("/{id}/reject")
    public InboxTicketView reject(@PathVariable UUID id) {
        return InboxTicketView.from(triageService.reject(id));
    }

    @PutMapping("/{id}/texts")
    public InboxTicketView updateTexts(@PathVariable UUID id, @RequestBody TextsUpdateRequest request) {
        return InboxTicketView.from(triageService.updateTexts(id, request.reply(), request.privateNote()));
    }

    private FeedbackTicketState parseState(String state) {
        if (!StringUtils.hasText(state)) {
            return null;
        }
        try {
            return FeedbackTicketState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown state: " + state, exception);
        }
    }
}
