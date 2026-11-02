package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.settings.service.UserSessionService;

import java.time.Instant;
import java.util.List;

public record UserSessionsResponse(List<UserSessionView> sessions) {

    public static UserSessionsResponse from(List<UserSessionService.PersonSession> sessions) {
        return new UserSessionsResponse(sessions.stream().map(UserSessionView::from).toList());
    }

    public record UserSessionView(String id, String kind, boolean offline, Instant startedAt, Instant lastAccessedAt) {

        public static UserSessionView from(UserSessionService.PersonSession session) {
            return new UserSessionView(session.id(), session.kind().name(), session.offline(), session.startedAt(),
                    session.lastAccessedAt());
        }
    }
}
