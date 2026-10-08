package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.BrandFamily;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameListItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleSearchResult;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.KnownConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.service.AlreadyOnConsoleException;
import dev.jordy.jordylab.gamecatalog.service.ConsoleBulkService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleGameLookupException;
import dev.jordy.jordylab.gamecatalog.service.ConsoleGameService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.ConsoleService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(ConsoleController.class)
class ConsoleControllerTest {

    private static final UUID CONSOLE_ID = UUID.fromString("5c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final ConsoleResponse DOCK = new ConsoleResponse(CONSOLE_ID, "Nintendo Switch", BrandFamily.NINTENDO,
            PlatformChip.of("Nintendo Switch"), "Switch dock", "Switch dock", 3);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsoleService consoleService;

    @MockitoBean
    private ConsoleGameService consoleGameService;

    @MockitoBean
    private ConsoleBulkService consoleBulkService;

    @Test
    void knownConsolesAreFilteredByTheQuery() throws Exception {
        when(consoleService.known("nin")).thenReturn(List.of(new KnownConsoleResponse("Nintendo 64",
                BrandFamily.NINTENDO, 5, false)));

        mockMvc.perform(get("/api/gamecatalog/consoles/known").param("q", "nin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Nintendo 64"))
                .andExpect(jsonPath("$[0].generation").value(5))
                .andExpect(jsonPath("$[0].handheld").value(false));
    }

    @Test
    void listsConsolesWithTheirGameCounts() throws Exception {
        when(consoleService.list()).thenReturn(List.of(DOCK));

        mockMvc.perform(get("/api/gamecatalog/consoles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Switch dock"))
                .andExpect(jsonPath("$[0].gameCount").value(3))
                .andExpect(jsonPath("$[0].family").value("NINTENDO"))
                .andExpect(jsonPath("$[0].chip.background").value("#E60012"));
    }

