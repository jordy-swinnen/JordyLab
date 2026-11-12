package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The scan client's endpoints (spec 003): the desktop sync app asks whether a scan is needed and uploads the payload.
 * The script download endpoint was removed with the script client (spec 024).
 */
@RestController
@RequestMapping("/api/gamecatalog/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final ScanService scanService;

    @PostMapping(path = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScanResponse> submitScan(@Valid @RequestBody ScanRequest request) {
        return ResponseEntity.ok(scanService.submitScan(request));
    }

    @PostMapping(path = "/check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScanCheckResponse> check(@Valid @RequestBody ScanCheckRequest request) {
        return ResponseEntity.ok(scanService.submitCheck(request));
    }
}
