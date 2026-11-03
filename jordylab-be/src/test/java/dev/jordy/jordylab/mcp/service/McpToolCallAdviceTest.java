package dev.jordy.jordylab.mcp.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jordy.jordylab.mcp.McpToolException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolCallAdviceTest {

    private static final String SUBJECT = "00000000-0000-0000-0000-00000000000a";

    private final McpRateLimiter rateLimiter = mock(McpRateLimiter.class);
    private final McpToolCallAdvice advice = new McpToolCallAdvice(rateLimiter);
    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    private final ListAppender<ILoggingEvent> auditEvents = new ListAppender<>();
    private final ListAppender<ILoggingEvent> warnings = new ListAppender<>();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("dev.jordy.jordylab.mcp.audit");
    private final Logger adviceLogger = (Logger) LoggerFactory.getLogger(McpToolCallAdvice.class);

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(ProbeTools.class.getMethod("probe"));
        when(joinPoint.getSignature()).thenReturn(signature);
        auditEvents.start();
        warnings.start();
        auditLogger.addAppender(auditEvents);
        adviceLogger.addAppender(warnings);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        auditLogger.detachAppender(auditEvents);
        adviceLogger.detachAppender(warnings);
    }

    @Test
    void writesOneAuditLineWithTheAdminToolAndOutcomeAndNoArguments() throws Throwable {
        when(rateLimiter.tryAcquire(SUBJECT)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = advice.aroundToolCall(joinPoint);

        assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo("result");
            softly.assertThat(auditEvents.list).hasSize(1);
            softly.assertThat(auditEvents.list.getFirst().getFormattedMessage())
                    .startsWith("mcp tool=probe admin=" + SUBJECT + " outcome=ok durationMs=");
        });
    }

    @Test
    void refusesOverTheLimitWithoutRunningTheTool() throws Throwable {
        when(rateLimiter.tryAcquire(SUBJECT)).thenReturn(false);

        assertThatThrownBy(() -> advice.aroundToolCall(joinPoint))
                .isInstanceOf(McpToolException.class)
                .hasMessage(McpToolCallAdvice.RATE_LIMITED_MESSAGE);
        verify(joinPoint, never()).proceed();
        assertSoftly(softly -> softly.assertThat(auditEvents.list.getFirst().getFormattedMessage())
                .startsWith("mcp tool=probe admin=" + SUBJECT + " outcome=rate_limited durationMs="));
    }

    @Test
    void replacesAnUnexpectedFailureWithTheSafeMessageAndLogsOnlyTheExceptionClass() throws Throwable {
        when(rateLimiter.tryAcquire(SUBJECT)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("SELECT secret FROM hidden"));

        assertThatThrownBy(() -> advice.aroundToolCall(joinPoint))
                .isInstanceOf(McpToolException.class)
                .hasMessage(McpToolCallAdvice.FAILED_MESSAGE);
        assertSoftly(softly -> {
            softly.assertThat(auditEvents.list.getFirst().getFormattedMessage()).contains("outcome=error");
            softly.assertThat(warnings.list.getFirst().getFormattedMessage())
                    .contains("IllegalStateException")
                    .doesNotContain("SELECT");
        });
    }

    @Test
    void passesThroughAToolsOwnSafeMessage() throws Throwable {
        when(rateLimiter.tryAcquire(SUBJECT)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new McpToolException("No game has that identifier."));

        assertThatThrownBy(() -> advice.aroundToolCall(joinPoint))
                .isInstanceOf(McpToolException.class)
                .hasMessage("No game has that identifier.");
    }

    static class ProbeTools {

        @McpTool(name = "probe", description = "A probe. Read-only.",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String probe() {
            return "result";
        }
    }
}
