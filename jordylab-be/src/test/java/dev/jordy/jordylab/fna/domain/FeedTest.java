package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedTest {

    @Test
    void buildFeed() {
        Feed feed = Feed.builder()
                .name("HLN Economie")
                .url("https://hln.be/rss/economie")
                .enabled(true)
                .build();

        assertThat(feed.getId()).isNotNull();
        assertThat(feed.getName()).isEqualTo("HLN Economie");
        assertThat(feed.getUrl()).isEqualTo("https://hln.be/rss/economie");
        assertThat(feed.isEnabled()).isTrue();
    }

    @Test
    void buildWithoutName() {
        assertThatThrownBy(() -> Feed.builder().url("https://hln.be/rss").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankName() {
        assertThatThrownBy(() -> Feed.builder().name(" ").url("https://hln.be/rss").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutUrl() {
        assertThatThrownBy(() -> Feed.builder().name("HLN Economie").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUrl() {
        assertThatThrownBy(() -> Feed.builder().name("HLN Economie").url("  ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Feed.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
