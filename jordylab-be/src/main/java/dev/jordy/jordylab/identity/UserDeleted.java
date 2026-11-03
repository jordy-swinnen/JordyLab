package dev.jordy.jordylab.identity;

/**
 * Published when the admin deletes a person completely (spec 014 FR-015, research R4; preserved by spec 019).
 * Whatever other modules hold for that person is removed in response: the Game Catalog forgets their LibBot
 * conversations, and any future module removes their personal data from its records.
 *
 * @param userSubject the id of the deleted person
 */
public record UserDeleted(String userSubject) {
}
