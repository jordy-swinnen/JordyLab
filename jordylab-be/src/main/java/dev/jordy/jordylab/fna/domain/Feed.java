package dev.jordy.jordylab.fna.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Entity
@Table(schema = "finance", name = "feed")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends BaseEntity<Feed> {

    @Id
    private UUID id;

    private String name;

    private String url;

    private boolean enabled;

    public static class FeedBuilder {
        public Feed build() {
            Preconditions.checkArgument(StringUtils.hasText(name), "name is required");
            Preconditions.checkArgument(StringUtils.hasText(url), "url is required");
            if (id == null) id = UUID.randomUUID();

            return new Feed(id, name, url, enabled);
        }
    }
}
