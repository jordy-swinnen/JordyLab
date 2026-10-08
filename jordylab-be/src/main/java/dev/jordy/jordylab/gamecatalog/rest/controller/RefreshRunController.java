package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshRunResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.StartRefreshRunRequest;
import dev.jordy.jordylab.gamecatalog.service.autofill.CostConfirmationRequiredException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshAlreadyActiveException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunNotRunningException;
import dev.jordy.jordylab.gamecatalog.service.autofill.RefreshRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The admin's bulk refresh runs (spec 013 US11). Admin only, by the catalog-wide matcher. */
@RestController
@RequestMapping("/api/gamecatalog/refresh-runs")
@RequiredArgsConstructor
public class RefreshRunController {

    private final RefreshRunService refreshRunService;

    @PostMapping
    public ResponseEntity<RefreshRunResponse> start(@Valid @RequestBody StartRefreshRunRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RefreshRunResponse.of(
                refreshRunService.start(request.kind(), jwt.getSubject(), request.costConfirmed())));
    }

    @GetMapping("/current")
    public ResponseEntity<RefreshRunResponse> current(@RequestParam RefreshRunKind kind) {
        return refreshRunService.current(kind)
                .map(run -> ResponseEntity.ok(RefreshRunResponse.of(run)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/stop")
    public RefreshRunResponse stop(@PathVariable UUID id) {
        return RefreshRunResponse.of(refreshRunService.stop(id));
    }

    @ExceptionHandler(RefreshAlreadyActiveException.class)
    public ResponseEntity<ErrorBody> alreadyActive() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("RUN_ALREADY_ACTIVE", null));
    }

    @ExceptionHandler(RefreshRunNotRunningException.class)
    public ResponseEntity<ErrorBody> notRunning() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("RUN_NOT_RUNNING", null));
    }

    @ExceptionHandler(RefreshRunNotFoundException.class)
    public ResponseEntity<ErrorBody> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND", null));
    }

    @ExceptionHandler(CostConfirmationRequiredException.class)
    public ResponseEntity<ErrorBody> costConfirmationRequired(CostConfirmationRequiredException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("COST_CONFIRMATION_REQUIRED", exception.getGames()));
    }

    /** {@code games} is present only for the cost confirmation, so the dialog can state the count. */
    public record ErrorBody(String reason, Integer games) {
    }
}
