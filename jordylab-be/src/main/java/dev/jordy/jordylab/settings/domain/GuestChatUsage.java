package dev.jordy.jordylab.settings.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One guest's message count for one calendar day (FR-009). The unique key member is the date,
 * so midnight — in the configured zone — is a fresh row and therefore a fresh budget. Counting
 * itself happens through {@code GuestChatUsageRepository}'s race-safe native upsert, never by
 * loading and re-saving this entity.
 */
@Entity
@Table(schema = "settings", name = "guest_chat_usage")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestChatUsage extends BaseEntity<GuestChatUsage> {

    @Id
    private UUID id;

    @Column(name = "user_subject")
    private String userSubject;

    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "message_count")
    private int messageCount;

    public static class GuestChatUsageBuilder {
        public GuestChatUsage build() {
            Preconditions.checkArgument(StringUtils.hasText(userSubject), "userSubject is required");
            Preconditions.checkArgument(usageDate != null, "usageDate is required");
            Preconditions.checkArgument(messageCount >= 1, "messageCount must be at least 1");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new GuestChatUsage(id, userSubject, usageDate, messageCount);
        }
    }
}
