package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.UserSessionNotFoundException;
import dev.jordy.jordylab.settings.service.UserSessionService;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsUserSessionsController.class)
class SettingsUserSessionsControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void listsSessionsWithTheirKind() throws Exception {
        when(userSessionService.listSessions(ADMIN_ID, USER_ID)).thenReturn(List.of(
                new UserSessionService.PersonSession("web-1", UserSessionService.SessionKind.WEB, false,
                        Instant.parse("2026-11-02T08:01:00Z"), Instant.parse("2026-11-02T08:40:00Z")),
                new UserSessionService.PersonSession("phone-1", UserSessionService.SessionKind.PHONE, true,
                        Instant.parse("2026-10-28T17:20:00Z"), Instant.parse("2026-11-01T22:05:00Z"))));

        mockMvc.perform(get("/api/settings/users/{id}/sessions", USER_ID).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].kind").value("WEB"))
                .andExpect(jsonPath("$.sessions[0].offline").value(false))
                .andExpect(jsonPath("$.sessions[1].kind").value("PHONE"))
                .andExpect(jsonPath("$.sessions[1].offline").value(true));
    }

    @Test
    void endsOneSession() throws Exception {
        mockMvc.perform(delete("/api/settings/users/{id}/sessions/{sessionId}", USER_ID, "web-1")
                        .param("kind", "WEB")
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userSessionService).endSession(ADMIN_ID, USER_ID, "web-1");
    }

    @Test
    void endsAllSessions() throws Exception {
        mockMvc.perform(post("/api/settings/users/{id}/sessions/end-all", USER_ID).with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userSessionService).endAllSessions(ADMIN_ID, USER_ID);
    }

    @Test
    void aKeycloakFailureIsAServiceUnavailable() throws Exception {
        when(userSessionService.listSessions(ADMIN_ID, USER_ID))
                .thenThrow(new KeycloakUnavailableException("down", null));

        mockMvc.perform(get("/api/settings/users/{id}/sessions", USER_ID).with(adminJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("KEYCLOAK_UNAVAILABLE"));
    }

    @Test
    void anUnknownSessionIsNotFound() throws Exception {
        doThrow(new UserSessionNotFoundException("nope")).when(userSessionService)
                .endSession(ADMIN_ID, USER_ID, "nope");

        mockMvc.perform(delete("/api/settings/users/{id}/sessions/{sessionId}", USER_ID, "nope").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("SESSION_NOT_FOUND"));
    }

    @Test
    void theAdminsOwnSessionsAreRefused() throws Exception {
        doThrow(new CannotChangeOwnAccountException(ADMIN_ID)).when(userSessionService)
                .listSessions(ADMIN_ID, ADMIN_ID);

        mockMvc.perform(get("/api/settings/users/{id}/sessions", ADMIN_ID).with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt.subject(ADMIN_ID.toString()));
    }
}
