package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotOutcome;

import java.util.List;
import java.util.UUID;

/** The one {@code answer} event of a LibBot stream (spec 013 contracts/libbot-api.md). */
public record LibBotAnswerResponse(LibBotOutcome outcome, String language, String text, List<String> applied,
        Unknown unknown, List<Reference> references) {

    public record Unknown(int count, String note) {
    }

    public record Reference(UUID gameId, String title, List<PlatformChip> platforms, Cover cover) {
    }

    public record Cover(ArtworkStatus status, String externalUrl, String localUrl) {
    }
}
