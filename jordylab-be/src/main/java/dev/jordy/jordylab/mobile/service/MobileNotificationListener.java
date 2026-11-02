package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.feedback.FeedbackTicketCreated;
import dev.jordy.jordylab.fna.BriefingReady;
import dev.jordy.jordylab.mobile.MobileProperties;
import dev.jordy.jordylab.mobile.rest.client.NtfyClient;
import dev.jordy.jordylab.settings.UserSignUpPending;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns two business events from other modules into admin Ntfy push notifications with a
 * tap-through App Link (spec FR-015/FR-016, research D9) — the first real use of Spring
 * Modulith's application-event infrastructure in this codebase. {@code mobile} depends only on
 * the published event <em>types</em> (declared in each module's public root package), never on
 * {@code settings}'/{@code fna}'s internals — the same one-way dependency direction every other
 * module already follows.
 */
@Component
@RequiredArgsConstructor
public class MobileNotificationListener {

    private final NtfyClient ntfyClient;
    private final MobileProperties properties;

    @ApplicationModuleListener
    void on(UserSignUpPending event) {
        ntfyClient.publish(
                "New JordyLab sign-up",
                event.displayName() + " (" + event.email() + ") is waiting for approval.",
                appLinkUrl("settings-users"));
    }

    @ApplicationModuleListener
    void on(BriefingReady event) {
        ntfyClient.publish(
                "Your monthly briefing is ready",
                "The FNA monthly briefing generated at " + event.generatedAt() + " is ready to read.",
                appLinkUrl("fna-briefing"));
    }

    /**
     * One push per new feedback ticket, never carrying the ticket's text (spec 017 FR-017): the
     * notification body is built from the event's metadata only, and a delivery failure is
     * swallowed by {@link NtfyClient} so it can never fail the guest's submission.
     */
    @ApplicationModuleListener
    void on(FeedbackTicketCreated event) {
        ntfyClient.publish(
                "New feedback ticket",
                event.senderName() + " sent a " + event.typeName() + " for the " + event.appName() + " app.",
                appLinkUrl("feedback-inbox"));
    }

    private String appLinkUrl(String screen) {
        return "https://" + properties.app().productionDomain() + "/mobile/open?screen=" + screen;
    }
}
