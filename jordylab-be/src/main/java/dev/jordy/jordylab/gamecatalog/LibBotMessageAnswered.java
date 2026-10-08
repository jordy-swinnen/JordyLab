package dev.jordy.jordylab.gamecatalog;

/**
 * Published once for every question LibBot answered successfully (spec 013 FR-013). The {@code settings} module counts it
 * against a guest's daily allowance; a failed, cancelled or rejected question never publishes it, so it never costs a
 * message. A root-package event, so other modules can depend on it without reaching into gamecatalog internals.
 */
public record LibBotMessageAnswered(String userSubject, boolean admin) {
}
