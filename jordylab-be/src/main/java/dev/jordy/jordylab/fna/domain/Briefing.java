package dev.jordy.jordylab.fna.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "finance", name = "briefing")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Briefing extends BaseEntity<Briefing> {

    @Id
    private UUID id;

    private Instant generatedAt;

    private String content;

    private String modelUsed;

    public static class BriefingBuilder {
        public Briefing build() {
            Preconditions.checkArgument(StringUtils.hasText(content), "content is required");
            Preconditions.checkArgument(StringUtils.hasText(modelUsed), "modelUsed is required");
            Preconditions.checkArgument(generatedAt != null, "generatedAt is required");
            if (id == null) id = UUID.randomUUID();

            return new Briefing(id, generatedAt, content, modelUsed);
        }
    }
}
