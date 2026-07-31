package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.service.FnaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fna")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class FnaController {

    private final FnaService fnaService;

    @GetMapping("/articles")
    public ResponseEntity<List<ArticleSummaryDto>> getArticles() {
        return ResponseEntity.ok(fnaService.getRecentArticles());
    }

    @GetMapping("/portfolio")
    public ResponseEntity<List<PortfolioPositionDto>> getPortfolio() {
        return ResponseEntity.ok(fnaService.getPortfolioPositions());
    }

    @PutMapping("/portfolio/{ticker}")
    public ResponseEntity<PortfolioPositionDto> upsertPosition(@PathVariable String ticker,
                                                                @RequestParam BigDecimal shares) {
        return ResponseEntity.ok(fnaService.upsertPosition(ticker, shares));
    }

    @DeleteMapping("/portfolio/{id}")
    public ResponseEntity<Void> removePosition(@PathVariable UUID id) {
        fnaService.removePosition(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/briefing")
    public ResponseEntity<BriefingDto> getLatestBriefing() {
        return fnaService.getLatestBriefing()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/briefing/trigger")
    public ResponseEntity<BriefingDto> triggerBriefing() {
        return ResponseEntity.ok(fnaService.triggerBriefing());
    }
}
