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
 * A console the admin registered (spec 013 US6): a platform (from the {@link PlatformCatalog} or a custom name) and the
 * name shown for it. The same platform can be registered more than once under different names.
 */
@Entity
@Table(schema = "gamecatalog", name = "console")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Console extends BaseEntity<Console> {

    public static final int MAX_NAME_LENGTH = 40;

    @Id
    private UUID id;

    private String platform;

    private String name;

    public String label() {
        return name;
    }

    public boolean isCustomPlatform() {
        return PlatformCatalog.find(platform).isEmpty();
    }

    public void rename(String newName) {
        Preconditions.checkArgument(StringUtils.hasText(newName), "name is required");
        String trimmed = newName.trim();
        Preconditions.checkArgument(trimmed.length() <= MAX_NAME_LENGTH, "name must not exceed %s characters",
                MAX_NAME_LENGTH);
        this.name = trimmed;
    }

    public static class ConsoleBuilder {
        public Console build() {
            Preconditions.checkArgument(StringUtils.hasText(platform), "platform is required");
            platform = PlatformCatalog.canonical(platform);
            if (!StringUtils.hasText(name)) {
                name = platform;
            }
            name = name.trim();
            Preconditions.checkArgument(name.length() <= MAX_NAME_LENGTH, "name must not exceed %s characters",
                    MAX_NAME_LENGTH);
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new Console(id, platform, name);
        }
    }
}
