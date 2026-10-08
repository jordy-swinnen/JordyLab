package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

/** The public totals after the change and the caller's own mark; voters are never listed. */
public record MarkResponse(VoteTotalsResponse votes, MarkType myMark) {
}
