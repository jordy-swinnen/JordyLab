package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * A guest's daily LibBot allowance (FR-009): how much is left today and when it resets, and the one place the count goes
 * up. Admins are exempt and see no limit.
 */
@Service
@RequiredArgsConstructor
public class GuestChatQuotaService {

    private final GuestChatUsageRepository guestChatUsageRepository;
    private final SettingsProperties settingsProperties;
    private final Clock clock;

    /** {@code remaining} is null for an exempt (admin) user. */
    public record Quota(Integer limit, Integer remaining, Instant resetsAt, boolean exempt) {
    }

    @Transactional(readOnly = true)
    public Quota quotaFor(String userSubject, boolean admin) {
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant resetsAt = today.plusDays(1).atStartOfDay(zone).toInstant();
        if (admin) {
            return new Quota(null, null, resetsAt, true);
        }
        int limit = settingsProperties.guestChat().dailyLimit();
        int used = guestChatUsageRepository.findByUserSubjectAndUsageDate(userSubject, today)
                .map(GuestChatUsage::getMessageCount)
                .orElse(0);

        return new Quota(limit, Math.max(0, limit - used), resetsAt, false);
    }

    @Transactional
    public void countAnsweredMessage(String userSubject) {
        guestChatUsageRepository.incrementUsage(UUID.randomUUID(), userSubject, LocalDate.now(clock.withZone(zone())));
    }

    private ZoneId zone() {
        return ZoneId.of(settingsProperties.guestChat().zone());
    }
}
