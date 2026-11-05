package dev.jordy.jordylab.mcp.service;

import dev.jordy.jordylab.mcp.McpAdminIdentity;
import dev.jordy.jordylab.mcp.McpToolException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Wraps every {@code @McpTool} method (research R7). Before the tool runs it re-checks the account (FR-004) and applies the
 * per-admin limit, so a refused call never runs (SC-012). After it runs, one audit line names the admin, the tool and the
 * outcome, with no arguments and no result (FR-016). A failure reaches the model only as a fixed, safe message (FR-018).
 */
@Aspect
@Component
@RequiredArgsConstructor
public class McpToolCallAdvice {

    public static final String RATE_LIMITED_MESSAGE = "Too many requests in one minute. Try again shortly.";
    public static final String ACCOUNT_REFUSED_MESSAGE = "Your JordyLab account can no longer use the connector. Sign in again.";
    public static final String FAILED_MESSAGE = "The tool could not read this data right now.";

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("dev.jordy.jordylab.mcp.audit");
    private static final Logger LOG = LoggerFactory.getLogger(McpToolCallAdvice.class);

    private final McpRateLimiter rateLimiter;
    private final McpAccountStatusValidator accountStatusValidator;

    @Around("@annotation(org.springframework.ai.mcp.annotation.McpTool)")
    public Object aroundToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = toolName(joinPoint);
        String subject = McpAdminIdentity.currentSubject();
        long startedNanos = System.nanoTime();
        if (!accountStatusValidator.isActiveAdmin(subject)) {
            audit(toolName, subject, "refused", startedNanos);
            throw new McpToolException(ACCOUNT_REFUSED_MESSAGE);
        }
        if (!rateLimiter.tryAcquire(subject)) {
            audit(toolName, subject, "rate_limited", startedNanos);
            throw new McpToolException(RATE_LIMITED_MESSAGE);
        }
        try {
            Object result = joinPoint.proceed();
            audit(toolName, subject, "ok", startedNanos);
            return result;
        } catch (McpToolException safeFailure) {
            audit(toolName, subject, "error", startedNanos);
            throw safeFailure;
        } catch (RuntimeException failure) {
            LOG.warn("mcp tool={} failed with {}", toolName, failure.getClass().getSimpleName());
            audit(toolName, subject, "error", startedNanos);
            throw new McpToolException(FAILED_MESSAGE);
        }
    }

    private static String toolName(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        McpTool annotation = method.getAnnotation(McpTool.class);

        return annotation.name().isBlank() ? method.getName() : annotation.name();
    }

    private static void audit(String toolName, String subject, String outcome, long startedNanos) {
        long durationMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        AUDIT_LOG.info("mcp tool={} admin={} outcome={} durationMs={}", toolName, subject, outcome, durationMillis);
    }
}
