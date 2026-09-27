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

import java.time.Instant;
import java.util.UUID;

/**
 * A game available through a library source (Owned or Family). At most one entry per game
 * per source; a game that leaves the library is soft-removed and purged after the grace
 * period, exactly like an installation. Holding an active entry keeps the game (and its
 * enrichment) alive even when it has no installations (FR-014).
 */
@Entity
@Table(schema = "gamecatalog", name = "game_library_entry")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameLibraryEntry extends BaseEntity<GameLibraryEntry> {

    private static final int MAX_FAMILY_OWNER_NAMES_LENGTH = 500;

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @Enumerated(EnumType.STRING)
    private LibrarySource librarySource;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant removedAt;

    private String familyOwnerNames;

    public boolean isActive() {
        return removedAt == null;
    }

    public boolean isWithinGrace(Instant cutoff) {
        return removedAt != null && !removedAt.isBefore(cutoff);
    }

    public void seenAgain(Instant seenAt) {
        this.lastSeenAt = seenAt;
        this.removedAt = null;
    }

    public void markRemoved(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public void updateFamilyOwnerNames(String familyOwnerNames) {
        this.familyOwnerNames = familyOwnerNames;
    }

    public static class GameLibraryEntryBuilder {
        public GameLibraryEntry build() {
            Preconditions.checkArgument(game != null, "game is required");
            Preconditions.checkArgument(librarySource == LibrarySource.OWNED || librarySource == LibrarySource.FAMILY,
                    "librarySource must be OWNED or FAMILY");
            Preconditions.checkArgument(firstSeenAt != null, "firstSeenAt is required");
            Preconditions.checkArgument(lastSeenAt != null, "lastSeenAt is required");
            Preconditions.checkArgument(familyOwnerNames == null
                            || familyOwnerNames.length() <= MAX_FAMILY_OWNER_NAMES_LENGTH,
                    "familyOwnerNames must not exceed 500 characters");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new GameLibraryEntry(id, game, librarySource, firstSeenAt, lastSeenAt, removedAt,
                    familyOwnerNames);
        }
    }
}
