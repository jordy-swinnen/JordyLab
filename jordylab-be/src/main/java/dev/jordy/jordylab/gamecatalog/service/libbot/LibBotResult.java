package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;

/**
 * A finished LibBot reply, before it is shaped for HTTP. {@code applied} are the requirement chips in the question's
 * language; {@code unknown} is present only when games with unknown data could have matched; {@code references} are
 * empty unless the outcome is {@code ANSWERED}.
 */
public record LibBotResult(LibBotOutcome outcome, Language language, String text, List<String> applied,
        Unknown unknown, List<Candidate> references) {

    public LibBotResult {
        applied = applied == null ? List.of() : List.copyOf(applied);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public record Unknown(int count, String note) {
    }

    static LibBotResult plain(LibBotOutcome outcome, Language language, String text) {
        return new LibBotResult(outcome, language, text, List.of(), null, List.of());
    }
}
