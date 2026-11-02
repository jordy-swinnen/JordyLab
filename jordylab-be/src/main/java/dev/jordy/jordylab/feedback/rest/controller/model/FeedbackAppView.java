package dev.jordy.jordylab.feedback.rest.controller.model;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;

/**
 * One app the caller may send feedback about; {@code id} is the wire enum name.
 */
public record FeedbackAppView(String id, String name) {

    public static FeedbackAppView from(FeedbackApp app) {
        return new FeedbackAppView(app.name(), app.getDisplayName());
    }
}
