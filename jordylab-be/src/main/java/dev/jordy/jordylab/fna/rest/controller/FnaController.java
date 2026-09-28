package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.ManualArticleRequest;
import dev.jordy.jordylab.fna.rest.controller.model.ManualArticleResponse;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.service.FnaService;
import dev.jordy.jordylab.fna.service.ManualArticleSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final ManualArticleSubmissionService manualArticleSubmissionService;

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

    /** "Save to FNA" — spec 007 FR-017, US5. Admin-only via the existing {@code /api/fna/**}
     * matcher in {@code SecurityConfig}; not a {@code mobile}-module concern (research D6). */
    @PostMapping("/articles/manual")
    public ResponseEntity<ManualArticleResponse> submitManualArticle(@RequestBody ManualArticleRequest request) {
        ManualArticleResponse response = ManualArticleResponse.from(
                manualArticleSubmissionService.queueArticle(request.url()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