    @Test
    void addingAConsoleReturns201WithTheConsole() throws Exception {
        when(consoleService.add("Nintendo Switch", "Switch dock")).thenReturn(DOCK);

        mockMvc.perform(post("/api/gamecatalog/consoles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"Nintendo Switch\",\"name\":\"Switch dock\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CONSOLE_ID.toString()));
    }

    @Test
    void aTakenNameIs409AndATooLongNameIs400() throws Exception {
        when(consoleService.add("Nintendo Switch", "taken")).thenThrow(new NameTakenException("taken"));
        when(consoleService.add("Nintendo Switch", "long")).thenThrow(new NameTooLongException(40));

        mockMvc.perform(post("/api/gamecatalog/consoles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"Nintendo Switch\",\"name\":\"taken\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("NAME_TAKEN"));
        mockMvc.perform(post("/api/gamecatalog/consoles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"Nintendo Switch\",\"name\":\"long\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("NAME_TOO_LONG"));
    }

    @Test
    void aBlankPlatformIsRejected() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/consoles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renamingReturnsTheUpdatedConsole() throws Exception {
        when(consoleService.rename(CONSOLE_ID, "Bedroom")).thenReturn(DOCK);

        mockMvc.perform(patch("/api/gamecatalog/consoles/{id}", CONSOLE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bedroom\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void impactAndRemovalWork() throws Exception {
        when(consoleService.impact(CONSOLE_ID)).thenReturn(new ConsoleImpactResponse(41, 12, 29));

        mockMvc.perform(get("/api/gamecatalog/consoles/{id}/impact", CONSOLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").value(41))
                .andExpect(jsonPath("$.alsoElsewhere").value(12))
                .andExpect(jsonPath("$.wouldBeRemoved").value(29));
        mockMvc.perform(delete("/api/gamecatalog/consoles/{id}", CONSOLE_ID)).andExpect(status().isNoContent());
        verify(consoleService).remove(CONSOLE_ID);
    }

    @Test
    void anUnknownConsoleIs404() throws Exception {
        when(consoleService.impact(CONSOLE_ID)).thenThrow(new ConsoleNotFoundException());

        mockMvc.perform(get("/api/gamecatalog/consoles/{id}/impact", CONSOLE_ID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("NOT_FOUND"));
    }

    @Test
    void searchListsIgdbMatchesOnTheConsolesPlatform() throws Exception {
        when(consoleGameService.search(CONSOLE_ID, "mario")).thenReturn(List.of(new ConsoleSearchResult(13427L,
                "Mario Kart 8 Deluxe", 2017, List.of("Racing"), "Nintendo", "https://c", "https://b", true)));

        mockMvc.perform(get("/api/gamecatalog/consoles/{id}/search", CONSOLE_ID).param("q", "mario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].igdbGameId").value(13427))
                .andExpect(jsonPath("$[0].alreadyOnConsole").value(true));
    }

    @Test
    void addingAGameByIgdbIdOrTitleReturns201() throws Exception {
        when(consoleGameService.add(CONSOLE_ID, 13427L, null))
                .thenReturn(new ConsoleGameResponse(GAME_ID, "Mario Kart 8 Deluxe", false));
        when(consoleGameService.add(CONSOLE_ID, null, "Celeste"))
                .thenReturn(new ConsoleGameResponse(GAME_ID, "Celeste", true));

        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games", CONSOLE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":13427}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.linkedExisting").value(false));
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games", CONSOLE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Celeste\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.linkedExisting").value(true));
    }

    @Test
    void aGameAlreadyOnTheConsoleIs409AndAnUnknownIgdbGameIs400() throws Exception {
        when(consoleGameService.add(CONSOLE_ID, null, "Celeste")).thenThrow(new AlreadyOnConsoleException());
        when(consoleGameService.add(CONSOLE_ID, 1L, null)).thenThrow(new ConsoleGameLookupException());

        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games", CONSOLE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Celeste\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("ALREADY_ON_CONSOLE"));
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games", CONSOLE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"igdbGameId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("GAME_NOT_FOUND"));
    }

    @Test
    void relinkingAndRemovingAGame() throws Exception {
        when(consoleGameService.relink(CONSOLE_ID, GAME_ID, 13427L))
                .thenReturn(new ConsoleGameResponse(GAME_ID, "Mario Kart 8 Deluxe", false));

        mockMvc.perform(patch("/api/gamecatalog/consoles/{id}/games/{gameId}", CONSOLE_ID, GAME_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"igdbGameId\":13427}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Mario Kart 8 Deluxe"));
        mockMvc.perform(delete("/api/gamecatalog/consoles/{id}/games/{gameId}", CONSOLE_ID, GAME_ID))
                .andExpect(status().isNoContent());
        verify(consoleGameService).remove(CONSOLE_ID, GAME_ID);
    }

    @Test
    void listsTheGamesOfAConsole() throws Exception {
        when(consoleGameService.list(CONSOLE_ID)).thenReturn(List.of(
                new ConsoleGameListItem(GAME_ID, "Celeste", 2018, "https://c", null)));

        mockMvc.perform(get("/api/gamecatalog/consoles/{id}/games", CONSOLE_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Celeste"));
    }

    @Test
    void bulkPreviewAndConfirm() throws Exception {
        when(consoleBulkService.preview(CONSOLE_ID, List.of("Celeste"))).thenReturn(new ConsoleBulkPreviewResponse(
                List.of(new ConsoleBulkPreviewResponse.Line("Celeste", ConsoleBulkPreviewResponse.Status.NO_MATCH, null))));
        when(consoleBulkService.confirm(CONSOLE_ID, List.of(new dev.jordy.jordylab.gamecatalog.rest.controller.model
                .ConsoleBulkConfirmRequest.Item("Celeste", null, "Celeste"))))
                .thenReturn(new ConsoleBulkSummaryResponse(List.of(), List.of("Celeste"), List.of()));

        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games/bulk/preview", CONSOLE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[\"Celeste\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].status").value("NO_MATCH"));
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games/bulk/confirm", CONSOLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"line\":\"Celeste\",\"title\":\"Celeste\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skipped[0]").value("Celeste"));
    }
}
