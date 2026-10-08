package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostResponse;
import dev.jordy.jordylab.gamecatalog.service.HostService;
import dev.jordy.jordylab.gamecatalog.service.NameTakenException;
import dev.jordy.jordylab.gamecatalog.service.NameTooLongException;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(HostController.class)
class HostControllerTest {

    private static final UUID HOST_ID = UUID.fromString("5b8e2f4a-9c1d-4e7f-a3b6-0d2c4e6f8a1b");
    private static final String URL = "/api/gamecatalog/hosts/" + HOST_ID + "/display-name";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HostService hostService;

    @Test
    void namesTheHostAndReturnsTheLabel() throws Exception {
        when(hostService.setDisplayName(HOST_ID, "Living room PC")).thenReturn(
                Optional.of(new HostResponse(HOST_ID, "cachyos-htpc", "Living room PC", "Living room PC")));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Living room PC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(HOST_ID.toString()))
                .andExpect(jsonPath("$.hostname").value("cachyos-htpc"))
                .andExpect(jsonPath("$.label").value("Living room PC"));
    }

    @Test
    void aNullNameClearsIt() throws Exception {
        when(hostService.setDisplayName(HOST_ID, null)).thenReturn(
                Optional.of(new HostResponse(HOST_ID, "cachyos-htpc", null, "cachyos-htpc")));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.label").value("cachyos-htpc"));
    }

    @Test
    void aTakenNameIsAConflict() throws Exception {
        when(hostService.setDisplayName(HOST_ID, "Nintendo Switch")).thenThrow(new NameTakenException("Nintendo Switch"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nintendo Switch\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("NAME_TAKEN"));
    }

    @Test
    void aTooLongNameIsABadRequest() throws Exception {
        when(hostService.setDisplayName(HOST_ID, "x".repeat(41))).thenThrow(new NameTooLongException(40));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "x".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("NAME_TOO_LONG"));
    }

    @Test
    void anUnknownHostIsNotFound() throws Exception {
        when(hostService.setDisplayName(HOST_ID, "Anything")).thenReturn(Optional.empty());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Anything\"}"))
                .andExpect(status().isNotFound());
    }
}
