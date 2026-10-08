package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkResponse;
import dev.jordy.jordylab.gamecatalog.service.MarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @PutMapping("/{id}/mark")
    public ResponseEntity<MarkResponse> setMark(@PathVariable UUID id, @RequestBody MarkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return markService.setMark(id, jwt.getSubject(), request.mark())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
