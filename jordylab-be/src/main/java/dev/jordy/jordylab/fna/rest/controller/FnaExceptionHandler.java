package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.BriefingErrorDto;
import dev.jordy.jordylab.fna.service.BriefingGenerationException;
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
}
