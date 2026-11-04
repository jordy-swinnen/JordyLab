package dev.jordy.jordylab.feedback.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An app a guest may send feedback about (spec 017). The guest-visible set comes from
 * {@code FeedbackAppAccessService}: the per-app grant role the admin hands out (spec 014).
 */
@Getter
@RequiredArgsConstructor
public enum FeedbackApp {

    GAME_CATALOG("Game Catalog", "Game Catalog", "app-gamecatalog");

    private final String displayName;
    private final String linearLabelName;
    private final String grantRoleName;
}
