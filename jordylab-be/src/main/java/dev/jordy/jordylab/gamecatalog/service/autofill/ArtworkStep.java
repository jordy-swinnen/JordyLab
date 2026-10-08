package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.client.ArtworkLookupClient;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Step 2 of the auto-fill (spec 013 FR-019, FR-020): a cover and a banner for every game. Order: the Steam CDN for Steam
 * games, then IGDB's cover and artwork, then libretro thumbnails (trying regional and punctuation spellings), and only
 * then an explicit placeholder. A placeholder is looked up again after the retry period. A cover or banner an admin or
 * the scanner already supplied is never replaced.
 */
@Component
@RequiredArgsConstructor
public class ArtworkStep {

    private final ArtworkLookupClient lookupClient;
    private final IgdbClient igdbClient;
    private final GameCatalogProperties properties;

    public record Fetched(Optional<String> coverUrl, Optional<String> bannerUrl, boolean attempted) {

        public static Fetched none() {
            return new Fetched(Optional.empty(), Optional.empty(), false);
        }
    }

    public boolean needed(Game game, Instant retryBefore) {
        if (!properties.artwork().externalLookupEnabled()) {
            return false;
        }
        boolean gap = isOpen(game.getCoverStatus()) || isOpen(game.getBannerStatus());

        return gap && (game.getArtworkCheckedAt() == null || game.getArtworkCheckedAt().isBefore(retryBefore));
    }

    /** {@code facts} are what the facts step already asked IGDB, so the same game is not looked up twice. */
    public Fetched fetch(Game game, List<String> platforms, FactsStep.Fetched facts) {
        return fetch(game, platforms, facts, false);
    }

    /**
     * {@code refreshExisting} also looks again for a cover or banner that came from an external address, as the admin's
     * "refresh game data" run does; one the scanner or an admin uploaded is never touched.
     */
    public Fetched fetch(Game game, List<String> platforms, FactsStep.Fetched facts, boolean refreshExisting) {
        boolean coverOpen = isReplaceable(game.getCoverStatus(), refreshExisting);
        boolean bannerOpen = isReplaceable(game.getBannerStatus(), refreshExisting);
        Optional<String> cover = Optional.empty();
        Optional<String> banner = Optional.empty();
        if (StringUtils.hasText(game.getSteamAppId())) {
            cover = coverOpen ? lookupClient.findCoverArtworkUrl(SourceType.STEAM, PlatformCatalog.STEAM,
                    game.getSteamAppId(), game.getTitle()) : Optional.empty();
            banner = bannerOpen ? lookupClient.findBannerArtworkUrl(SourceType.STEAM, PlatformCatalog.STEAM,
                    game.getSteamAppId(), game.getTitle()) : Optional.empty();
        }
        if ((coverOpen && cover.isEmpty()) || (bannerOpen && banner.isEmpty())) {
            Optional<IgdbClient.IgdbFacts> igdb = igdbFacts(game, platforms, facts);
            if (cover.isEmpty() && coverOpen) {
                cover = igdb.map(IgdbClient.IgdbFacts::coverUrl).filter(StringUtils::hasText);
            }
            if (banner.isEmpty() && bannerOpen) {
                banner = igdb.map(IgdbClient.IgdbFacts::bannerUrl).filter(StringUtils::hasText);
            }
        }
        for (String platform : platforms) {
            if (cover.isEmpty() && coverOpen) {
                cover = lookupClient.findCoverArtworkUrl(SourceType.EMUDECK, platform, null, game.getTitle());
            }
            if (banner.isEmpty() && bannerOpen) {
                banner = lookupClient.findBannerArtworkUrl(SourceType.EMUDECK, platform, null, game.getTitle());
            }
        }

        return new Fetched(cover, banner, true);
    }

    public void apply(Game game, Fetched fetched, Instant now) {
        apply(game, fetched, now, false);
    }

    /** A refresh replaces an existing external address only when the new lookup found one; it never downgrades to a placeholder. */
    public void apply(Game game, Fetched fetched, Instant now, boolean refreshExisting) {
        if (!fetched.attempted()) {
            return;
        }
        if (isOpen(game.getCoverStatus())) {
            game.applyCoverArtwork(fetched.coverUrl().isPresent() ? ArtworkStatus.EXTERNAL_URL : ArtworkStatus.PLACEHOLDER,
                    fetched.coverUrl().orElse(null));
        } else if (refreshExisting && isReplaceable(game.getCoverStatus(), true) && fetched.coverUrl().isPresent()) {
            game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, fetched.coverUrl().get());
        }
        if (isOpen(game.getBannerStatus())) {
            game.applyBannerArtwork(fetched.bannerUrl().isPresent() ? ArtworkStatus.EXTERNAL_URL : ArtworkStatus.PLACEHOLDER,
                    fetched.bannerUrl().orElse(null));
        } else if (refreshExisting && isReplaceable(game.getBannerStatus(), true) && fetched.bannerUrl().isPresent()) {
            game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, fetched.bannerUrl().get());
        }
        game.recordArtworkChecked(now);
    }

    private boolean isOpen(ArtworkStatus status) {
        return status == ArtworkStatus.PENDING || status == ArtworkStatus.PLACEHOLDER;
    }

    private boolean isReplaceable(ArtworkStatus status, boolean refreshExisting) {
        return isOpen(status) || (refreshExisting && status == ArtworkStatus.EXTERNAL_URL);
    }

    private Optional<IgdbClient.IgdbFacts> igdbFacts(Game game, List<String> platforms, FactsStep.Fetched facts) {
        if (facts.igdb().isPresent()) {
            return facts.igdb();
        }
        if (!igdbClient.isConfigured()) {
            return Optional.empty();
        }
        Long platformId = platforms.stream()
                .map(platform -> PlatformCatalog.entryFor(platform).igdbPlatformId())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        return igdbClient.findGame(game.getTitle(), platformId).flatMap(found -> igdbClient.fetchFacts(found.id()));
    }
}
