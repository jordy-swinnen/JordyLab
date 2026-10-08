package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

/** {@code mark} null clears the caller's mark. */
public record MarkRequest(MarkType mark) {
}
