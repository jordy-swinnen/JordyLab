package dev.jordy.jordylab.identity;

/**
 * Published when an account loses its access (revoked, rejected or deleted). Whatever other modules keep per person,
 * such as a game's marks or a personal chat limit, is removed in response, so a removed account stops counting in
 * public totals (spec 013 FR-046, spec 014 FR-015). Published by the identity module; moved here from {@code
 * shared.event} when identity became its own module (spec 019).
 *
 * @param userSubject the id of the person who lost access
 */
public record UserAccessRemoved(String userSubject) {
}
