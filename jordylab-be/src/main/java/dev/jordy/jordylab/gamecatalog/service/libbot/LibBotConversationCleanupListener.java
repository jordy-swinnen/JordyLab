package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.shared.event.UserDeleted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** A deleted person leaves nothing behind in LibBot's in-memory conversations (spec 014 FR-015). */
@Slf4j
@Component
@RequiredArgsConstructor
public class LibBotConversationCleanupListener {

    private final ConversationStore conversationStore;

    @ApplicationModuleListener
    void on(UserDeleted event) {
        conversationStore.forget(event.userSubject());
        log.info("Forgot the LibBot conversations of a deleted account");
    }
}
