package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameUpdateRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SwitchGameController.class)
class SwitchGameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SwitchGameService switchGameService;

    @Test
    void searchReturnsResults() throws Exception {
        when(switchGameService.search("mario")).thenReturn(List.of(
                new SwitchSearchResult(111L, "Mario Kart 8 Deluxe", 2017, List.of("Racing"), "Nintendo",
                        "https://cover.test/cover.jpg", "https://banner.test/banner.jpg")));

        mockMvc.perform(get("/api/gamecatalog/switch/search").param("query", "mario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].igdbGameId").value(111))
                .andExpect(jsonPath("$[0].title").value("Mario Kart 8 Deluxe"));
    }

    @Test
    void addFromIgdbReturnsCreated() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
        SwitchGameRequest expectedRequest = new SwitchGameRequest(111L, null, InstallationFormat.PHYSICAL);
        when(switchGameService.addFromIgdb(eq(expectedRequest)))
                .thenReturn(new SwitchGameResponse(gameId, "Mario Kart 8 Deluxe", "Nintendo Switch", "PHYSICAL"));

        mockMvc.perform(post("/api/gamecatalog/switch/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"igdbGameId": 111, "format": "PHYSICAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/gamecatalog/games/" + gameId))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.format").value("PHYSICAL"));
    }

    @Test
    void addManualReturnsCreated() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
        SwitchGameRequest expectedRequest = new SwitchGameRequest(null, "My Custom Game", InstallationFormat.DIGITAL);
        when(switchGameService.addManual(eq(expectedRequest)))
                .thenReturn(new SwitchGameResponse(gameId, "My Custom Game", "Nintendo Switch", "DIGITAL"));

        mockMvc.perform(post("/api/gamecatalog/switch/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "My Custom Game", "format": "DIGITAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Custom Game"))
                .andExpect(jsonPath("$.format").value("DIGITAL"));
    }

    @Test
    void addWithMissingFormatReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/switch/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"igdbGameId": 111}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFormatReturnsOk() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
        SwitchGameUpdateRequest expectedRequest = new SwitchGameUpdateRequest(InstallationFormat.DIGITAL, null);
        when(switchGameService.update(eq(gameId), eq(expectedRequest)))
                .thenReturn(new SwitchGameResponse(gameId, "My Game", "Nintendo Switch", "DIGITAL"));

        mockMvc.perform(patch("/api/gamecatalog/switch/games/" + gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format": "DIGITAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("DIGITAL"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

        mockMvc.perform(delete("/api/gamecatalog/switch/games/" + gameId))
                .andExpect(status().isNoContent());
    }
}
