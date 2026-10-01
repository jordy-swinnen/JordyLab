package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkPreviewRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameUpdateRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import dev.jordy.jordylab.gamecatalog.service.SwitchBulkService;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gamecatalog/switch")
@RequiredArgsConstructor
public class SwitchGameController {

    private final SwitchGameService switchGameService;
    private final SwitchBulkService switchBulkService;

    @GetMapping("/search")
    public List<SwitchSearchResult> search(@RequestParam String query) {
        return switchGameService.search(query);
    }

    @PostMapping("/games")
    public ResponseEntity<SwitchGameResponse> add(@Valid @RequestBody SwitchGameRequest request) {
        SwitchGameResponse response = request.igdbGameId() != null
                ? switchGameService.addFromIgdb(request)
                : switchGameService.addManual(request);

        return ResponseEntity.created(URI.create("/api/gamecatalog/games/" + response.gameId())).body(response);
    }

    @PatchMapping("/games/{id}")
    public ResponseEntity<SwitchGameResponse> update(@PathVariable UUID id,
            @Valid @RequestBody SwitchGameUpdateRequest request) {
        return ResponseEntity.ok(switchGameService.update(id, request));
    }

    @DeleteMapping("/games/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        switchGameService.delete(id);

        return ResponseEntity.noContent().build();
    }

    /** Matches every pasted line against IGDB; nothing is saved (spec 009 US3 AS1). */
    @PostMapping("/bulk/preview")
    public SwitchBulkPreviewResponse bulkPreview(@Valid @RequestBody SwitchBulkPreviewRequest request) {
        return switchBulkService.preview(request.text());
    }

    /** Adds the ticked lines and reports added / already present / skipped (spec 009 US3 AS2). */
    @PostMapping("/bulk/confirm")
    public SwitchBulkConfirmResponse bulkConfirm(@Valid @RequestBody SwitchBulkConfirmRequest request) {
        return switchBulkService.confirm(request);
    }
}
