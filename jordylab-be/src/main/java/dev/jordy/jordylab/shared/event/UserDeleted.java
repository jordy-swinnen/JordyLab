package dev.jordy.jordylab.shared.event;

/**
 * Published when the admin deletes a person completely (spec 014 FR-015, research R4). Whatever other modules hold for
 * that person is removed in response: the Game Catalog forgets their LibBot conversations, and the feedback module (once
 * it exists) removes their personal details from their tickets while keeping the ticket content (FR-016).
 *
 * <p>It lives in {@code shared} for the same reason as {@link UserAccessRemoved}: the reacting modules must not depend
 * on {@code settings}.
 *
 * @param userSubject the Keycloak subject of the deleted person
 */
public record UserDeleted(String userSubject) {
}
