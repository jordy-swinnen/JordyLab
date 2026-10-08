package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** The one {@code error} event of a LibBot stream; {@code code} is UNAVAILABLE or INTERNAL. */
public record LibBotErrorResponse(String code, boolean retryable) {
}
