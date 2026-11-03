package dev.jordy.jordylab.feedback.domain.repository;

import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackTicketRepository extends JpaRepository<FeedbackTicket, UUID> {

    /**
     * The guest's own list, newest first — the only ticket surface a guest ever gets.
     */
    List<FeedbackTicket> findBySenderSubjectOrderByCreatedDateDesc(String senderSubject);

    /**
     * The anonymisation pass after an account loses its access.
     */
    List<FeedbackTicket> findBySenderSubject(String senderSubject);

    /**
     * The daily submission budget: how many tickets this sender created since the window started.
     */
    long countBySenderSubjectAndCreatedDateAfter(String senderSubject, Instant threshold);

    /**
     * The admin inbox, newest first (all states, or one state when filtered).
     */
    List<FeedbackTicket> findAllByOrderByCreatedDateDesc();

    List<FeedbackTicket> findAllByStateOrderByCreatedDateDesc(FeedbackTicketState state);

    /**
     * Loads one ticket under a pessimistic write lock: the accept flow holds it while talking to
     * Linear, so a double click serialises on the row instead of creating a second issue (FR-009).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from FeedbackTicket ticket where ticket.id = :id")
    Optional<FeedbackTicket> findByIdForAccept(@Param("id") UUID id);
}
