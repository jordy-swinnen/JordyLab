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
 * A game installed via one scan source (a host + library type). The game itself is host-independent; per-host
 * presence and its grace clock live here. {@code platform} is the platform of this copy (a game can be a SNES ROM
 * here and a Switch entry elsewhere), and {@code romStatus} says whether this machine's ROM launches; only copies on an
 * emulation source carry one (spec 013 FR-051).
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

    private String platform;

    @Enumerated(EnumType.STRING)
    private Presence presence;

    @Enumerated(EnumType.STRING)
    private RomStatus romStatus;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant uninstalledAt;

    public boolean isInstalled() {
        return presence == Presence.INSTALLED;
    }

    /** True for a ROM copy found by an emulation scan: the only kind of copy that has a ROM status. */
    public boolean isEmulated() {
        return source.getSourceType() == SourceType.EMUDECK;
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

    /** The scan reports the platform per copy; a corrected folder mapping changes it without losing the copy's history. */
    public void updatePlatform(String newPlatform) {
        Preconditions.checkArgument(StringUtils.hasText(newPlatform), "platform is required");
        this.platform = newPlatform;
    }

    /** Records whether this machine's ROM launches. Rejected for Steam copies and anything else that is not emulated. */
    public void changeRomStatus(RomStatus newStatus) {
        Preconditions.checkArgument(newStatus != null, "ROM status is required");
        Preconditions.checkState(isEmulated(), "ROM status only applies to emulated copies");
        this.romStatus = newStatus;
    }

    public static class GameInstallationBuilder {
        public GameInstallation build() {
            Preconditions.checkArgument(game != null, "game is required");
            Preconditions.checkArgument(source != null, "source is required");
            Preconditions.checkArgument(StringUtils.hasText(externalRef), "externalRef is required");
            Preconditions.checkArgument(externalRef.length() <= MAX_EXTERNAL_REF_LENGTH,
                    "externalRef must not exceed 500 characters");
            Preconditions.checkArgument(StringUtils.hasText(platform), "platform is required");
            Preconditions.checkArgument(firstSeenAt != null, "firstSeenAt is required");
            Preconditions.checkArgument(lastSeenAt != null, "lastSeenAt is required");
            boolean emulated = source.getSourceType() == SourceType.EMUDECK;
            Preconditions.checkArgument(romStatus == null || emulated, "ROM status only applies to emulated copies");
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (presence == null) {
                presence = Presence.INSTALLED;
            }
            if (romStatus == null && emulated) {
                romStatus = RomStatus.UNKNOWN;
            }

            return new GameInstallation(id, game, source, externalRef, platform, presence, romStatus, firstSeenAt,
                    lastSeenAt, uninstalledAt);
        }
    }
}
