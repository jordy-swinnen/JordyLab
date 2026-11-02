package dev.jordy.jordylab.feedback.rest.controller.model;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import dev.jordy.jordylab.feedback.domain.FeedbackType;

/**
 * A guest's submission (contracts/rest-api.md). All fields required; the enum values are the
 * wire names {@code GAME_CATALOG} and {@code BUG_REPORT|FEATURE_REQUEST|IMPROVEMENT|IDEA}.
 */
public record TicketSubmitRequest(FeedbackApp app, FeedbackType type, String title, String description) {
}
