package dev.jordy.jordylab.identity;

/**
 * Published when the admin removes one app from a person while leaving their other grants in place (spec 014 FR-002,
 * clarification 2026-11-02; preserved by spec 019). The app that owns the data reacts: the Game Catalog removes the
 * person's votes from its totals, exactly as {@link UserAccessRemoved} does when the whole account loses access.
 *
 * @param userSubject the id of the person who lost the app
 * @param appKey the key of the app that was removed, for example {@code gamecatalog}
 */
public record AppAccessRemoved(String userSubject, String appKey) {
}
