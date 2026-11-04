package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkResponse;
import dev.jordy.jordylab.gamecatalog.service.MarkService;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/gamecatalog/games")
@RequiredArgsConstructor
public class MarkController {

    private final MarkService markService;
    private final CurrentUserResolver currentUserResolver;

    @PutMapping("/{id}/mark")
    public ResponseEntity<MarkResponse> setMark(@PathVariable UUID id, @RequestBody MarkRequest request) {
        return markService.setMark(id, currentUserResolver.requireCurrentUserSubject(), request.mark())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
