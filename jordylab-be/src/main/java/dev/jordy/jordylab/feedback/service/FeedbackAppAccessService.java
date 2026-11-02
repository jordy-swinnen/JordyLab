package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * The single source of "which apps may this person send feedback about" (spec 017 FR-005): it
 * backs both the form's choice list and the submit validation, so the two can never disagree.
 * Today the platform's role matrix is the access source — every guest uses the Game Catalog — and
 * the per-app grants of the user-management feature will replace only this class.
 */
@Service
public class FeedbackAppAccessService {

    private static final String GUEST_AUTHORITY = "ROLE_guest";

    public List<FeedbackApp> allowedApps(Collection<? extends GrantedAuthority> authorities) {
        boolean guest = authorities.stream()
                .anyMatch(authority -> GUEST_AUTHORITY.equals(authority.getAuthority()));

        return guest ? List.of(FeedbackApp.GAME_CATALOG) : List.of();
    }

    public void requireAllowed(FeedbackApp app, Collection<? extends GrantedAuthority> authorities) {
        if (!allowedApps(authorities).contains(app)) {
            throw new FeedbackAppNotAllowedException("app is not available to this sender: " + app);
        }
    }
}
