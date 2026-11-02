package com.campx.admissions.logging;

import com.campx.admissions.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise structured logging service for STM-02 Admissions Domain Service.
 * <p>
 * Provides centralized context-aware logging, PII sanitization/redaction,
 * request/response telemetry, domain lifecycle audit logging, and error tracking.
 * Adheres to zero-credential-exposure and data minimization standards.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class AdmissionsLoggingService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsLoggingService.class);

    // Regex patterns for sensitive PII masking
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9_.+-])([a-zA-Z0-9_.+-]*?)([a-zA-Z0-9_.+-])?@([a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+[0-9]{2}|[0-9]{3})[0-9]+([0-9]{4})\\b");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(password|secret|token|apiKey|signature|authorization)\\s*[:=]\\s*[\"']?([^\"'\\s,]+)[\"']?");

    private final String serviceName;

    public AdmissionsLoggingService() {
        this("admissions-service");
    }

    public AdmissionsLoggingService(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Logs an incoming REST request with caller context and correlation tracing.
     *
     * @param method        HTTP verb (e.g. GET, POST).
     * @param path          Request URI path.
     * @param correlationId Distributed correlation ID.
     * @param tenantId      Tenant identifier.
     * @param userId        Authenticated user identifier.
     * @param role          Authenticated user role.
     */
    public void logRequest(String method, String path, String correlationId, String tenantId, String userId, String role) {
        logger.info("[HTTP_IN] method={} path={} correlationId={} tenantId={} userId={} role={}",
                method, path, sanitize(correlationId), sanitize(tenantId), sanitize(userId), sanitize(role));
    }

    /**
     * Logs an outgoing REST response with status code, execution duration, and correlation tracing.
     *
     * @param method        HTTP verb.
     * @param path          Request URI path.
     * @param statusCode    HTTP response status code.
     * @param durationMs    Elapsed processing duration in milliseconds.
     * @param correlationId Distributed correlation ID.
     */
    public void logResponse(String method, String path, int statusCode, long durationMs, String correlationId) {
        if (statusCode >= 500) {
            logger.error("[HTTP_OUT] method={} path={} status={} durationMs={}ms correlationId={}",
                    method, path, statusCode, durationMs, sanitize(correlationId));
        } else if (statusCode >= 400) {
            logger.warn("[HTTP_OUT] method={} path={} status={} durationMs={}ms correlationId={}",
                    method, path, statusCode, durationMs, sanitize(correlationId));
        } else {
            logger.info("[HTTP_OUT] method={} path={} status={} durationMs={}ms correlationId={}",
                    method, path, statusCode, durationMs, sanitize(correlationId));
        }
    }

    /**
     * Logs an application lifecycle transition.
     *
     * @param tenantId      Tenant UUID.
     * @param applicationId Application UUID.
     * @param fromStatus    Previous lifecycle status.
     * @param toStatus      New lifecycle status.
     * @param action        Triggering action.
     * @param actorId       Actor UUID performing the transition.
     */
    public void logLifecycleTransition(UUID tenantId, UUID applicationId, String fromStatus, String toStatus, String action, UUID actorId) {
        logger.info("[LIFECYCLE_TRANSITION] tenant={} app={} transition='{} -> {}' action='{}' actor={}",
                tenantId, applicationId, fromStatus, toStatus, action, actorId);
    }

    /**
     * Logs an RBAC/ABAC security violation or Segregation of Duties rejection.
     *
     * @param context       User security context.
     * @param action        Target operation attempted.
     * @param reason        Policy failure justification.
     * @param correlationId Request correlation ID.
     */
    public void logSecurityViolation(UserSecurityContext context, String action, String reason, String correlationId) {
        String actor = context != null ? context.getUserId().toString() : "ANONYMOUS";
        String tenant = context != null ? String.valueOf(context.getTenantId()) : "NONE";
        String role = context != null ? context.getRole() : "NONE";

        logger.warn("[SECURITY_VIOLATION] action='{}' reason='{}' actor={} tenant={} role={} correlationId={}",
                action, maskPii(reason), actor, tenant, role, sanitize(correlationId));
    }

    /**
     * Logs a downstream student matriculation handover event.
     *
     * @param tenantId      Tenant UUID.
     * @param applicationId Application UUID.
     * @param studentId     Created STM-01 student UUID.
     * @param status        Handover status (STUDENT_CREATED, FAILED).
     * @param durationMs    Elapsed duration in milliseconds.
     */
    public void logHandover(UUID tenantId, UUID applicationId, UUID studentId, String status, long durationMs) {
        logger.info("[HANDOVER] tenant={} app={} student={} status={} durationMs={}ms",
                tenantId, applicationId, studentId, status, durationMs);
    }

    /**
     * Logs an operational audit event for compliance tracking.
     *
     * @param context        User security context.
     * @param eventType      Audit event classification.
     * @param targetResource Target entity identifier.
     * @param metadata       Diagnostic metadata.
     */
    public void logAudit(UserSecurityContext context, String eventType, String targetResource, Map<String, Object> metadata) {
        String actor = context != null ? context.getUserId().toString() : "SYSTEM";
        String tenant = context != null ? String.valueOf(context.getTenantId()) : "SYSTEM";
        logger.info("[AUDIT] eventType='{}' target='{}' actor={} tenant={} metadata={}",
                eventType, targetResource, actor, tenant, maskPii(String.valueOf(metadata)));
    }

    /**
     * Logs an unexpected error or unhandled exception with correlation tracing.
     *
     * @param operation     Operation being performed.
     * @param throwable     Exception thrown.
     * @param correlationId Correlation ID.
     */
    public void logError(String operation, Throwable throwable, String correlationId) {
        logger.error("[ERROR] operation='{}' correlationId={} error='{}'",
                operation, sanitize(correlationId), maskPii(throwable.getMessage()), throwable);
    }

    /**
     * Masks PII data (emails, phone numbers, secret tokens) to protect sensitive data in logs.
     *
     * @param input Raw text.
     * @return Sanitized text with redacted sensitive fields.
     */
    public static String maskPii(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Mask email addresses: user@domain.com -> u***r@domain.com
        Matcher emailMatcher = EMAIL_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (emailMatcher.find()) {
            String firstChar = emailMatcher.group(1);
            String lastChar = emailMatcher.group(3) != null ? emailMatcher.group(3) : "";
            String domain = emailMatcher.group(4);
            emailMatcher.appendReplacement(sb, Matcher.quoteReplacement(firstChar + "***" + lastChar + "@" + domain));
        }
        emailMatcher.appendTail(sb);
        String masked = sb.toString();

        // Mask phone numbers: +919876543210 -> +91*****3210
        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        sb = new StringBuffer();
        while (phoneMatcher.find()) {
            String prefix = phoneMatcher.group(1);
            String suffix = phoneMatcher.group(2);
            phoneMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + "*****" + suffix));
        }
        phoneMatcher.appendTail(sb);
        masked = sb.toString();

        // Mask secrets, tokens, passwords
        Matcher secretMatcher = SECRET_PATTERN.matcher(masked);
        sb = new StringBuffer();
        while (secretMatcher.find()) {
            String key = secretMatcher.group(1);
            secretMatcher.appendReplacement(sb, Matcher.quoteReplacement(key + "=[REDACTED]"));
        }
        secretMatcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Sanitizes inputs to prevent log injection (CRLF attacks).
     *
     * @param input Raw string.
     * @return Cleaned string without carriage return or newline characters.
     */
    public static String sanitize(String input) {
        if (input == null) return "null";
        return input.replace('\n', '_').replace('\r', '_');
    }
}
