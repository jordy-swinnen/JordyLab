package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.RefreshRun;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.service.autofill.CostConfirmationRequiredException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshAlreadyActiveException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunNotRunningException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunService;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(RefreshRunController.class)
class RefreshRunControllerTest {

    private static final UUID RUN_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    private static final Instant STARTED = Instant.parse("2026-10-07T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshRunService refreshRunService;

    private RefreshRun aRun(RefreshRunKind kind, int total) {
        return RefreshRun.builder().id(RUN_ID).kind(kind).total(total).startedBy("admin-subject").startedAt(STARTED).build();
    }

    @Test
    void startingARunAnswers201WithItsProgress() throws Exception {
        when(refreshRunService.start(RefreshRunKind.DATA, "admin-subject", false)).thenReturn(aRun(RefreshRunKind.DATA, 228));

        mockMvc.perform(post("/api/gamecatalog/refresh-runs").with(jwt().jwt(token -> token.subject("admin-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DATA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.kind").value("DATA"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.total").value(228))
                .andExpect(jsonPath("$.processed").value(0));
    }

    @Test
    void theAiRunPassesTheConfirmationOn() throws Exception {
        when(refreshRunService.start(RefreshRunKind.AI, "admin-subject", true)).thenReturn(aRun(RefreshRunKind.AI, 12));

        mockMvc.perform(post("/api/gamecatalog/refresh-runs").with(jwt().jwt(token -> token.subject("admin-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"AI\",\"confirmCost\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("AI"));
    }

    @Test
    void withoutTheCostConfirmationTheAiRunAnswers400WithTheGameCount() throws Exception {
        when(refreshRunService.start(RefreshRunKind.AI, "admin-subject", false))
                .thenThrow(new CostConfirmationRequiredException(228));

        mockMvc.perform(post("/api/gamecatalog/refresh-runs").with(jwt().jwt(token -> token.subject("admin-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"AI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("COST_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.games").value(228));
    }

    @Test
    void aSecondStartWhileRunningIsAConflict() throws Exception {
        when(refreshRunService.start(RefreshRunKind.DATA, "admin-subject", false)).thenThrow(new RefreshAlreadyActiveException());

        mockMvc.perform(post("/api/gamecatalog/refresh-runs").with(jwt().jwt(token -> token.subject("admin-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DATA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RUN_ALREADY_ACTIVE"));
    }

    @Test
    void anUnknownKindIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/refresh-runs").with(jwt().jwt(token -> token.subject("admin-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"EVERYTHING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currentIsTheLatestRunOrNoContent() throws Exception {
        when(refreshRunService.current(RefreshRunKind.AI)).thenReturn(Optional.of(aRun(RefreshRunKind.AI, 12)));
        when(refreshRunService.current(RefreshRunKind.DATA)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gamecatalog/refresh-runs/current").param("kind", "AI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12));
        mockMvc.perform(get("/api/gamecatalog/refresh-runs/current").param("kind", "DATA"))
                .andExpect(status().isNoContent());
    }

    @Test
    void stoppingReturnsTheRunWithTheStopFlag() throws Exception {
        RefreshRun run = aRun(RefreshRunKind.DATA, 5);
        run.requestStop();
        when(refreshRunService.stop(RUN_ID)).thenReturn(run);

        mockMvc.perform(post("/api/gamecatalog/refresh-runs/{id}/stop", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopRequested").value(true));
    }

    @Test
    void stoppingAFinishedRunIsAConflictAndAnUnknownOneIsNotFound() throws Exception {
        when(refreshRunService.stop(RUN_ID)).thenThrow(new RefreshRunNotRunningException());
        UUID unknown = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(refreshRunService.stop(unknown)).thenThrow(new RefreshRunNotFoundException());

        mockMvc.perform(post("/api/gamecatalog/refresh-runs/{id}/stop", RUN_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("RUN_NOT_RUNNING"));
        mockMvc.perform(post("/api/gamecatalog/refresh-runs/{id}/stop", unknown))
                .andExpect(status().isNotFound());
    }
}
