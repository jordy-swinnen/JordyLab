package dev.jordy.jordylab.gamecatalog.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
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
 * A machine that scans libraries. The hostname comes from the machine; the optional display name is the owner's own
 * name for it and wins everywhere (spec 013 FR-030). {@link #label()} is the only place that decides which of the two
 * is shown, so no screen or answer repeats that rule.
 */
@Entity
@Table(schema = "gamecatalog", name = "host")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Host extends BaseEntity<Host> {

    public static final int MAX_DISPLAY_NAME_LENGTH = 40;

    @Id
    private UUID id;

    private String hostname;

    private String displayName;

    /** The name to show: the display name when set, otherwise the hostname. */
    public String label() {
        return StringUtils.hasText(displayName) ? displayName : hostname;
    }

    /** Sets the display name; a blank value clears it so the hostname is shown again (FR-031). */
    public void rename(String newDisplayName) {
        String trimmed = newDisplayName == null ? "" : newDisplayName.trim();
        Preconditions.checkArgument(trimmed.length() <= MAX_DISPLAY_NAME_LENGTH,
                "display name must not exceed %s characters", MAX_DISPLAY_NAME_LENGTH);
        this.displayName = trimmed.isEmpty() ? null : trimmed;
        registerEvent(new HostRenamed(id, label()));
    }

    public static class HostBuilder {
        public Host build() {
            Preconditions.checkArgument(StringUtils.hasText(hostname), "hostname is required");
            Preconditions.checkArgument(displayName == null || displayName.length() <= MAX_DISPLAY_NAME_LENGTH,
                    "display name must not exceed %s characters", MAX_DISPLAY_NAME_LENGTH);
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new Host(id, hostname, displayName);
        }
    }
}
