package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "gamecatalog", name = "game")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity<Game> {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id")
    private ScanSource source;

    private String platform;

    private String externalRef;

    private String title;

    private String genre;

    private Integer maxLocalPlayers;

    private Boolean onlineMultiplayer;

    private Boolean singlePlayer;

    private String description;

    @Enumerated(EnumType.STRING)
    private EnrichmentStatus enrichmentStatus;

    private int enrichmentAttempts;

    @Enumerated(EnumType.STRING)
    private ArtworkStatus artworkStatus;

    private String artworkRef;

    private int artworkFallbackRequests;

    @Enumerated(EnumType.STRING)
    private Presence presence;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant uninstalledAt;

    public void seenAgain(Instant seenAt) {
        this.presence = Presence.INSTALLED;
        this.uninstalledAt = null;
        this.lastSeenAt = seenAt;
    }

    public void updateCatalogInfo(String title, String platform) {
        this.title = title;
        this.platform = platform;
    }

    public void markUninstalled(Instant uninstalledAt) {
        this.presence = Presence.UNINSTALLED;
        this.uninstalledAt = uninstalledAt;
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

    public void applyArtwork(ArtworkStatus status, String artworkRef) {
        this.artworkStatus = status;
        this.artworkRef = artworkRef;
    }

    public void requestLocalArtworkFallback() {
        this.artworkStatus = ArtworkStatus.LOCAL_FALLBACK_REQUESTED;
        this.artworkFallbackRequests++;
    }

    public static class GameBuilder {
        public Game build() {
            Preconditions.checkArgument(source != null, "source is required");
            Preconditions.checkArgument(StringUtils.hasText(platform), "platform is required");
            Preconditions.checkArgument(StringUtils.hasText(externalRef), "externalRef is required");
            Preconditions.checkArgument(StringUtils.hasText(title), "title is required");
            Preconditions.checkArgument(firstSeenAt != null, "firstSeenAt is required");
            Preconditions.checkArgument(lastSeenAt != null, "lastSeenAt is required");
            Preconditions.checkArgument(maxLocalPlayers == null || (maxLocalPlayers >= 1 && maxLocalPlayers <= 64),
                    "maxLocalPlayers must be between 1 and 64");
            if (id == null) id = UUID.randomUUID();
            if (enrichmentStatus == null) enrichmentStatus = EnrichmentStatus.PENDING;
            if (artworkStatus == null) artworkStatus = ArtworkStatus.PENDING;
            if (presence == null) presence = Presence.INSTALLED;

            return new Game(id, source, platform, externalRef, title, genre, maxLocalPlayers, onlineMultiplayer,
                    singlePlayer, description, enrichmentStatus, enrichmentAttempts, artworkStatus, artworkRef,
                    artworkFallbackRequests, presence, firstSeenAt, lastSeenAt, uninstalledAt);
        }
    }
}
