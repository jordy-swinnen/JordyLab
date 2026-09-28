package dev.jordy.jordylab.mobile.service;

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
                "Today's briefing is ready",
                "The FNA daily briefing generated at " + event.generatedAt() + " is ready to read.",
                appLinkUrl("fna-briefing"));
    }

    private String appLinkUrl(String screen) {
        return "https://" + properties.app().productionDomain() + "/mobile/open?screen=" + screen;
    }
}
