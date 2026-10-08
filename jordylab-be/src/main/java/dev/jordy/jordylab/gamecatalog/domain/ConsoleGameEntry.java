package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A game the admin added to one console (spec 013 FR-034). A place like an installed copy or a library membership, but
 * always effective: consoles have no install state and scans never touch these rows.
 */
@Entity
@Table(schema = "gamecatalog", name = "console_game_entry")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsoleGameEntry extends BaseEntity<ConsoleGameEntry> {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @ManyToOne(optional = false)
    @JoinColumn(name = "console_id")
    private Console console;

    public static class ConsoleGameEntryBuilder {
        public ConsoleGameEntry build() {
            Preconditions.checkArgument(game != null, "game is required");
            Preconditions.checkArgument(console != null, "console is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new ConsoleGameEntry(id, game, console);
        }
    }
}
