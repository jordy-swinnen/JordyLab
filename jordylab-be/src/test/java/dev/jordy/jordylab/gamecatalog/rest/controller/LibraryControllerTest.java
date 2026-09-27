package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySourceStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatusResponse;
import dev.jordy.jordylab.gamecatalog.service.LibraryStatusService;
import dev.jordy.jordylab.gamecatalog.service.SteamLibrarySyncService;
import dev.jordy.jordylab.gamecatalog.service.SteamNotConfiguredException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-09-27T10:00:04Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SteamLibrarySyncService librarySyncService;

    @MockitoBean
    private LibraryStatusService libraryStatusService;

    @Test
    void ownedSyncReturnsTheRun() throws Exception {
        when(librarySyncService.syncOwned(eq(false))).thenReturn(aRun(LibrarySource.OWNED, LibrarySyncOutcome.APPLIED, null));

        mockMvc.perform(post("/api/gamecatalog/library/steam/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.librarySource").value("OWNED"))
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.entriesSubmitted").value(412))
                .andExpect(jsonPath("$.metadataCalls").value(25))
                .andExpect(jsonPath("$.aiCalls").value(6));
    }

    @Test
    void ownedSyncWhenNotConfiguredReturns409() throws Exception {
        when(librarySyncService.syncOwned(eq(false)))
                .thenThrow(new SteamNotConfiguredException("not configured"));

        mockMvc.perform(post("/api/gamecatalog/library/steam/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("STEAM_NOT_CONFIGURED"));
    }

    @Test
    void ownedSyncFailureReturns502() throws Exception {
        when(librarySyncService.syncOwned(eq(false)))
                .thenReturn(aRun(LibrarySource.OWNED, LibrarySyncOutcome.FAILED, "STEAM_SYNC_FAILED"));

        mockMvc.perform(post("/api/gamecatalog/library/steam/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("STEAM_SYNC_FAILED"));
    }

    @Test
    void familySyncReturnsTheRun() throws Exception {
        when(librarySyncService.syncFamily(eq("token"), eq(false)))
                .thenReturn(aRun(LibrarySource.FAMILY, LibrarySyncOutcome.APPLIED, null));

        mockMvc.perform(post("/api/gamecatalog/library/steam-family/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.librarySource").value("FAMILY"));
    }

    @Test
    void familySyncWithBlankTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/library/steam-family/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accessToken": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("FAMILY_TOKEN_REQUIRED"));
    }

    @Test
    void familySyncFailureReturns502WithErrorCode() throws Exception {
        when(librarySyncService.syncFamily(eq("token"), eq(false)))
                .thenReturn(aRun(LibrarySource.FAMILY, LibrarySyncOutcome.FAILED, "TOKEN_EXPIRED"));

        mockMvc.perform(post("/api/gamecatalog/library/steam-family/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("FAMILY_SYNC_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    void statusReportsWhetherOwnedIsConfigured() throws Exception {
        LibrarySourceStatus empty = new LibrarySourceStatus(null, null, 0, 0, 0, false, false);
        when(libraryStatusService.status()).thenReturn(new LibraryStatusResponse(empty, empty, true));

        mockMvc.perform(get("/api/gamecatalog/library/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedConfigured").value(true))
                .andExpect(jsonPath("$.owned.entriesActive").value(0));
    }

    @Language("JSON")
    private String tokenBody() {
        return """
                {"accessToken": "token"}
                """;
    }

    private LibrarySyncRun aRun(LibrarySource source, LibrarySyncOutcome outcome, String errorCode) {
        return LibrarySyncRun.builder()
                .librarySource(source)
                .startedAt(STARTED_AT)
                .finishedAt(FINISHED_AT)
                .outcome(outcome)
                .entriesSubmitted(412)
                .entriesAdded(87)
                .metadataCalls(25)
                .aiCalls(6)
                .errorCode(errorCode)
                .build();
    }
}
