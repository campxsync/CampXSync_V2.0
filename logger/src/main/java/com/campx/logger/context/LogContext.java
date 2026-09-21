package com.campx.logger.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-local context storage (MDC) for distributed tracing, tenant context,
 * and user session tracking across CampXSync ERP services.
 */
public final class LogContext {

    public static final String KEY_TRACE_ID = "traceId";
    public static final String KEY_SPAN_ID = "spanId";
    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_USER_ROLE = "userRole";
    public static final String KEY_SERVICE = "service";

    private static final InheritableThreadLocal<Map<String, String>> CONTEXT =
            new InheritableThreadLocal<Map<String, String>>() {
                @Override
                protected Map<String, String> initialValue() {
                    return new HashMap<>();
                }

                @Override
                protected Map<String, String> childValue(Map<String, String> parentValue) {
                    return parentValue != null ? new HashMap<>(parentValue) : new HashMap<String, String>();
                }
            };

    private LogContext() {}

    public static void put(String key, String value) {
        if (key == null) return;
        Map<String, String> map = CONTEXT.get();
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    public static String get(String key) {
        if (key == null) return null;
        return CONTEXT.get().get(key);
    }

    public static void remove(String key) {
        if (key == null) return;
        CONTEXT.get().remove(key);
    }

    public static Map<String, String> getCopyOfContextMap() {
        return Collections.unmodifiableMap(new HashMap<>(CONTEXT.get()));
    }

    public static void setContextMap(Map<String, String> map) {
        CONTEXT.get().clear();
        if (map != null) {
            CONTEXT.get().putAll(map);
        }
    }

    public static void clear() {
        CONTEXT.get().clear();
    }

    // Convenience helpers
    public static String initTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        put(KEY_TRACE_ID, traceId);
        return traceId;
    }

    public static String getTraceId() {
        return get(KEY_TRACE_ID);
    }

    public static void setTraceId(String traceId) {
        put(KEY_TRACE_ID, traceId);
    }

    public static void setTenantId(String tenantId) {
        put(KEY_TENANT_ID, tenantId);
    }

    public static String getTenantId() {
        return get(KEY_TENANT_ID);
    }

    public static void setUserId(String userId) {
        put(KEY_USER_ID, userId);
    }

    public static String getUserId() {
        return get(KEY_USER_ID);
    }

    public static void setUserRole(String role) {
        put(KEY_USER_ROLE, role);
    }

    public static void setService(String serviceName) {
        put(KEY_SERVICE, serviceName);
    }

    /**
     * Scope guard for managing contextual lifecycle in a try-with-resources block.
     */
    public static AutoCloseable withContext(String key, String value) {
        String previous = get(key);
        put(key, value);
        return () -> {
            if (previous != null) {
                put(key, previous);
            } else {
                remove(key);
            }
        };
    }
}
