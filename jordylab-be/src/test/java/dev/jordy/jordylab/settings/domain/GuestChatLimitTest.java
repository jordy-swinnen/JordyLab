package dev.jordy.jordylab.settings.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GuestChatLimitTest {

    @Test
    void buildGuestChatLimit() {
        GuestChatLimit limit = GuestChatLimitTestBuilder.aDefaultGuestChatLimit();

        assertSoftly(softly -> {
            softly.assertThat(limit.getId()).isNotNull();
            softly.assertThat(limit.getUserSubject()).isEqualTo(GuestChatLimitTestBuilder.DEFAULT_USER_SUBJECT);
            softly.assertThat(limit.getDailyLimit()).isEqualTo(GuestChatLimitTestBuilder.DEFAULT_DAILY_LIMIT);
            softly.assertThat(limit.getUpdatedBySubject()).isEqualTo(GuestChatLimitTestBuilder.DEFAULT_UPDATED_BY_SUBJECT);
            softly.assertThat(limit.isDefault()).isFalse();
        });
    }

    @Test
    void buildSavedDefaultWithoutUserSubject() {
        GuestChatLimit limit = GuestChatLimitTestBuilder.aSavedDefault();

        assertThat(limit.isDefault()).isTrue();
    }

    @Test
    void buildWithZeroIsAllowed() {
        GuestChatLimit limit = GuestChatLimitTestBuilder.aGuestChatLimit().dailyLimit(0).build();

        assertThat(limit.getDailyLimit()).isZero();
    }

    @Test
    void buildWithNegativeDailyLimit() {
        assertThatThrownBy(() -> GuestChatLimitTestBuilder.aGuestChatLimit().dailyLimit(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutUpdatedBySubject() {
        assertThatThrownBy(() -> GuestChatLimitTestBuilder.aGuestChatLimit().updatedBySubject(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUpdatedBySubject() {
        assertThatThrownBy(() -> GuestChatLimitTestBuilder.aGuestChatLimit().updatedBySubject(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeDailyLimitRejectsNegativeValues() {
        GuestChatLimit limit = GuestChatLimitTestBuilder.aDefaultGuestChatLimit();

        assertThatThrownBy(() -> limit.changeDailyLimit(-1, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeDailyLimitAcceptsZeroAndRecordsWhoChangedIt() {
        GuestChatLimit limit = GuestChatLimitTestBuilder.aDefaultGuestChatLimit();

        limit.changeDailyLimit(0, "other-admin");

        assertSoftly(softly -> {
            softly.assertThat(limit.getDailyLimit()).isZero();
            softly.assertThat(limit.getUpdatedBySubject()).isEqualTo("other-admin");
        });
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GuestChatLimit.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
