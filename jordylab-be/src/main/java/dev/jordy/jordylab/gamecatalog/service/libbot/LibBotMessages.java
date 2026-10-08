package dev.jordy.jordylab.gamecatalog.service.libbot;

import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Every sentence LibBot says that the model does not write (spec 013 research A9): the applied-requirement chips, the
 * unknown-data note and the fixed replies. One template file per language; any language other than Dutch gets English.
 */
@Component
public class LibBotMessages {

    private static final String BUNDLE = "libbot/messages";
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    public String applied(AppliedConstraint constraint, Language language) {
        return switch (constraint.kind()) {
            case MIN_LOCAL_PLAYERS -> text(language, "applied.minLocalPlayers", constraint.argument());
            case LOCAL_MULTIPLAYER -> text(language, "applied.localMultiplayer");
            case SINGLE_PLAYER -> text(language, "applied.singlePlayer");
            case ONLINE -> text(language, "applied.online");
            case PLATFORM -> text(language, "applied.platform", constraint.argument());
            case PLACE -> text(language, "applied.place", constraint.argument());
            case INSTALLED -> text(language, "applied.installed");
            case NOT_INSTALLED -> text(language, "applied.notInstalled");
            case GENRE -> text(language, "applied.genre", constraint.argument());
            case YEARS -> text(language, "applied.years", constraint.argument());
            case MARK -> text(language, "applied.mark." + constraint.argument());
            case LIKE_MY_LIKED -> text(language, "applied.likeMyLiked");
        };
    }

    public List<String> applied(List<AppliedConstraint> constraints, Language language) {
        return constraints.stream().map(constraint -> applied(constraint, language)).toList();
    }

    /** The note that says how many games could not be checked; mentions the party size when that was the requirement. */
    public String unknownNote(int count, Integer partySize, Language language) {
        if (partySize != null) {
            return text(language, "unknown.players", count, partySize);
        }

        return text(language, "unknown.generic", count);
    }

    public String noMatch(Language language) {
        return text(language, "nomatch.default");
    }

    public String noMatchGroupTooLarge(int partySize, int largestKnownGroup, Language language) {
        return text(language, "nomatch.groupTooLarge", partySize, largestKnownGroup);
    }

    public String noMatchUnknownOnly(Language language) {
        return text(language, "nomatch.unknownOnly");
    }

    public String emptyLibrary(Language language) {
        return text(language, "emptyLibrary");
    }

    public String outOfScope(Language language) {
        return text(language, "outOfScope");
    }

    public String clarify(Language language) {
        return text(language, "clarify");
    }

    /** The fixed English reply for any language other than English or Dutch. */
    public String rephrase() {
        return text(Language.EN, "rephrase");
    }

    private String text(Language language, String key, Object... arguments) {
        Locale locale = language == Language.NL ? Locale.forLanguageTag("nl") : Locale.ENGLISH;
        String pattern = ResourceBundle.getBundle(BUNDLE, locale, NO_FALLBACK).getString(key);

        return arguments.length == 0 ? new MessageFormat(pattern, locale).format(new Object[0])
                : new MessageFormat(pattern, locale).format(arguments);
    }
}
