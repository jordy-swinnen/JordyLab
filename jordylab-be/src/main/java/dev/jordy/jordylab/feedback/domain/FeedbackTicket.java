package dev.jordy.jordylab.feedback.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.feedback.FeedbackTicketCreated;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One piece of guest feedback (spec 017): a ticket in the idea box. States move
 * {@code NEW} → {@code ACCEPTED}, {@code BACKLOG} or {@code REJECTED}; {@code BACKLOG} may still
 * become {@code ACCEPTED} or {@code REJECTED}; {@code ACCEPTED} and {@code REJECTED} are terminal
 * (FR-007). A ticket carries exactly two admin texts — the guest-visible reply and the private
 * note that is included in the Linear issue when the ticket is accepted with one set (FR-008/009).
 */
@Entity
@Table(schema = "feedback", name = "feedback_ticket")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackTicket extends BaseEntity<FeedbackTicket> {

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_TEXT_LENGTH = 5000;
    public static final int MAX_ACCEPT_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private FeedbackApp app;

    @Enumerated(EnumType.STRING)
    private FeedbackType type;

    private String title;

    private String description;

    @Column(name = "sender_subject")
    private String senderSubject;

    @Column(name = "sender_display_name")
    private String senderDisplayName;

    @Enumerated(EnumType.STRING)
    private FeedbackTicketState state;

    private String reply;

    @Column(name = "private_note")
    private String privateNote;

    @Column(name = "linear_issue_id")
    private String linearIssueId;

    @Column(name = "linear_issue_url")
    private String linearIssueUrl;

    @Column(name = "accept_error")
    private String acceptError;

    /**
     * Keeps the ticket in the app for later; it can still become accepted or rejected.
     */
    public void moveToBacklog() {
        Preconditions.checkState(state == FeedbackTicketState.NEW, "only a NEW ticket can move to backlog");
        this.state = FeedbackTicketState.BACKLOG;
    }

    /**
     * Archives the ticket as rejected — final, view-only afterwards (FR-007).
     */
    public void reject() {
        Preconditions.checkState(state == FeedbackTicketState.NEW || state == FeedbackTicketState.BACKLOG,
                "only a NEW or BACKLOG ticket can be rejected");
        this.state = FeedbackTicketState.REJECTED;
    }

    /**
     * Records the one Linear issue created for this ticket. Terminal, and the idempotency anchor:
     * a ticket with an issue id never calls Linear again (FR-009).
     */
    public void markAccepted(String issueId, String issueUrl) {
        Preconditions.checkArgument(StringUtils.hasText(issueId), "linear issue id is required");
        Preconditions.checkArgument(StringUtils.hasText(issueUrl), "linear issue url is required");
        Preconditions.checkState(state == FeedbackTicketState.NEW || state == FeedbackTicketState.BACKLOG,
                "only a NEW or BACKLOG ticket can be accepted");
        this.linearIssueId = issueId;
        this.linearIssueUrl = issueUrl;
        this.acceptError = null;
        this.state = FeedbackTicketState.ACCEPTED;
    }

    /**
     * Replaces both admin texts wholesale; a blank value clears its text.
     */
    public void updateTexts(String newReply, String newPrivateNote) {
        Preconditions.checkArgument(newReply != null, "reply is required (an empty string clears it)");
        Preconditions.checkArgument(newPrivateNote != null, "privateNote is required (an empty string clears it)");
        Preconditions.checkArgument(newReply.length() <= MAX_TEXT_LENGTH, "reply is too long");
        Preconditions.checkArgument(newPrivateNote.length() <= MAX_TEXT_LENGTH, "privateNote is too long");
        this.reply = normalise(newReply);
        this.privateNote = normalise(newPrivateNote);
    }

    /**
     * Stores the last Linear failure so the admin can see why Accept failed; the state stays put
     * and the accept remains retryable (FR-019).
     */
    public void recordAcceptError(String message) {
        Preconditions.checkState(state == FeedbackTicketState.NEW || state == FeedbackTicketState.BACKLOG,
                "only a NEW or BACKLOG ticket can record an accept error");
        this.acceptError = message != null && message.length() > MAX_ACCEPT_ERROR_LENGTH
                ? message.substring(0, MAX_ACCEPT_ERROR_LENGTH)
                : message;
    }

    /**
     * Drops the sender's display name when the account loses its access; the ticket stays as
     * history, without personal data (spec edge case).
     */
    public void anonymiseSender() {
        this.senderDisplayName = null;
    }

    private static String normalise(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    public static class FeedbackTicketBuilder {
        public FeedbackTicket build() {
            Preconditions.checkArgument(app != null, "app is required");
            Preconditions.checkArgument(type != null, "type is required");
            Preconditions.checkArgument(StringUtils.hasText(title), "title is required");
            Preconditions.checkArgument(title.trim().length() <= MAX_TITLE_LENGTH, "title is too long");
            Preconditions.checkArgument(StringUtils.hasText(description), "description is required");
            Preconditions.checkArgument(description.trim().length() <= MAX_DESCRIPTION_LENGTH,
                    "description is too long");
            Preconditions.checkArgument(StringUtils.hasText(senderSubject), "senderSubject is required");
            Preconditions.checkArgument(state != null, "state is required");
            if (id == null) {
                id = UUID.randomUUID();
            }
            title = title.trim();
            description = description.trim();
            FeedbackTicket ticket = new FeedbackTicket(id, app, type, title, description, senderSubject,
                    senderDisplayName, state, reply, privateNote, linearIssueId, linearIssueUrl, acceptError);
            ticket.registerEvent(new FeedbackTicketCreated(
                    ticket.senderDisplayName == null ? "A guest" : ticket.senderDisplayName,
                    app.getDisplayName(),
                    type.getDisplayName()));

            return ticket;
        }
    }
}
