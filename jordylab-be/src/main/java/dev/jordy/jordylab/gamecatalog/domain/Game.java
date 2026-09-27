package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * The host-independent catalog entry for one game. Per-host state lives on
 * {@link GameInstallation}; this entity carries the shared data: identity, enrichment,
 * deterministic metadata, and the cover/banner artwork slots.
 */
@Entity
@Table(schema = "gamecatalog", name = "game")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity<Game> {

    private static final int MAX_STEAM_APP_ID_LENGTH = 32;
    private static final int MAX_GENRES_LENGTH = 200;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_RELEASE_YEAR = 1950;
    private static final int MAX_RELEASE_YEAR = 2028;

    @Id
    private UUID id;

    private String platform;

    private String steamAppId;

    private String title;

    private String genre;

    private String genres;

    private String developer;

    private String publisher;

    private Integer releaseYear;

    private Integer maxLocalPlayers;

    private Boolean onlineMultiplayer;

    private Boolean singlePlayer;

    private String description;

    @Enumerated(EnumType.STRING)
    private EnrichmentStatus enrichmentStatus;

    private int enrichmentAttempts;

    @Enumerated(EnumType.STRING)
    private MetadataStatus metadataStatus;

    private int metadataAttempts;

    @Enumerated(EnumType.STRING)
    private ArtworkStatus coverStatus;

    private String coverRef;

    @Enumerated(EnumType.STRING)
    private ArtworkStatus bannerStatus;

    private String bannerRef;

    private int artworkFallbackRequests;

    public void updateCatalogInfo(String title, String platform) {
        this.title = title;
        this.platform = platform;
    }

    public void applyEnrichment(String genre, Integer maxLocalPlayers, Boolean onlineMultiplayer, Boolean singlePlayer,
            String description) {
        this.genre = genre;
        this.maxLocalPlayers = maxLocalPlayers;
        this.onlineMultiplayer = onlineMultiplayer;
        this.singlePlayer = singlePlayer;
        this.description = description;
        this.enrichmentStatus = EnrichmentStatus.ENRICHED;
    }

    public void applyDeterministicMetadata(String genres, String developer, String publisher, Integer releaseYear) {
        this.genres = genres;
        this.developer = developer;
        this.publisher = publisher;
        this.releaseYear = releaseYear;
        this.metadataStatus = MetadataStatus.OK;
    }

    public void recordEnrichmentFailure(int maxAttempts) {
        this.enrichmentAttempts++;
        if (this.enrichmentAttempts >= maxAttempts) {
            this.enrichmentStatus = EnrichmentStatus.FAILED;
        }
    }

    public void resetEnrichmentForRetry() {
        this.enrichmentStatus = EnrichmentStatus.PENDING;
        this.enrichmentAttempts = 0;
    }

    public void recordMetadataFailure(int maxAttempts) {
        this.metadataAttempts++;
        if (this.metadataAttempts >= maxAttempts) {
            this.metadataStatus = MetadataStatus.FAILED;
        }
    }

    public void resetMetadataForRetry() {
        this.metadataStatus = MetadataStatus.PENDING;
        this.metadataAttempts = 0;
    }

    public void applyCoverArtwork(ArtworkStatus status, String coverRef) {
        this.coverStatus = status;
        this.coverRef = coverRef;
    }

    public void requestLocalCoverFallback() {
        this.coverStatus = ArtworkStatus.LOCAL_FALLBACK_REQUESTED;
        this.artworkFallbackRequests++;
    }

    public void applyBannerArtwork(ArtworkStatus status, String bannerRef) {
        this.bannerStatus = status;
        this.bannerRef = bannerRef;
    }

    public static class GameBuilder {
        public Game build() {
            Preconditions.checkArgument(StringUtils.hasText(platform), "platform is required");
            Preconditions.checkArgument(StringUtils.hasText(title), "title is required");
            Preconditions.checkArgument(steamAppId == null || steamAppId.length() <= MAX_STEAM_APP_ID_LENGTH,
                    "steamAppId must not exceed 32 characters");
            Preconditions.checkArgument(genres == null || genres.length() <= MAX_GENRES_LENGTH,
                    "genres must not exceed 200 characters");
            Preconditions.checkArgument(developer == null || developer.length() <= MAX_NAME_LENGTH,
                    "developer must not exceed 100 characters");
            Preconditions.checkArgument(publisher == null || publisher.length() <= MAX_NAME_LENGTH,
                    "publisher must not exceed 100 characters");
            Preconditions.checkArgument(releaseYear == null
                            || (releaseYear >= MIN_RELEASE_YEAR && releaseYear <= MAX_RELEASE_YEAR),
                    "releaseYear must be between 1950 and 2028");
            Preconditions.checkArgument(maxLocalPlayers == null || (maxLocalPlayers >= 1 && maxLocalPlayers <= 64),
                    "maxLocalPlayers must be between 1 and 64");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (enrichmentStatus == null) {
                enrichmentStatus = EnrichmentStatus.PENDING;
            }
            if (metadataStatus == null) {
                metadataStatus = MetadataStatus.PENDING;
            }
            if (coverStatus == null) {
                coverStatus = ArtworkStatus.PENDING;
            }
            if (bannerStatus == null) {
                bannerStatus = ArtworkStatus.PENDING;
            }

            return new Game(id, platform, steamAppId, title, genre, genres, developer, publisher, releaseYear,
                    maxLocalPlayers, onlineMultiplayer, singlePlayer, description, enrichmentStatus,
                    enrichmentAttempts, metadataStatus, metadataAttempts, coverStatus, coverRef, bannerStatus,
                    bannerRef, artworkFallbackRequests);
        }
    }
}
