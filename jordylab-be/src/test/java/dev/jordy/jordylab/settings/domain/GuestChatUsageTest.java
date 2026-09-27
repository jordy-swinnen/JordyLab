package dev.jordy.jordylab.settings.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GuestChatUsageTest {

    @Test
    void buildGuestChatUsage() {
        GuestChatUsage usage = GuestChatUsageTestBuilder.aDefaultGuestChatUsage();

        assertSoftly(softly -> {
            softly.assertThat(usage.getId()).isNotNull();
            softly.assertThat(usage.getUserSubject()).isEqualTo(GuestChatUsageTestBuilder.DEFAULT_USER_SUBJECT);
            softly.assertThat(usage.getUsageDate()).isEqualTo(GuestChatUsageTestBuilder.DEFAULT_USAGE_DATE);
            softly.assertThat(usage.getMessageCount()).isEqualTo(GuestChatUsageTestBuilder.DEFAULT_MESSAGE_COUNT);
        });
    }

    @Test
    void buildWithoutUserSubject() {
        assertThatThrownBy(() -> GuestChatUsageTestBuilder.aGuestChatUsage().userSubject(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUserSubject() {
        assertThatThrownBy(() -> GuestChatUsageTestBuilder.aGuestChatUsage().userSubject(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutUsageDate() {
        assertThatThrownBy(() -> GuestChatUsageTestBuilder.aGuestChatUsage().usageDate(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithMessageCountBelowOne() {
        assertThatThrownBy(() -> GuestChatUsageTestBuilder.aGuestChatUsage().messageCount(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GuestChatUsage.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
