package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import jakarta.validation.constraints.NotNull;

/** {@code confirmCost} must be true for the AI kind, which makes a paid model call per game; absent means false. */
public record StartRefreshRunRequest(@NotNull RefreshRunKind kind, Boolean confirmCost) {

    public boolean costConfirmed() {
        return Boolean.TRUE.equals(confirmCost);
    }
}
