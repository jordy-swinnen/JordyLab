package dev.jordy.jordylab.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the current user for every module (spec 019 FR-009). The principal of a session authentication is an
 * {@link AuthenticatedUser}; machine-token and anonymous authentications carry no person, so they resolve to empty.
 * This replaces reading {@code @AuthenticationPrincipal Jwt} in the controllers.
 */
@Component
public class CurrentUserResolver {

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return Optional.of(authenticatedUser);
        }

        return Optional.empty();
    }

    /** The current user's id as the owner-column string ({@code user_subject} and friends). */
    public Optional<String> currentUserSubject() {
        return currentUser().map(user -> user.id().toString());
    }

    /** Fails loudly when a controller that requires a person is reached without one — a security-config bug. */
    public String requireCurrentUserSubject() {
        return currentUserSubject().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
