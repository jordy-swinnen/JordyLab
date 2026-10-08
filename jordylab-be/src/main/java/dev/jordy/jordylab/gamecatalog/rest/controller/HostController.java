package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostDisplayNameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostResponse;
import dev.jordy.jordylab.gamecatalog.service.HostService;
import dev.jordy.jordylab.gamecatalog.service.NameTakenException;
import dev.jordy.jordylab.gamecatalog.service.NameTooLongException;
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
@RequestMapping("/api/gamecatalog/hosts")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;

    @PutMapping("/{id}/display-name")
    public ResponseEntity<HostResponse> setDisplayName(@PathVariable UUID id,
            @RequestBody HostDisplayNameRequest request) {
        return hostService.setDisplayName(id, request.displayName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(NameTakenException.class)
    public ResponseEntity<ErrorBody> nameTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("NAME_TAKEN"));
    }

    @ExceptionHandler(NameTooLongException.class)
    public ResponseEntity<ErrorBody> nameTooLong() {
        return ResponseEntity.badRequest().body(new ErrorBody("NAME_TOO_LONG"));
    }

    public record ErrorBody(String reason) {
    }
}
