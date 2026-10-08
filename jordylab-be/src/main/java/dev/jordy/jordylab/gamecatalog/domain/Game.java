package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.gamecatalog.util.TitleKeys;
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
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.time.Instant;
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
    private static final int MAX_IGDB_GAME_ID_LENGTH = 32;
    private static final int MAX_GENRES_LENGTH = 200;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_RELEASE_YEAR = 1950;
    private static final int MAX_RELEASE_YEAR = 2028;

    @Id
    private UUID id;

    private String steamAppId;

    @Setter
    private String igdbGameId;

    private String title;

    private String titleKey;

    @Enumerated(EnumType.STRING)
    private TitleSource titleSource;

    private String genre;

    private String genres;

    private String developer;

    private String publisher;

    private Integer releaseYear;

    private Integer maxLocalPlayers;

    private Boolean onlineMultiplayer;

    private Boolean singlePlayer;

    private Boolean localMultiplayer;

    private Boolean splitScreen;

    @Enumerated(EnumType.STRING)
    private MultiplayerSource multiplayerSource;

    private int multiplayerAttempts;

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

    private Instant factsCheckedAt;

    private Instant artworkCheckedAt;

    @Enumerated(EnumType.STRING)
    private DescriptionSource descriptionSource;

    private String descriptionModel;

    private String descriptionRequestedModel;

    private Instant descriptionWrittenAt;

    /**
     * Updates the title. It changes when the reporting source outranks the current one, or equals it and is a library or
     * a person (FR-012): a scan never overwrites a library title, and one scan never renames another's. The title key follows the title so identity matching stays right.
     */
    public void updateCatalogInfo(String title, TitleSource source) {
        if (source.replaces(this.titleSource)) {
            this.title = title;
            this.titleKey = titleKeyOf(title);
            this.titleSource = source;
        }
    }

    public void applyEnrichment(String genre, Boolean onlineMultiplayer, Boolean singlePlayer, String description,
            AiAuthorship authorship) {
        this.genre = genre;
        if (this.onlineMultiplayer == null) {
            this.onlineMultiplayer = onlineMultiplayer;
        }
        if (this.singlePlayer == null) {
            this.singlePlayer = singlePlayer;
        }
        this.description = description;
        this.descriptionSource = DescriptionSource.AI;
        this.descriptionModel = authorship.answeredModel();
        this.descriptionRequestedModel = authorship.requestedModel();
        this.descriptionWrittenAt = authorship.writtenAt();
        this.enrichmentStatus = EnrichmentStatus.ENRICHED;
    }

    /** Deterministic metadata fills only still-null fields; AI output never overwrites it (FR-005). */
    public void applyDeterministicMetadata(String genres, String developer, String publisher, Integer releaseYear) {
        if (this.genres == null && genres != null) {
            this.genres = genres;
        }
        if (this.developer == null && developer != null) {
            this.developer = developer;
        }
        if (this.publisher == null && publisher != null) {
            this.publisher = publisher;
        }
        if (this.releaseYear == null && releaseYear != null) {
            this.releaseYear = releaseYear;
        }
    }

    /** Deterministic multiplayer flags derived from Steam categories; fill-only. */
    public void applyDeterministicMultiplayerFlags(Boolean singlePlayer, Boolean onlineMultiplayer) {
        if (this.singlePlayer == null && singlePlayer != null) {
            this.singlePlayer = singlePlayer;
        }
        if (this.onlineMultiplayer == null && onlineMultiplayer != null) {
            this.onlineMultiplayer = onlineMultiplayer;
        }
    }

    /** Steam's own store description: fill-only, recorded as coming from Steam and never from a model (FR-059). */
    public void applyDeterministicDescription(String description) {
        if (this.description == null && description != null) {
            this.description = description;
            this.descriptionSource = DescriptionSource.STEAM;
            this.descriptionModel = null;
            this.descriptionRequestedModel = null;
            this.descriptionWrittenAt = null;
        }
    }

    public void recordFactsChecked(Instant checkedAt) {
        this.factsCheckedAt = checkedAt;
    }

    public void recordArtworkChecked(Instant checkedAt) {
        this.artworkCheckedAt = checkedAt;
    }

    /** Adopts a Steam app id for a game that was known without one (the same title already existed elsewhere). */
    public void assignSteamAppId(String newSteamAppId) {
        Preconditions.checkArgument(StringUtils.hasText(newSteamAppId) && newSteamAppId.length() <= MAX_STEAM_APP_ID_LENGTH,
                "steamAppId is required and must not exceed 32 characters");
        this.steamAppId = newSteamAppId;
    }

    private static String titleKeyOf(String title) {
        return TitleKeys.keyFor(title);
    }

    /**
     * Applies structured local-multiplayer facts from a deterministic source (Steam categories or
     * IGDB). Sets provenance and clears the attempt counter. A {@code null} max player count leaves
     * the current value untouched (e.g. Steam categories carry no counts).
     */
    public void applyDeterministicMultiplayer(Boolean localMultiplayer, Boolean splitScreen, Integer maxLocalPlayers,
            MultiplayerSource source) {
        this.localMultiplayer = localMultiplayer;
        this.splitScreen = splitScreen;
        if (maxLocalPlayers != null) {
            this.maxLocalPlayers = maxLocalPlayers;
        }
        this.multiplayerSource = source;
        this.multiplayerAttempts = 0;
    }

    /** Counts a failed/unresolved multiplayer lookup; the backlog query parks games at max attempts. */
    public void recordMultiplayerFailure() {
        this.multiplayerAttempts++;
    }

    public void resetMultiplayerForRetry() {
        this.multiplayerAttempts = 0;
    }

    /** Marks the deterministic metadata pass complete, even when Steam returned no usable fields. */
    public void markMetadataFetched() {
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
            Preconditions.checkArgument(StringUtils.hasText(title), "title is required");
            Preconditions.checkArgument(steamAppId == null || steamAppId.length() <= MAX_STEAM_APP_ID_LENGTH,
                    "steamAppId must not exceed 32 characters");
            Preconditions.checkArgument(igdbGameId == null || igdbGameId.length() <= MAX_IGDB_GAME_ID_LENGTH,
                    "igdbGameId must not exceed 32 characters");
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
            titleKey = titleKeyOf(title);
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
            if (multiplayerSource == null) {
                multiplayerSource = MultiplayerSource.UNKNOWN;
            }

            return new Game(id, steamAppId, igdbGameId, title, titleKey, titleSource, genre, genres, developer,
                    publisher, releaseYear, maxLocalPlayers, onlineMultiplayer, singlePlayer, localMultiplayer,
                    splitScreen, multiplayerSource, multiplayerAttempts, description, enrichmentStatus,
                    enrichmentAttempts, metadataStatus, metadataAttempts, coverStatus, coverRef, bannerStatus,
                    bannerRef, artworkFallbackRequests, factsCheckedAt, artworkCheckedAt, descriptionSource,
                    descriptionModel, descriptionRequestedModel, descriptionWrittenAt);
        }
    }
}
