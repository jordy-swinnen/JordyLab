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

/**
 * A game installed via one scan source (a host + library type). The game itself is
 * host-independent; per-host presence and its grace clock live here.
 */
@Entity
@Table(schema = "gamecatalog", name = "game_installation")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameInstallation extends BaseEntity<GameInstallation> {

    private static final int MAX_EXTERNAL_REF_LENGTH = 500;

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_id")
    private ScanSource source;

    private String externalRef;

    @Enumerated(EnumType.STRING)
    private Presence presence;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant uninstalledAt;

    public boolean isInstalled() {
        return presence == Presence.INSTALLED;
    }

    public void seenAgain(Instant seenAt) {
        this.presence = Presence.INSTALLED;
        this.uninstalledAt = null;
        this.lastSeenAt = seenAt;
    }

    public void markUninstalled(Instant uninstalledAt) {
        this.presence = Presence.UNINSTALLED;
        this.uninstalledAt = uninstalledAt;
    }

    public static class GameInstallationBuilder {
        public GameInstallation build() {
            Preconditions.checkArgument(game != null, "game is required");
            Preconditions.checkArgument(source != null, "source is required");
            Preconditions.checkArgument(StringUtils.hasText(externalRef), "externalRef is required");
            Preconditions.checkArgument(externalRef.length() <= MAX_EXTERNAL_REF_LENGTH,
                    "externalRef must not exceed 500 characters");
            Preconditions.checkArgument(firstSeenAt != null, "firstSeenAt is required");
            Preconditions.checkArgument(lastSeenAt != null, "lastSeenAt is required");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (presence == null) {
                presence = Presence.INSTALLED;
            }

            return new GameInstallation(id, game, source, externalRef, presence, firstSeenAt, lastSeenAt,
                    uninstalledAt);
        }
    }
}
