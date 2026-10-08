package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** What removing a console would do: how many games it holds, how many also live elsewhere, how many would go. */
public record ConsoleImpactResponse(long games, long alsoElsewhere, long wouldBeRemoved) {
}
