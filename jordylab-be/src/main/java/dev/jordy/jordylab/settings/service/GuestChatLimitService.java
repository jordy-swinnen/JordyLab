package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatLimit;
import dev.jordy.jordylab.settings.domain.repository.GuestChatLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The one place that decides a guest's daily chat limit (spec 014 FR-009 to FR-013, research R5): a personal row if the
 * admin set one, else the saved default, else the environment value. Both the quota shown above LibBot and the filter
 * that refuses the request read from here, so the two can never disagree.
 *
 * <p>Saved values are stored as given, including 0, which blocks chat. The environment value keeps its own rule (0 or
 * less falls back to 20 in {@link SettingsProperties}); saved values never pass through that coercion.
 */
@Service
@RequiredArgsConstructor
public class GuestChatLimitService {

    public enum LimitSource {
        PERSONAL, DEFAULT_SAVED, ENVIRONMENT
    }

    /** The number in force for one person, and where it came from. */
    public record EffectiveGuestChatLimit(int limit, LimitSource source) {
    }

    /** The saved default, with who set it, for the panel. */
    public record DefaultLimitView(int dailyLimit, LimitSource source, Instant updatedAt, String updatedBy) {
    }

    private final GuestChatLimitRepository guestChatLimitRepository;
    private final SettingsProperties settingsProperties;

    @Transactional(readOnly = true)
    public EffectiveGuestChatLimit effectiveFor(String userSubject) {
        return guestChatLimitRepository.findByUserSubject(userSubject)
                .map(row -> new EffectiveGuestChatLimit(row.getDailyLimit(), LimitSource.PERSONAL))
                .orElseGet(() -> new EffectiveGuestChatLimit(currentDefaultLimit(), currentDefaultSource()));
    }

    @Transactional(readOnly = true)
    public DefaultLimitView describeDefault() {
        return guestChatLimitRepository.findDefault()
                .map(row -> new DefaultLimitView(row.getDailyLimit(), LimitSource.DEFAULT_SAVED,
                        row.getLastModifiedDate(), row.getUpdatedBySubject()))
                .orElseGet(() -> new DefaultLimitView(settingsProperties.guestChat().dailyLimit(),
                        LimitSource.ENVIRONMENT, null, null));
    }

    @Transactional
    public DefaultLimitView saveDefault(int dailyLimit, String updatedBySubject) {
        validate(dailyLimit);
        GuestChatLimit row = guestChatLimitRepository.findDefault()
                .orElseGet(() -> GuestChatLimit.builder()
                        .dailyLimit(dailyLimit)
                        .updatedBySubject(updatedBySubject)
                        .build());
        row.changeDailyLimit(dailyLimit, updatedBySubject);
        guestChatLimitRepository.save(row);

        return new DefaultLimitView(dailyLimit, LimitSource.DEFAULT_SAVED, row.getLastModifiedDate(), updatedBySubject);
    }

    @Transactional
    public void savePersonal(String userSubject, int dailyLimit, String updatedBySubject) {
        validate(dailyLimit);
        GuestChatLimit row = guestChatLimitRepository.findByUserSubject(userSubject)
                .orElseGet(() -> GuestChatLimit.builder()
                        .userSubject(userSubject)
                        .dailyLimit(dailyLimit)
                        .updatedBySubject(updatedBySubject)
                        .build());
        row.changeDailyLimit(dailyLimit, updatedBySubject);
        guestChatLimitRepository.save(row);
    }

    @Transactional
    public void clearPersonal(String userSubject) {
        guestChatLimitRepository.deleteByUserSubject(userSubject);
    }

    private int currentDefaultLimit() {
        return guestChatLimitRepository.findDefault()
                .map(GuestChatLimit::getDailyLimit)
                .orElseGet(() -> settingsProperties.guestChat().dailyLimit());
    }

    private LimitSource currentDefaultSource() {
        return guestChatLimitRepository.findDefault()
                .map(row -> LimitSource.DEFAULT_SAVED)
                .orElse(LimitSource.ENVIRONMENT);
    }

    private void validate(int dailyLimit) {
        if (dailyLimit < 0) {
            throw new InvalidLimitException(dailyLimit);
        }
    }
}
