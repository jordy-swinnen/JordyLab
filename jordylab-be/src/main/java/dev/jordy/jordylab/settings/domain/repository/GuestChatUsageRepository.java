package dev.jordy.jordylab.settings.domain.repository;

import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface GuestChatUsageRepository extends JpaRepository<GuestChatUsage, UUID> {

    Optional<GuestChatUsage> findByUserSubjectAndUsageDate(String userSubject, LocalDate usageDate);

    /**
     * Race-safe increment for concurrent chats on the same day (data-model.md): the candidate
     * id is only used on first insert for the day, ignored on conflict.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO settings.guest_chat_usage (id, user_subject, usage_date, message_count, "
            + "created_at, updated_at) VALUES (:id, :userSubject, :usageDate, 1, now(), now()) "
            + "ON CONFLICT (user_subject, usage_date) "
            + "DO UPDATE SET message_count = guest_chat_usage.message_count + 1, updated_at = now()",
            nativeQuery = true)
    void incrementUsage(@Param("id") UUID id, @Param("userSubject") String userSubject,
            @Param("usageDate") LocalDate usageDate);
}
