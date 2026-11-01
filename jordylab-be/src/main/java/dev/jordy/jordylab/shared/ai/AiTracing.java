package dev.jordy.jordylab.shared.ai;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The Langfuse side of an AI call: one span per {@link ResilientAiService} call, named after the feature, under which
 * Spring AI's own model spans (one per provider attempt: gateway, then the fallback) hang as generations. Attributes
 * use Langfuse's {@code langfuse.*} names. Prompts and answers are only added with {@code langfuse.capture-content}.
 */
@Component
@RequiredArgsConstructor
class AiTracing {

    private static final String RUN = "jordylab.ai.run";

    private final ObservationRegistry observationRegistry;
    private final LangfuseProperties properties;

    Observation start(AiFeature feature, List<Message> messages) {
        Observation observation = Observation.createNotStarted(RUN, observationRegistry)
                .contextualName("run-" + feature.key().replace('.', '-'))
                .lowCardinalityKeyValue("langfuse.observation.metadata.feature", feature.key());
        userId().ifPresent(id -> observation.highCardinalityKeyValue("langfuse.user.id", id));
        if (properties.captureContent()) {
            messages.stream().filter(message -> message.getMessageType() == MessageType.USER)
                    .reduce((first, last) -> last)
                    .ifPresent(last -> observation.highCardinalityKeyValue(LangfuseObservationFilter.INPUT,
                            last.getText()));
        }

        return observation.start();
    }

    void finish(Observation observation, AiCallResult result) {
        observation.lowCardinalityKeyValue("langfuse.observation.metadata.provider", result.provider());
        observation.lowCardinalityKeyValue("langfuse.observation.metadata.fallback_used",
                String.valueOf(result.fallbackUsed()));
        if (result.success()) {
            if (properties.captureContent()) {
                observation.highCardinalityKeyValue(LangfuseObservationFilter.OUTPUT, result.content());
            }
        } else {
            observation.highCardinalityKeyValue("langfuse.observation.level", "ERROR");
            observation.highCardinalityKeyValue("langfuse.observation.status_message",
                    String.valueOf(result.failureReason()));
        }
    }

    /**
     * Calls run on another thread (for the timeout), and the current observation lives in a thread-local: carry it over
     * so the model span lands under the feature's span instead of starting a trace of its own.
     */
    <T> Callable<T> inCurrentTrace(Callable<T> task) {
        Observation parent = observationRegistry.getCurrentObservation();
        if (parent == null) {
            return task;
        }

        return () -> {
            try (Observation.Scope ignored = parent.openScope()) {
                return task.call();
            }
        };
    }

    private static Optional<String> userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());

        return signedIn ? Optional.of(authentication.getName()) : Optional.empty();
    }
}
