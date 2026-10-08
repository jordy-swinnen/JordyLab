package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/**
 * What turning a source off would do: {@code hiddenGames} stop showing because this was their only place,
 * {@code stillVisibleElsewhere} stay because another source, the Steam library or a console holds them. Nothing is deleted.
 */
public record HideImpactResponse(long hiddenGames, long stillVisibleElsewhere) {
}
