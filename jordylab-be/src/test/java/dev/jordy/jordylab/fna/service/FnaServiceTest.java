package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.*;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItemDto;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FnaServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private BriefingRepository briefingRepository;

    @Mock
    private BriefingGeneratorService briefingGeneratorService;

    @Mock
    private StockPriceService stockPriceService;

    @InjectMocks
    private FnaService fnaService;

    @Test
    void getRecentArticlesMapsEntitiesToDtos() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc())
                .thenReturn(List.of(ArticleTestBuilder.aDefaultArticle()));

        List<ArticleSummaryDto> result = fnaService.getRecentArticles();

        assertThat(result).hasSize(1);
        assertSoftly(softly -> {
            ArticleSummaryDto dto = result.getFirst();
            softly.assertThat(dto.id()).isEqualTo(ArticleTestBuilder.DEFAULT_ID);
            softly.assertThat(dto.title()).isEqualTo(ArticleTestBuilder.DEFAULT_TITLE);
            softly.assertThat(dto.url()).isEqualTo(ArticleTestBuilder.DEFAULT_URL);
            softly.assertThat(dto.publishedAt()).isEqualTo(ArticleTestBuilder.DEFAULT_PUBLISHED_AT);
            softly.assertThat(dto.feedName()).isEqualTo(FeedTestBuilder.DEFAULT_NAME);
        });
    }

    @Test
    void upsertPositionCreatesNewWhenTickerNotFound() {
        when(positionRepository.findByTicker("ACKB.BR")).thenReturn(Optional.empty());

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        when(positionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioPositionDto result = fnaService.upsertPosition("ACKB.BR", BigDecimal.valueOf(25));

        verify(stockPriceService).refresh(captor.getValue());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getTicker()).isEqualTo("ACKB.BR");
            softly.assertThat(captor.getValue().getShareCount()).isEqualByComparingTo(BigDecimal.valueOf(25));
            softly.assertThat(result.ticker()).isEqualTo("ACKB.BR");
        });
    }

    @Test
    void upsertPositionUpdatesExistingWhenTickerExists() {
        UUID existingId = UUID.fromString("99999999-0000-0000-0000-000000000009");
        PortfolioPosition existing = PortfolioPositionTestBuilder.aPortfolioPosition()
                .id(existingId)
                .ticker("KBC.BR")
                .shareCount(BigDecimal.valueOf(10))
                .build();

        when(positionRepository.findByTicker("KBC.BR")).thenReturn(Optional.of(existing));
        when(positionRepository.save(existing)).thenReturn(existing);

        PortfolioPositionDto result = fnaService.upsertPosition("KBC.BR", BigDecimal.valueOf(50));

        assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(existingId);
            softly.assertThat(existing.getShareCount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        });
    }

    @Test
    void upsertPositionRejectsZeroShares() {
        assertThatThrownBy(() -> fnaService.upsertPosition("KBC.BR", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertPositionRejectsNegativeShares() {
        assertThatThrownBy(() -> fnaService.upsertPosition("KBC.BR", BigDecimal.valueOf(-2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertPositionRejectsMissingShares() {
        assertThatThrownBy(() -> fnaService.upsertPosition("KBC.BR", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertPositionPromotesAWishlistItemInPlace() {
        UUID wishlistId = PortfolioPositionTestBuilder.DEFAULT_WISHLIST_ID;
        PortfolioPosition wishlistItem = PortfolioPositionTestBuilder.aWishlistPosition().build();

        when(positionRepository.findByTicker(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER))
                .thenReturn(Optional.of(wishlistItem));
        when(positionRepository.save(wishlistItem)).thenReturn(wishlistItem);

        PortfolioPositionDto result = fnaService.upsertPosition(
                PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER, BigDecimal.valueOf(2));

        assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(wishlistId);
            softly.assertThat(result.shareCount()).isEqualByComparingTo(BigDecimal.valueOf(2));
            softly.assertThat(wishlistItem.isHeld()).isTrue();
        });
        verify(stockPriceService).refresh(wishlistItem);
    }

    @Test
    void getPortfolioPositionsReturnsHeldPositionsOnly() {
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultPortfolioPosition()));

        List<PortfolioPositionDto> result = fnaService.getPortfolioPositions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().ticker()).isEqualTo(PortfolioPositionTestBuilder.DEFAULT_TICKER);
    }

    @Test
    void getWishlistItemsMapsItemsWithoutAShareCount() {
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultWishlistPosition()));

        List<WishlistItemDto> result = fnaService.getWishlistItems();

        assertThat(result).hasSize(1);
        assertSoftly(softly -> {
            WishlistItemDto dto = result.getFirst();
            softly.assertThat(dto.id()).isEqualTo(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_ID);
            softly.assertThat(dto.ticker()).isEqualTo(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER);
            softly.assertThat(dto.lastPrice()).isEqualByComparingTo(PortfolioPositionTestBuilder.DEFAULT_LAST_PRICE);
            softly.assertThat(dto.lastPriceFetchedAt())
                    .isEqualTo(PortfolioPositionTestBuilder.DEFAULT_LAST_PRICE_FETCHED_AT);
        });
    }

    @Test
    void addWishlistItemCreatesAnItemWithoutAShareCountAndPricesIt() {
        when(positionRepository.findByTicker("ASML")).thenReturn(Optional.empty());
        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        when(positionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        WishlistItemDto result = fnaService.addWishlistItem("ASML");

        verify(stockPriceService).refresh(captor.getValue());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getTicker()).isEqualTo("ASML");
            softly.assertThat(captor.getValue().getShareCount()).isNull();
            softly.assertThat(result.ticker()).isEqualTo("ASML");
        });
    }

    @Test
    void addWishlistItemIsIdempotentForAnExistingWishlistTicker() {
        PortfolioPosition existing = PortfolioPositionTestBuilder.aWishlistPosition().build();
        when(positionRepository.findByTicker(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER))
                .thenReturn(Optional.of(existing));
        when(positionRepository.save(existing)).thenReturn(existing);

        WishlistItemDto result = fnaService.addWishlistItem(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER);

        assertThat(result.id()).isEqualTo(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_ID);
        verify(stockPriceService).refresh(existing);
    }

    @Test
    void addWishlistItemRejectsATickerAlreadyHeld() {
        PortfolioPosition held = PortfolioPositionTestBuilder.aPortfolioPosition().build();
        when(positionRepository.findByTicker(PortfolioPositionTestBuilder.DEFAULT_TICKER))
                .thenReturn(Optional.of(held));

        assertThatThrownBy(() -> fnaService.addWishlistItem(PortfolioPositionTestBuilder.DEFAULT_TICKER))
                .isInstanceOf(TickerAlreadyHeldException.class);
    }

    @Test
    void addWishlistItemNormalisesWhatTheUserTyped() {
        when(positionRepository.findByTicker("ASML")).thenReturn(Optional.empty());
        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        when(positionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        fnaService.addWishlistItem("  asml ");

        assertThat(captor.getValue().getTicker()).isEqualTo("ASML");
    }

    @Test
    void addWishlistItemRejectsABlankTicker() {
        assertThatThrownBy(() -> fnaService.addWishlistItem("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeWishlistItemDeletesById() {
        UUID wishlistId = PortfolioPositionTestBuilder.DEFAULT_WISHLIST_ID;

        fnaService.removeWishlistItem(wishlistId);

        verify(positionRepository).deleteById(wishlistId);
    }

    @Test
    void getLatestBriefingReturnsEmptyWhenNoBriefingExists() {
        when(briefingRepository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.empty());

        Optional<BriefingDto> result = fnaService.getLatestBriefing();

        assertThat(result).isEmpty();
    }

    @Test
    void getLatestBriefingReturnsMappedDto() {
        when(briefingRepository.findTopByOrderByGeneratedAtDesc())
                .thenReturn(Optional.of(BriefingTestBuilder.aDefaultBriefing()));

        Optional<BriefingDto> result = fnaService.getLatestBriefing();

        assertThat(result).isPresent();
        assertSoftly(softly -> {
            BriefingDto dto = result.get();
            softly.assertThat(dto.id()).isEqualTo(BriefingTestBuilder.DEFAULT_ID);
            softly.assertThat(dto.generatedAt()).isEqualTo(BriefingTestBuilder.DEFAULT_GENERATED_AT);
            softly.assertThat(dto.content()).isEqualTo(BriefingTestBuilder.DEFAULT_CONTENT);
            softly.assertThat(dto.modelUsed()).isEqualTo(BriefingTestBuilder.DEFAULT_MODEL_USED);
        });
    }

    @Test
    void triggerBriefingReturnsMappedDtoOnSuccess() {
        when(briefingGeneratorService.generateBriefing()).thenReturn(BriefingTestBuilder.aDefaultBriefing());

        BriefingDto result = fnaService.triggerBriefing();

        assertSoftly(softly -> {
            softly.assertThat(result.id()).isEqualTo(BriefingTestBuilder.DEFAULT_ID);
            softly.assertThat(result.content()).isEqualTo(BriefingTestBuilder.DEFAULT_CONTENT);
            softly.assertThat(result.modelUsed()).isEqualTo(BriefingTestBuilder.DEFAULT_MODEL_USED);
        });
    }

    @Test
    void triggerBriefingPropagatesExceptionOnAiFailure() {
        when(briefingGeneratorService.generateBriefing())
                .thenThrow(new BriefingGenerationException(ProviderFailureReason.UNREACHABLE));

        assertThatThrownBy(() -> fnaService.triggerBriefing())
                .isInstanceOf(BriefingGenerationException.class);
    }

    @Test
    void upsertPositionNormalisesWhatTheUserTyped() {
        when(positionRepository.findByTicker("BTC")).thenReturn(Optional.empty());
        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        when(positionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        fnaService.upsertPosition("  btc ", new BigDecimal("0.002106"));

        assertThat(captor.getValue().getTicker()).isEqualTo("BTC");
    }
}
