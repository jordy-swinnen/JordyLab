package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkLine;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameUpdateRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import dev.jordy.jordylab.gamecatalog.service.SwitchBulkService;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameAlreadyPresentException;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @MockitoBean
    private SwitchBulkService switchBulkService;

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

    @Test
    void bulkPreviewReturnsOneReviewedLinePerTitle() throws Exception {
        when(switchBulkService.preview("Pikmin 4\nSome Indie Gem")).thenReturn(new SwitchBulkPreviewResponse(List.of(
                new SwitchBulkLine("Pikmin 4", SwitchBulkStatus.MATCH, List.of(new SwitchSearchResult(111L,
                        "Pikmin 4", 2023, List.of("Strategy"), "Nintendo", null, null)), null, true),
                new SwitchBulkLine("Some Indie Gem", SwitchBulkStatus.NO_MATCH, List.of(), null, false))));

        mockMvc.perform(post("/api/gamecatalog/switch/bulk/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Pikmin 4\\nSome Indie Gem"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].status").value("MATCH"))
                .andExpect(jsonPath("$.lines[0].candidates[0].igdbGameId").value(111))
                .andExpect(jsonPath("$.lines[1].status").value("NO_MATCH"))
                .andExpect(jsonPath("$.lines[1].include").value(false));
    }

    @Test
    void bulkPreviewOfTooManyLinesIsABadRequest() throws Exception {
        when(switchBulkService.preview("lots")).thenThrow(new IllegalArgumentException("Paste at most 100 titles"));

        mockMvc.perform(post("/api/gamecatalog/switch/bulk/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "lots"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Paste at most 100 titles"));
    }

    @Test
    void bulkPreviewOfAnEmptyPasteIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/switch/bulk/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkConfirmReturnsTheSummary() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
        SwitchBulkConfirmRequest expected = new SwitchBulkConfirmRequest(List.of(
                new SwitchBulkConfirmRequest.Item("Pikmin 4", 111L, null, InstallationFormat.DIGITAL)));
        when(switchBulkService.confirm(eq(expected))).thenReturn(new SwitchBulkConfirmResponse(
                List.of(new SwitchGameResponse(gameId, "Pikmin 4", "Nintendo Switch", "DIGITAL")),
                List.of("Splatoon 3"), List.of(new SwitchBulkConfirmResponse.Skipped("Gone", "IGDB game not found"))));

        mockMvc.perform(post("/api/gamecatalog/switch/bulk/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"line": "Pikmin 4", "igdbGameId": 111, "format": "DIGITAL"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added[0].gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.alreadyPresent[0]").value("Splatoon 3"))
                .andExpect(jsonPath("$.skipped[0].reason").value("IGDB game not found"));
    }

    @Test
    void bulkConfirmWithoutAFormatIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/switch/bulk/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"line": "Pikmin 4", "igdbGameId": 111}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingAGameAlreadyOnTheSwitchIsAConflict() throws Exception {
        when(switchGameService.addManual(eq(new SwitchGameRequest(null, "Pikmin 4", InstallationFormat.PHYSICAL))))
                .thenThrow(new SwitchGameAlreadyPresentException("Switch installation already exists"));

        mockMvc.perform(post("/api/gamecatalog/switch/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Pikmin 4", "format": "PHYSICAL"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void removingAnUnknownGameIsNotFound() throws Exception {
        UUID gameId = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
        doThrow(new SwitchGameNotFoundException("Game not found")).when(switchGameService).delete(gameId);

        mockMvc.perform(delete("/api/gamecatalog/switch/games/" + gameId))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingVirtualSwitchSourceStaysAServerError() throws Exception {
        when(switchGameService.addManual(eq(new SwitchGameRequest(null, "Pikmin 4", InstallationFormat.PHYSICAL))))
                .thenThrow(new IllegalStateException("Virtual Switch source is missing"));

        // Not mapped to 409: MockMvc rethrows unhandled exceptions, which a real server answers with 500.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/gamecatalog/switch/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Pikmin 4", "format": "PHYSICAL"}
                                """)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Virtual Switch source is missing");
    }
}
