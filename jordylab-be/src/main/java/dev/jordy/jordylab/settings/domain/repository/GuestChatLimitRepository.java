package dev.jordy.jordylab.settings.domain.repository;

import dev.jordy.jordylab.settings.domain.GuestChatLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GuestChatLimitRepository extends JpaRepository<GuestChatLimit, UUID> {

    Optional<GuestChatLimit> findByUserSubject(String userSubject);

    @Query("SELECT limit FROM GuestChatLimit limit WHERE limit.userSubject IS NULL")
    Optional<GuestChatLimit> findDefault();

    /** Idempotent: clearing a person's limit that was never set is not an error. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GuestChatLimit limit WHERE limit.userSubject = :userSubject")
    void deleteByUserSubject(@Param("userSubject") String userSubject);
}
