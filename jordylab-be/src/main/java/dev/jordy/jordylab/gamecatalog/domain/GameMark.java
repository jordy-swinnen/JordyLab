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

import java.util.UUID;

/**
 * One user's vote on one game (spec 013 US10). A user holds at most one of the three marks per game; that is a unique
 * index on {@code (game_id, user_subject)}, so choosing another mark replaces this one. Totals are public, the voter
 * is never exposed (FR-044). {@code userSubject} is the JWT subject, never taken from a request.
 */
@Entity
@Table(schema = "gamecatalog", name = "game_mark")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameMark extends BaseEntity<GameMark> {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    private String userSubject;

    @Enumerated(EnumType.STRING)
    private MarkType mark;

    public void changeTo(MarkType newMark) {
        Preconditions.checkArgument(newMark != null, "mark is required");
        this.mark = newMark;
    }

    public static class GameMarkBuilder {
        public GameMark build() {
            Preconditions.checkArgument(game != null, "game is required");
            Preconditions.checkArgument(StringUtils.hasText(userSubject), "userSubject is required");
            Preconditions.checkArgument(mark != null, "mark is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new GameMark(id, game, userSubject, mark);
        }
    }
}
