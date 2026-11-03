package dev.jordy.jordylab.fna.service;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FnaService {

    private final ArticleRepository articleRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BriefingRepository briefingRepository;
    private final BriefingGeneratorService briefingGeneratorService;
    private final StockPriceService stockPriceService;

    public List<ArticleSummaryDto> getRecentArticles() {
        return articleRepository.findTop50ByOrderByPublishedAtDesc().stream()
                .map(this::toArticleSummary)
                .toList();
    }

    /** Newest first. A blank text returns every article; otherwise the title must contain the text (spec change S2). */
    public Page<ArticleSummaryDto> searchArticles(String titleText, Pageable pageable) {
        Page<Article> articles = StringUtils.hasText(titleText)
                ? articleRepository.findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(titleText.trim(), pageable)
                : articleRepository.findAll(pageable);
        return articles.map(this::toArticleSummary);
    }

    private ArticleSummaryDto toArticleSummary(Article article) {
        return new ArticleSummaryDto(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getPublishedAt(),
                article.getFeed().getName());
    }

    /** Held positions only: wishlist items have no share count and never contribute to worth or allocation. */
    public List<PortfolioPositionDto> getPortfolioPositions() {
        return positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc().stream()
                .map(this::toPositionDto)
                .toList();
    }

    public PortfolioPositionDto upsertPosition(String typedTicker, BigDecimal shareCount) {
        Preconditions.checkArgument(shareCount != null && shareCount.compareTo(BigDecimal.ZERO) > 0,
                "shareCount must be positive");
        String ticker = normaliseTicker(typedTicker);
        // A ticker already on the wishlist is promoted in place: it keeps its resolved symbol and cached price.
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
        // Price it right away (resolving BTC -> BTC-EUR, MEUD -> MEUD.PA) so the value shows without waiting for the
        // half-hourly refresh.
        stockPriceService.refresh(saved);

        return toPositionDto(saved);
    }

    public void removePosition(UUID id) {
        positionRepository.deleteById(id);
    }

    public List<WishlistItemDto> getWishlistItems() {
        return positionRepository.findAllByShareCountIsNullOrderByTickerAsc().stream()
                .map(this::toWishlistItemDto)
                .toList();
    }

    public WishlistItemDto addWishlistItem(String typedTicker) {
        String ticker = normaliseTicker(typedTicker);
        PortfolioPosition position = positionRepository.findByTicker(ticker)
                .map(existing -> {
                    if (existing.isHeld()) {
                        throw new TickerAlreadyHeldException(ticker);
                    }

                    return existing;
                })
                .orElseGet(() -> PortfolioPosition.builder()
                        .ticker(ticker)
                        .build());

        PortfolioPosition saved = positionRepository.save(position);
        stockPriceService.refresh(saved);

        return toWishlistItemDto(saved);
    }

    public void removeWishlistItem(UUID id) {
        positionRepository.deleteById(id);
    }

    public Optional<BriefingDto> getLatestBriefing() {
        return briefingRepository.findTopByOrderByGeneratedAtDesc()
                .map(this::toBriefingDto);
    }

    public BriefingDto triggerBriefing() {
        return toBriefingDto(briefingGeneratorService.generateBriefing());
    }

    private String normaliseTicker(String typedTicker) {
        Preconditions.checkArgument(StringUtils.hasText(typedTicker), "ticker is required");

        return typedTicker.trim().toUpperCase(Locale.ROOT);
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

    private WishlistItemDto toWishlistItemDto(PortfolioPosition position) {
        return new WishlistItemDto(
                position.getId(),
                position.getTicker(),
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
