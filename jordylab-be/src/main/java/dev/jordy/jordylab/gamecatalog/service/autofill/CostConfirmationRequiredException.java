package dev.jordy.jordylab.gamecatalog.service.autofill;

import lombok.Getter;

/** The AI refresh costs money, so it only starts once the admin confirmed; {@code games} is how many paid calls it would make. */
@Getter
public class CostConfirmationRequiredException extends RuntimeException {

    private final int games;

    public CostConfirmationRequiredException(int games) {
        super("Regenerating " + games + " descriptions makes paid AI calls and needs confirmation");
        this.games = games;
    }
}
