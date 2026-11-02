package dev.jordy.jordylab.feedback.rest.controller;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.rest.controller.model.FeedbackAppView;
import dev.jordy.jordylab.feedback.rest.controller.model.FeedbackAppsResponse;
import dev.jordy.jordylab.feedback.rest.controller.model.GuestTicketView;
import dev.jordy.jordylab.feedback.rest.controller.model.GuestTicketsResponse;
import dev.jordy.jordylab.feedback.rest.controller.model.TicketSubmitRequest;
import dev.jordy.jordylab.feedback.service.FeedbackAppAccessService;
import dev.jordy.jordylab.feedback.service.FeedbackSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The guest feedback tab's API (contracts/rest-api.md, spec 017 US1): the apps this sender may
 * pick, submitting a ticket, and the sender's own tickets. {@code /api/feedback/apps} and
 * {@code /api/feedback/tickets} require the {@code guest} role in {@code SecurityConfig} — no
 * method-level checks needed here.
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackTicketController {

    private final FeedbackSubmissionService submissionService;
    private final FeedbackAppAccessService appAccessService;

    @GetMapping("/apps")
    public FeedbackAppsResponse getApps(Authentication authentication) {
        List<FeedbackAppView> apps = appAccessService.allowedApps(authentication.getAuthorities()).stream()
                .map(FeedbackAppView::from)
                .toList();

        return new FeedbackAppsResponse(apps);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestTicketView submit(@RequestBody TicketSubmitRequest request, Authentication authentication) {
        FeedbackTicket ticket = submissionService.submit(
                authentication.getName(),
                senderDisplayName(authentication),
                authentication.getAuthorities(),
                request.app(),
                request.type(),
                request.title(),
                request.description());

        return GuestTicketView.from(ticket);
    }

    @GetMapping("/tickets")
    public GuestTicketsResponse getOwnTickets(Authentication authentication) {
        List<GuestTicketView> tickets = submissionService.ownTickets(authentication.getName()).stream()
                .map(GuestTicketView::from)
                .toList();

        return new GuestTicketsResponse(tickets);
    }

    private String senderDisplayName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String name = jwtAuthenticationToken.getToken().getClaimAsString("name");
            if (StringUtils.hasText(name)) {
                return name;
            }

            return jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");
        }

        return null;
    }
}
