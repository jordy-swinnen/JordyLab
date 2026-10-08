package dev.jordy.jordylab.gamecatalog.service.libbot;

/**
 * One requirement LibBot applied, shown to the person as a chip ("6+ local players"). The wording comes from the
 * per-language message templates, never from the model, so what is shown is exactly what was filtered on.
 */
public record AppliedConstraint(Kind kind, String argument) {

    public enum Kind {
        MIN_LOCAL_PLAYERS,
        LOCAL_MULTIPLAYER,
        SINGLE_PLAYER,
        ONLINE,
        PLATFORM,
        PLACE,
        INSTALLED,
        NOT_INSTALLED,
        GENRE,
        YEARS,
        MARK,
        LIKE_MY_LIKED
    }

    public static AppliedConstraint of(Kind kind) {
        return new AppliedConstraint(kind, null);
    }

    public static AppliedConstraint of(Kind kind, String argument) {
        return new AppliedConstraint(kind, argument);
    }
}
