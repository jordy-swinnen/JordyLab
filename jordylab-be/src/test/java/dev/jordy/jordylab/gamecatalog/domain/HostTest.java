package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostTest {

    @Test
    void buildHost() {
        Host host = HostTestBuilder.aDefaultHost();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(host.getId()).isEqualTo(HostTestBuilder.DEFAULT_ID);
            softly.assertThat(host.getHostname()).isEqualTo(HostTestBuilder.DEFAULT_HOSTNAME);
            softly.assertThat(host.getDisplayName()).isNull();
        });
    }

    @Test
    void buildAssignsAnIdWhenNoneIsGiven() {
        assertThat(HostTestBuilder.aHost().id(null).build().getId()).isNotNull();
    }

    @Test
    void buildWithoutHostname() {
        assertThatThrownBy(() -> HostTestBuilder.aHost().hostname(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankHostname() {
        assertThatThrownBy(() -> HostTestBuilder.aHost().hostname(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongDisplayName() {
        assertThatThrownBy(() -> HostTestBuilder.aHost().displayName("x".repeat(41)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelIsTheHostnameWhenNoDisplayNameIsSet() {
        assertThat(HostTestBuilder.aDefaultHost().label()).isEqualTo(HostTestBuilder.DEFAULT_HOSTNAME);
    }

    @Test
    void labelPrefersTheDisplayName() {
        Host host = HostTestBuilder.aHost().displayName(HostTestBuilder.DEFAULT_DISPLAY_NAME).build();

        assertThat(host.label()).isEqualTo(HostTestBuilder.DEFAULT_DISPLAY_NAME);
    }

    @Test
    void renameTrimsAndRegistersAnEventWithTheNewLabel() {
        Host host = HostTestBuilder.aDefaultHost();

        host.rename("  " + HostTestBuilder.DEFAULT_DISPLAY_NAME + "  ");
        Collection<Object> events = ReflectionTestUtils.invokeMethod(host, "domainEvents");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(host.getDisplayName()).isEqualTo(HostTestBuilder.DEFAULT_DISPLAY_NAME);
            softly.assertThat(host.label()).isEqualTo(HostTestBuilder.DEFAULT_DISPLAY_NAME);
            softly.assertThat(events).containsExactly(
                    new HostRenamed(HostTestBuilder.DEFAULT_ID, HostTestBuilder.DEFAULT_DISPLAY_NAME));
        });
    }

    @Test
    void renameWithBlankTextClearsTheNameSoTheHostnameReturns() {
        Host host = HostTestBuilder.aHost().displayName(HostTestBuilder.DEFAULT_DISPLAY_NAME).build();

        host.rename("   ");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(host.getDisplayName()).isNull();
            softly.assertThat(host.label()).isEqualTo(HostTestBuilder.DEFAULT_HOSTNAME);
        });
    }

    @Test
    void renameWithNullClearsTheName() {
        Host host = HostTestBuilder.aHost().displayName(HostTestBuilder.DEFAULT_DISPLAY_NAME).build();

        host.rename(null);

        assertThat(host.label()).isEqualTo(HostTestBuilder.DEFAULT_HOSTNAME);
    }

    @Test
    void renameAcceptsExactlyFortyCharactersAndRejectsMore() {
        Host host = HostTestBuilder.aDefaultHost();

        host.rename("x".repeat(40));

        assertThat(host.getDisplayName()).hasSize(40);
        assertThatThrownBy(() -> host.rename("x".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Host.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
