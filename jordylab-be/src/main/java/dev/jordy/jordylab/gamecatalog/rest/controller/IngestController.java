package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import dev.jordy.jordylab.gamecatalog.service.ScriptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/gamecatalog/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final ScanService scanService;
    private final ScriptService scriptService;

    @PostMapping(path = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScanResponse> submitScan(@Valid @RequestBody ScanRequest request) {
        return ResponseEntity.ok(scanService.submitScan(request));
    }

    @GetMapping(path = "/script", produces = "text/x-shellscript")
    public ResponseEntity<byte[]> downloadScript(@RequestParam("libraryType") String libraryType,
            HttpServletRequest httpRequest) {
        String script = scriptService.generateScript(libraryType, httpRequest);
        byte[] body = script.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.OK)
                .header("Content-Disposition",
                        "attachment; filename=\"jordylab-scan-" + libraryType.toLowerCase() + ".sh\"")
                .contentType(MediaType.parseMediaType("text/x-shellscript"))
                .body(body);
    }
}
