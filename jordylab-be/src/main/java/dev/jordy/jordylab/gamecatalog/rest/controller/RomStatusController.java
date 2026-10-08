package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RomStatusRequest;
import dev.jordy.jordylab.gamecatalog.service.InstallationNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.RomStatusService;
import dev.jordy.jordylab.gamecatalog.service.RomStatusNotApplicableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/gamecatalog/games")
@RequiredArgsConstructor
public class RomStatusController {

    private final RomStatusService romStatusService;

    @PutMapping("/{id}/installations/{installationId}/rom-status")
    public PlaceResponse setStatus(@PathVariable UUID id, @PathVariable UUID installationId,
            @Valid @RequestBody RomStatusRequest request) {
        return romStatusService.setStatus(id, installationId, request.status());
    }

    @ExceptionHandler(RomStatusNotApplicableException.class)
    public ResponseEntity<ErrorBody> notApplicable() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("ROM_STATUS_NOT_APPLICABLE"));
    }

    @ExceptionHandler(InstallationNotFoundException.class)
    public ResponseEntity<ErrorBody> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND"));
    }

    public record ErrorBody(String reason) {
    }
}
