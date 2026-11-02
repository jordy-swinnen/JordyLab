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

import java.util.UUID;

/**
 * One daily guest chat budget (spec 014 FR-009 to FR-011). A row without a {@code userSubject} is the saved default
 * (at most one, enforced by the migration's partial unique index); a row with a subject is that person's personal
 * override. The environment value is not stored here: it applies only while no default row exists.
 *
 * <p>A saved value of 0 is a real limit that blocks chat. The environment value has different rules (0 or less falls
 * back to 20 in {@code SettingsProperties}); that coercion must never reach a saved row.
 */
@Entity
@Table(schema = "settings", name = "guest_chat_limit")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestChatLimit extends BaseEntity<GuestChatLimit> {

    @Id
    private UUID id;

    /** {@code null} marks the saved default row. */
    @Column(name = "user_subject")
    private String userSubject;

    @Column(name = "daily_limit")
    private int dailyLimit;

    @Column(name = "updated_by_subject")
    private String updatedBySubject;

    public boolean isDefault() {
        return userSubject == null;
    }

    public void changeDailyLimit(int newDailyLimit, String newUpdatedBySubject) {
        Preconditions.checkArgument(newDailyLimit >= 0, "dailyLimit must not be negative");
        Preconditions.checkArgument(StringUtils.hasText(newUpdatedBySubject), "updatedBySubject is required");
        this.dailyLimit = newDailyLimit;
        this.updatedBySubject = newUpdatedBySubject;
    }

    public static class GuestChatLimitBuilder {
        public GuestChatLimit build() {
            Preconditions.checkArgument(dailyLimit >= 0, "dailyLimit must not be negative");
            Preconditions.checkArgument(StringUtils.hasText(updatedBySubject), "updatedBySubject is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new GuestChatLimit(id, userSubject, dailyLimit, updatedBySubject);
        }
    }
}
