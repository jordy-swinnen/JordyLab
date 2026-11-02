package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.shared.event.UserDeleted;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LibBotConversationCleanupListenerTest {

    @Test
    void aDeletedPersonLosesTheirConversations() {
        ConversationStore conversationStore = mock(ConversationStore.class);
        LibBotConversationCleanupListener listener = new LibBotConversationCleanupListener(conversationStore);

        listener.on(new UserDeleted("deleted-subject"));

        verify(conversationStore).forget("deleted-subject");
    }
}
