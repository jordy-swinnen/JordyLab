package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.FamilySyncRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatusResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySyncRunResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.OwnedSyncRequest;
import dev.jordy.jordylab.gamecatalog.service.LibraryStatusService;
import dev.jordy.jordylab.gamecatalog.service.SteamLibrarySyncService;
import dev.jordy.jordylab.gamecatalog.service.SteamNotConfiguredException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamecatalog/library")
@RequiredArgsConstructor
public class LibraryController {

    private final SteamLibrarySyncService librarySyncService;
    private final LibraryStatusService libraryStatusService;

    @PostMapping("/steam/sync")
    public ResponseEntity<Object> syncOwned(@RequestBody(required = false) OwnedSyncRequest request) {
        LibrarySyncRun run = librarySyncService.syncOwned(request != null && Boolean.TRUE.equals(request.force()));

        return run.getOutcome() == LibrarySyncOutcome.FAILED
                ? ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new LibraryErrorBody("STEAM_SYNC_FAILED", run.getErrorCode()))
                : ResponseEntity.ok(toResponse(run));
    }
    @PostMapping("/steam-family/sync")
    public ResponseEntity<Object> syncFamily(@Valid @RequestBody FamilySyncRequest request) {
        LibrarySyncRun run = librarySyncService.syncFamily(request.accessToken(),
                Boolean.TRUE.equals(request.force()));

        return run.getOutcome() == LibrarySyncOutcome.FAILED
                ? ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new LibraryErrorBody("FAMILY_SYNC_FAILED", run.getErrorCode()))
                : ResponseEntity.ok(toResponse(run));
    }

    @GetMapping("/status")
    public LibraryStatusResponse status() {
        return libraryStatusService.status();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<LibraryErrorBody> handleInvalidFamilyRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new LibraryErrorBody("FAMILY_TOKEN_REQUIRED", null));
    }

    @ExceptionHandler(SteamNotConfiguredException.class)
    public ResponseEntity<LibraryErrorBody> handleSteamNotConfigured(SteamNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new LibraryErrorBody("STEAM_NOT_CONFIGURED", null));
    }

    private LibrarySyncRunResponse toResponse(LibrarySyncRun run) {
        return new LibrarySyncRunResponse(run.getLibrarySource(), run.getOutcome(), run.getStartedAt(),
                run.getFinishedAt(), run.getEntriesSubmitted(), run.getEntriesAdded(), run.getEntriesRemoved(),
                run.getMetadataCalls(), run.getAiCalls(), run.getErrorCode());
    }

    private record LibraryErrorBody(String reason, String errorCode) {
    }
}
