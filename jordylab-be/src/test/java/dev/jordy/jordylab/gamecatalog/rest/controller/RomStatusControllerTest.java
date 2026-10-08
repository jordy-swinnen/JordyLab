package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.service.InstallationNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.RomStatusNotApplicableException;
import dev.jordy.jordylab.gamecatalog.service.RomStatusService;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(RomStatusController.class)
class RomStatusControllerTest {

    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID COPY_ID = UUID.fromString("2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final String URL = "/api/gamecatalog/games/" + GAME_ID + "/installations/" + COPY_ID + "/rom-status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RomStatusService romStatusService;

    @Test
    void setsTheStatusAndReturnsThePlace() throws Exception {
        when(romStatusService.setStatus(GAME_ID, COPY_ID, RomStatus.BROKEN)).thenReturn(new PlaceResponse(
                PlaceResponse.PlaceKind.HOST_COPY, COPY_ID, UUID.randomUUID(), null, "Living room PC", "SNES", true,
                RomStatus.BROKEN, null, null));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.romStatus").value("BROKEN"))
                .andExpect(jsonPath("$.label").value("Living room PC"))
                .andExpect(jsonPath("$.installationId").value(COPY_ID.toString()));
    }

    @Test
    void aCopyThatIsNotEmulatedIsAConflict() throws Exception {
        when(romStatusService.setStatus(GAME_ID, COPY_ID, RomStatus.VALIDATED))
                .thenThrow(new RomStatusNotApplicableException());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ROM_STATUS_NOT_APPLICABLE"));
    }

    @Test
    void anUnknownOrInvisibleCopyIsNotFound() throws Exception {
        when(romStatusService.setStatus(GAME_ID, COPY_ID, RomStatus.BROKEN)).thenThrow(new InstallationNotFoundException());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingOrUnknownStatusIsABadRequest() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }
}
