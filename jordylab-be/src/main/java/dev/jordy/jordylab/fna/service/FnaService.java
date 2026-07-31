package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FnaService {

    private final ArticleRepository articleRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BriefingRepository briefingRepository;
    private final BriefingGeneratorService briefingGeneratorService;

    public List<ArticleSummaryDto> getRecentArticles() {
        return articleRepository.findTop50ByOrderByPublishedAtDesc().stream()
                .map(article -> new ArticleSummaryDto(
                        article.getId(),
                        article.getTitle(),
                        article.getUrl(),
                        article.getPublishedAt(),
                        article.getFeed().getName()
                ))
                .toList();
    }

    public List<PortfolioPositionDto> getPortfolioPositions() {
        return positionRepository.findAllByOrderByTickerAsc().stream()
                .map(this::toPositionDto)
                .toList();
    }

    public PortfolioPositionDto upsertPosition(String ticker, BigDecimal shareCount) {
        PortfolioPosition position = positionRepository.findByTicker(ticker)
                .map(existing -> {
                    existing.updateShareCount(shareCount);

                    return existing;
                })
                .orElseGet(() -> PortfolioPosition.builder()
                        .ticker(ticker)
                        .shareCount(shareCount)
                        .build());

        PortfolioPosition saved = positionRepository.save(position);

        return toPositionDto(saved);
    }

    public void removePosition(UUID id) {
        positionRepository.deleteById(id);
    }

    public Optional<BriefingDto> getLatestBriefing() {
        return briefingRepository.findTopByOrderByGeneratedAtDesc()
                .map(this::toBriefingDto);
    }

    public BriefingDto triggerBriefing() {
        return toBriefingDto(briefingGeneratorService.generateBriefing());
    }

    private PortfolioPositionDto toPositionDto(PortfolioPosition position) {
        return new PortfolioPositionDto(
                position.getId(),
                position.getTicker(),
                position.getShareCount(),
                position.getLastPrice(),
                position.getLastPriceFetchedAt()
        );
    }

    private BriefingDto toBriefingDto(Briefing briefing) {
        return new BriefingDto(
                briefing.getId(),
                briefing.getGeneratedAt(),
                briefing.getContent(),
                briefing.getModelUsed()
        );
    }
}
