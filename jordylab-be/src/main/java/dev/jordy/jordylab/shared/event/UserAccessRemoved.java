package dev.jordy.jordylab.shared.event;

/**
 * Published when an account loses its access (revoked or rejected). Whatever other modules keep per person, such as a
 * game's marks, is removed in response, so a removed account stops counting in public totals (spec 013 FR-046). It lives
 * in {@code shared}, not in the module that publishes it, because the module that reacts must not depend on that module:
 * {@code settings} already depends on {@code gamecatalog}, and the reverse would be a cycle.
 */
public record UserAccessRemoved(String userSubject) {
}
