package dev.jordy.jordylab.feedback.rest.controller;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.service.AcceptTicketService;
import dev.jordy.jordylab.feedback.service.FeedbackTriageService;
import dev.jordy.jordylab.feedback.service.LinearUnavailableException;
import dev.jordy.jordylab.feedback.service.TicketNotFoundException;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin idea box (contracts/rest-api.md): inbox listing and state filter, the triage actions,
 * the two admin texts, Accept with its optional note, and every error code.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(FeedbackInboxController.class)
class FeedbackInboxControllerTest {

    private static final UUID TICKET_ID = FeedbackTicketTestBuilder.DEFAULT_ID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackTriageService triageService;

    @MockitoBean
    private AcceptTicketService acceptTicketService;

    @Test
    void getInboxListsTicketsWithTheAdminFields() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aDefaultFeedbackTicket();
        when(triageService.inbox(null)).thenReturn(List.of(ticket));

        mockMvc.perform(get("/api/feedback/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].id").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$.tickets[0].sender").value("Ada Palmer"))
                .andExpect(jsonPath("$.tickets[0].state").value("NEW"));
    }

    @Test
    void getInboxFiltersByState() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.BACKLOG)
                .build();
        when(triageService.inbox(FeedbackTicketState.BACKLOG)).thenReturn(List.of(ticket));

        mockMvc.perform(get("/api/feedback/inbox").param("state", "BACKLOG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].state").value("BACKLOG"));
    }

    @Test
    void getInboxRejectsAnUnknownStateFilter() throws Exception {
        mockMvc.perform(get("/api/feedback/inbox").param("state", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION"));
    }

    @Test
    void backlogMovesTheTicket() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.BACKLOG)
                .build();
        when(triageService.moveToBacklog(TICKET_ID)).thenReturn(ticket);

        mockMvc.perform(post("/api/feedback/inbox/{id}/backlog", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BACKLOG"));
    }

    @Test
    void rejectArchivesTheTicket() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.REJECTED)
                .build();
        when(triageService.reject(TICKET_ID)).thenReturn(ticket);

        mockMvc.perform(post("/api/feedback/inbox/{id}/reject", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"));
    }

    @Test
    void aTerminalTicketMapsToInvalidState() throws Exception {
        when(triageService.reject(TICKET_ID))
                .thenThrow(new IllegalStateException("only a NEW or BACKLOG ticket can be rejected"));

        mockMvc.perform(post("/api/feedback/inbox/{id}/reject", TICKET_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("INVALID_STATE"));
    }

    @Test
    void anUnknownTicketMapsToNotFound() throws Exception {
        when(triageService.moveToBacklog(TICKET_ID))
                .thenThrow(new TicketNotFoundException("missing"));

        mockMvc.perform(post("/api/feedback/inbox/{id}/backlog", TICKET_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("NOT_FOUND"));
    }

    @Test
    void updateTextsReplacesBothAdminTexts() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .reply("Thanks — accepted.")
                .privateNote("Fix is planned.")
                .build();
        when(triageService.updateTexts(TICKET_ID, "Thanks — accepted.", "Fix is planned.")).thenReturn(ticket);

        mockMvc.perform(put("/api/feedback/inbox/{id}/texts", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reply":"Thanks — accepted.","privateNote":"Fix is planned."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Thanks — accepted."))
                .andExpect(jsonPath("$.privateNote").value("Fix is planned."));
    }

    @Test
    void acceptReturnsTheAcceptedTicketWithItsLinearLink() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.ACCEPTED)
                .linearIssueId("issue-1")
                .linearIssueUrl("https://linear.app/jordylab/issue/JOR-101")
                .privateNote("Repro'd on CachyOS too.")
                .build();
        when(acceptTicketService.accept(TICKET_ID, "Repro'd on CachyOS too.")).thenReturn(ticket);

        mockMvc.perform(post("/api/feedback/inbox/{id}/accept", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"privateNote":"Repro'd on CachyOS too."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.linearIssueUrl").value("https://linear.app/jordylab/issue/JOR-101"));
    }

    @Test
    void acceptWithoutABodyCreatesTheIssueWithoutANote() throws Exception {
        FeedbackTicket ticket = FeedbackTicketTestBuilder.aFeedbackTicket()
                .state(FeedbackTicketState.ACCEPTED)
                .linearIssueId("issue-1")
                .linearIssueUrl("https://linear.app/jordylab/issue/JOR-101")
                .build();
        when(acceptTicketService.accept(eq(TICKET_ID), eq(null))).thenReturn(ticket);

        mockMvc.perform(post("/api/feedback/inbox/{id}/accept", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));
    }

    @Test
    void aLinearFailureMapsToUnavailableAndKeepsTheErrorOnTheTicket() throws Exception {
        when(acceptTicketService.accept(TICKET_ID, null))
                .thenThrow(new LinearUnavailableException("LINEAR_API_KEY is not configured"));

        mockMvc.perform(post("/api/feedback/inbox/{id}/accept", TICKET_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("LINEAR_UNAVAILABLE"));
    }
}
