package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.service.GuestChatLimitService;
import dev.jordy.jordylab.settings.service.InvalidLimitException;
import dev.jordy.jordylab.shared.config.IdentityTestAuthentication;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsGuestChatLimitController.class)
class SettingsGuestChatLimitControllerTest {

    private static final String ADMIN_SUBJECT = IdentityTestAuthentication.ADMIN_SUBJECT;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GuestChatLimitService guestChatLimitService;

    @Test
    void reportsTheEnvironmentValueUntilADefaultIsSaved() throws Exception {
        when(guestChatLimitService.describeDefault()).thenReturn(
                new GuestChatLimitService.DefaultLimitView(20, GuestChatLimitService.LimitSource.ENVIRONMENT, null,
                        null));

        mockMvc.perform(get("/api/settings/guest-chat-limit").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(20))
                .andExpect(jsonPath("$.source").value("ENVIRONMENT"));
    }

    @Test
    void savesTheDefaultAndReportsItAsSaved() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-02T09:00:00Z");
        when(guestChatLimitService.saveDefault(25, ADMIN_SUBJECT)).thenReturn(
                new GuestChatLimitService.DefaultLimitView(25, GuestChatLimitService.LimitSource.DEFAULT_SAVED,
                        updatedAt, ADMIN_SUBJECT));

        mockMvc.perform(put("/api/settings/guest-chat-limit")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 25 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(25))
                .andExpect(jsonPath("$.source").value("DEFAULT_SAVED"));

        verify(guestChatLimitService).saveDefault(25, ADMIN_SUBJECT);
    }

    @Test
    void aNegativeLimitIsABadRequest() throws Exception {
        when(guestChatLimitService.saveDefault(-1, ADMIN_SUBJECT)).thenThrow(new InvalidLimitException(-1));

        mockMvc.perform(put("/api/settings/guest-chat-limit")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": -1 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_LIMIT"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return IdentityTestAuthentication.asAdmin();
    }
}
