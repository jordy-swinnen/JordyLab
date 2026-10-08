package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.gamecatalog.LibBotMessageAnswered;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Counts a guest's LibBot question once it was answered (spec 013 FR-013). Only the answered-message event counts: a
 * failed, cancelled or rejected question never publishes it. Admins are exempt, so their messages are not counted.
 */
@Component
@RequiredArgsConstructor
public class GuestChatUsageListener {

    private final GuestChatQuotaService quotaService;

    @ApplicationModuleListener
    void on(LibBotMessageAnswered event) {
        if (event.admin()) {
            return;
        }
        quotaService.countAnsweredMessage(event.userSubject());
    }
}
