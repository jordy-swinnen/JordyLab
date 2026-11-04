package dev.jordy.jordylab.feedback.service;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The single source of "which apps may this person send feedback about" (spec 017 FR-005): it
 * backs both the form's choice list and the submit validation, so the two can never disagree.
 * The access source is the per-app grant the admin hands out (spec 014): holding the app's grant
 * role makes the app available in the feedback form.
 */
@Service
public class FeedbackAppAccessService {

    private static final String ROLE_PREFIX = "ROLE_";

    public List<FeedbackApp> allowedApps(Collection<? extends GrantedAuthority> authorities) {
        return Arrays.stream(FeedbackApp.values())
                .filter(app -> hasRole(authorities, app.getGrantRoleName()))
                .toList();
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String roleName) {
        String authority = ROLE_PREFIX + roleName;

        return authorities.stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    public void requireAllowed(FeedbackApp app, Collection<? extends GrantedAuthority> authorities) {
        if (!allowedApps(authorities).contains(app)) {
            throw new FeedbackAppNotAllowedException("app is not available to this sender: " + app);
        }
    }
}
