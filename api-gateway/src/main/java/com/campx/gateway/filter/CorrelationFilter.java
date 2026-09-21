package com.campx.gateway.filter;

import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.util.UUID;

/**
 * Filter responsible for extracting and establishing correlation tracing tokens
 * (traceId, tenantId, userId) and initializing LogContext for API Gateway routing.
 */
public class CorrelationFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

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
