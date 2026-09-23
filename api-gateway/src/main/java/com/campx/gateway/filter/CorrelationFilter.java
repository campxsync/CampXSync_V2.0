package com.campx.gateway.filter;

import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.util.UUID;

/**
 * Filter responsible for extracting and establishing correlation tracing tokens
 * ({@code traceId}, {@code tenantId}, {@code userId}, {@code userRole}) and
 * initializing {@link LogContext} for API Gateway routing.
 * <p>
 * If incoming request headers omit a trace identifier, this filter generates
 * a cryptographically strong UUID-based trace token. The trace ID is then
 * injected into the response headers ({@code X-Trace-Id}) to support end-to-end
 * distributed request tracing across clients and backend services.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class CorrelationFilter {

    /**
     * HTTP Header name for distributed correlation trace identifier.
     */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * HTTP Header name for multi-tenant isolation context (Campus / College ID).
     */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * HTTP Header name for authenticated user principal identity.
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * HTTP Header name for caller security role (e.g. SUPER_ADMIN, COLLEGE_ADMIN).
     */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * Extracts correlation headers from the incoming exchange, populates the
     * ThreadLocal {@link LogContext}, and decorates response headers with the trace ID.
     *
     * @param exchange The incoming {@link HttpExchange} representing the client request.
     * @return The resolved or generated {@code traceId} string.
     */
    public String apply(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();

        // 1. Trace ID
        String traceId = headers.getFirst(HEADER_TRACE_ID);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        LogContext.setTraceId(traceId);

        // 2. Tenant ID (College / Campus / Organization)
        String tenantId = headers.getFirst(HEADER_TENANT_ID);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            LogContext.setTenantId(tenantId.trim());
        }

        // 3. User & Role
        String userId = headers.getFirst(HEADER_USER_ID);
        if (userId != null && !userId.trim().isEmpty()) {
            LogContext.setUserId(userId.trim());
        }

        String userRole = headers.getFirst(HEADER_USER_ROLE);
        if (userRole != null && !userRole.trim().isEmpty()) {
            LogContext.setUserRole(userRole.trim());
        }

        LogContext.setService("API-Gateway");

        // Add correlation ID to outgoing response headers
        exchange.getResponseHeaders().set(HEADER_TRACE_ID, traceId);

        return traceId;
    }
}
