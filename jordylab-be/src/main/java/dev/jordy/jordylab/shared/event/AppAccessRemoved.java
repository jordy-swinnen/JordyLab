package dev.jordy.jordylab.shared.event;

/**
 * Published when the admin removes one app from a person while leaving their other apps and their session in place
 * (spec 014 FR-002, clarification 2026-11-02). The app that owns the data reacts: the Game Catalog removes the person's
 * votes from its totals, exactly as {@link UserAccessRemoved} does when the whole account loses access.
 *
 * <p>It lives in {@code shared}, not in {@code settings}, because the module that reacts must not depend on the module
 * that publishes: {@code settings} already depends on {@code gamecatalog}, and the reverse would be a cycle.
 *
 * @param userSubject the Keycloak subject of the person who lost the app
 * @param appKey the key of the app that was removed, for example {@code gamecatalog}
 */
public record AppAccessRemoved(String userSubject, String appKey) {
}
