package dev.jordy.jordylab.feedback.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

/**
 * Canonical fixture for {@link FeedbackTicket}: fixed, realistic defaults so entity, service,
 * controller and repository tests agree on one ticket shape.
 */
@UtilityClass
public class FeedbackTicketTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("0c1e6f04-8d54-4a4b-9d3f-1e2a5b7c9d10");
    public static final FeedbackApp DEFAULT_APP = FeedbackApp.GAME_CATALOG;
    public static final FeedbackType DEFAULT_TYPE = FeedbackType.BUG_REPORT;
    public static final String DEFAULT_TITLE = "Grid loses my scroll position";
    public static final String DEFAULT_DESCRIPTION = "When I open a game and go back, the grid jumps to the top.";
    public static final String DEFAULT_SENDER_SUBJECT = "7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f";
    public static final String DEFAULT_SENDER_DISPLAY_NAME = "Ada Palmer";

    public static FeedbackTicket aDefaultFeedbackTicket() {
        return aFeedbackTicket().build();
    }

    public static FeedbackTicket.FeedbackTicketBuilder aFeedbackTicket() {
        return FeedbackTicket.builder()
                .id(DEFAULT_ID)
                .app(DEFAULT_APP)
                .type(DEFAULT_TYPE)
                .title(DEFAULT_TITLE)
                .description(DEFAULT_DESCRIPTION)
                .senderSubject(DEFAULT_SENDER_SUBJECT)
                .senderDisplayName(DEFAULT_SENDER_DISPLAY_NAME)
                .state(FeedbackTicketState.NEW);
    }
}
