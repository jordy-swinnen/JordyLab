package dev.jordy.jordylab.feedback.rest.controller;

import dev.jordy.jordylab.feedback.service.FeedbackAppNotAllowedException;
import dev.jordy.jordylab.feedback.service.FeedbackLimitReachedException;
import dev.jordy.jordylab.feedback.service.BacklogUnavailableException;
import dev.jordy.jordylab.feedback.service.TicketNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Stable error codes for the feedback API (contracts/rest-api.md), scoped to the module's
 * controllers. The entity's guards are the domain truth: an {@code IllegalArgumentException}
 * (blank or oversize text, unknown enum) maps to {@code 400 VALIDATION} and an
 * {@code IllegalStateException} (a transition out of a terminal state) to {@code 409 INVALID_STATE}.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {FeedbackTicketController.class, FeedbackInboxController.class})
public class FeedbackExceptionHandler {

    @ExceptionHandler(FeedbackAppNotAllowedException.class)
    public ResponseEntity<ErrorBody> handleAppNotAllowed(FeedbackAppNotAllowedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("APP_NOT_ALLOWED"));
    }

    @ExceptionHandler(FeedbackLimitReachedException.class)
    public ResponseEntity<ErrorBody> handleLimitReached(FeedbackLimitReachedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorBody("FEEDBACK_LIMIT_REACHED"));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(TicketNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND"));
    }

    @ExceptionHandler(BacklogUnavailableException.class)
    public ResponseEntity<ErrorBody> handleBacklogUnavailable(BacklogUnavailableException exception) {
        // The message names only the failing step (never the key or a request body); without it a
        // rejected credential would surface as a bare 503 with nothing in the log.
        log.warn("Backlog call failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody("BACKLOG_UNAVAILABLE"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleInvalidState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("INVALID_STATE"));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorBody> handleInvalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION"));
    }

    private record ErrorBody(String reason) {
    }
}
