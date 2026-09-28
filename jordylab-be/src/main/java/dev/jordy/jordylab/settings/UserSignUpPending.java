package dev.jordy.jordylab.settings;

import java.util.UUID;

/**
 * Published when a new self-registered sign-up is first seen waiting for approval (spec 007
 * FR-016, research D9) — the first real use of Spring Modulith's event infrastructure in this
 * codebase. Declared in the module root package (the public API) so other modules, such as
 * {@code mobile}'s {@code @ApplicationModuleListener}, can depend on the event type without
 * reaching into {@code settings}' internals.
 */
public record UserSignUpPending(UUID userId, String email, String displayName) {
}
