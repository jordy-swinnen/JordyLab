package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.BriefingErrorDto;
import dev.jordy.jordylab.fna.service.ArticleAlreadyQueuedException;
import dev.jordy.jordylab.fna.service.BriefingGenerationException;
import dev.jordy.jordylab.fna.service.TickerAlreadyHeldException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FnaController.class)
public class FnaExceptionHandler {

    @ExceptionHandler(BriefingGenerationException.class)
    public ResponseEntity<BriefingErrorDto> handleBriefingGenerationFailure(BriefingGenerationException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new BriefingErrorDto(exception.getFailureReason().name()));
    }

    @ExceptionHandler(ArticleAlreadyQueuedException.class)
    public ResponseEntity<ErrorBody> handleArticleAlreadyQueued(ArticleAlreadyQueuedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("ARTICLE_ALREADY_QUEUED"));
    }

    /** A ticker is held or wishlisted, never both (spec 015 FR-003). */
    @ExceptionHandler(TickerAlreadyHeldException.class)
    public ResponseEntity<ErrorBody> handleTickerAlreadyHeld(TickerAlreadyHeldException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("TICKER_ALREADY_HELD"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_POSITION"));
    }

    private record ErrorBody(String reason) {
    }
}
