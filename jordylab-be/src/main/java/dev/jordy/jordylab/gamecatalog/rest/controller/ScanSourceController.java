package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.SetSourceEnabledRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/gamecatalog/sources")
@RequiredArgsConstructor
public class ScanSourceController {

    private final ScanSourceService scanSourceService;

    @GetMapping
    public SourcesResponse getSources() {
        return scanSourceService.listSources();
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<SourceEnabledResponse> setEnabled(@PathVariable UUID id,
            @Valid @RequestBody SetSourceEnabledRequest request) {
        return scanSourceService.setEnabled(id, request.enabled())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
