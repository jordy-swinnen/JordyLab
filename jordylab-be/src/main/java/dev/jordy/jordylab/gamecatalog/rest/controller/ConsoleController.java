package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.AddConsoleGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkPreviewRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameListItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleRenameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleSearchResult;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.KnownConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RelinkConsoleGameRequest;
import dev.jordy.jordylab.gamecatalog.service.ConsoleBulkService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleGameService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consoles and the games on them (spec 013 contracts/consoles-api.md). Every endpoint is admin only through the
 * {@code /api/gamecatalog/**} catch-all in the security configuration.
 */
@RestController
@RequestMapping("/api/gamecatalog/consoles")
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService consoleService;
    private final ConsoleGameService consoleGameService;
    private final ConsoleBulkService consoleBulkService;

    @GetMapping("/known")
    public List<KnownConsoleResponse> known(@RequestParam(required = false) String q) {
        return consoleService.known(q);
    }

    @GetMapping
    public List<ConsoleResponse> list() {
        return consoleService.list();
    }

    @PostMapping
    public ResponseEntity<ConsoleResponse> add(@Valid @RequestBody ConsoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consoleService.add(request.platform(), request.name()));
    }

    @PatchMapping("/{id}")
    public ConsoleResponse rename(@PathVariable UUID id, @Valid @RequestBody ConsoleRenameRequest request) {
        return consoleService.rename(id, request.name());
    }

    @GetMapping("/{id}/impact")
    public ConsoleImpactResponse impact(@PathVariable UUID id) {
        return consoleService.impact(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        consoleService.remove(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/games")
    public List<ConsoleGameListItem> games(@PathVariable UUID id) {
        return consoleGameService.list(id);
    }

    @GetMapping("/{id}/search")
    public List<ConsoleSearchResult> search(@PathVariable UUID id, @RequestParam String q) {
        return consoleGameService.search(id, q);
    }

    @PostMapping("/{id}/games")
    public ResponseEntity<ConsoleGameResponse> addGame(@PathVariable UUID id,
            @RequestBody AddConsoleGameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(consoleGameService.add(id, request.igdbGameId(), request.title()));
    }

    @PatchMapping("/{id}/games/{gameId}")
    public ConsoleGameResponse relink(@PathVariable UUID id, @PathVariable UUID gameId,
            @Valid @RequestBody RelinkConsoleGameRequest request) {
        return consoleGameService.relink(id, gameId, request.igdbGameId());
    }

    @DeleteMapping("/{id}/games/{gameId}")
    public ResponseEntity<Void> removeGame(@PathVariable UUID id, @PathVariable UUID gameId) {
        consoleGameService.remove(id, gameId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/games/bulk/preview")
    public ConsoleBulkPreviewResponse bulkPreview(@PathVariable UUID id,
            @Valid @RequestBody ConsoleBulkPreviewRequest request) {
        return consoleBulkService.preview(id, request.lines());
    }

    @PostMapping("/{id}/games/bulk/confirm")
    public ConsoleBulkSummaryResponse bulkConfirm(@PathVariable UUID id,
            @Valid @RequestBody ConsoleBulkConfirmRequest request) {
        return consoleBulkService.confirm(id, request.items());
    }
}
