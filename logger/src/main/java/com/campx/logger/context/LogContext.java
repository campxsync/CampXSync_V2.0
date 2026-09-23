package com.campx.logger.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-local context storage (Mapped Diagnostic Context / MDC) for distributed tracing,
 * multi-tenant isolation, and user session tracking across CampXSync microservices.
 * <p>
 * Uses {@link InheritableThreadLocal} to automatically propagate context to child threads spawned
 * within request handling pipelines. Provides a scope guard {@link #withContext(String, String)}
 * for safe scoped execution in try-with-resources blocks.
 */
public final class LogContext {

    /** MDC key for distributed correlation trace identifier. */
    public static final String KEY_TRACE_ID = "traceId";
    /** MDC key for distributed span identifier. */
    public static final String KEY_SPAN_ID = "spanId";
    /** MDC key for multi-tenant college or institute code. */
    public static final String KEY_TENANT_ID = "tenantId";
    /** MDC key for authenticated user account identifier. */
    public static final String KEY_USER_ID = "userId";
    /** MDC key for authenticated user RBAC role or permission set. */
    public static final String KEY_USER_ROLE = "userRole";
    /** MDC key for publishing microservice name. */
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

    /**
     * Binds a key-value attribute into the current thread's diagnostic context.
     * If the value is {@code null}, any existing entry for the key is removed.
     *
     * @param key   diagnostic key
     * @param value diagnostic value to store
     */
    public static void put(String key, String value) {
        if (key == null) return;
        Map<String, String> map = CONTEXT.get();
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    /**
     * Retrieves the value bound to the specified diagnostic key on the current thread.
     *
     * @param key diagnostic key to query
     * @return current value, or {@code null} if not set
     */
    public static String get(String key) {
        if (key == null) return null;
        return CONTEXT.get().get(key);
    }

    /**
     * Removes the specified diagnostic key from the current thread's context.
     *
     * @param key diagnostic key to remove
     */
    public static void remove(String key) {
        if (key == null) return;
        CONTEXT.get().remove(key);
    }

    /**
     * Returns an unmodifiable snapshot copy of all attributes in the current thread's context map.
     *
     * @return immutable snapshot map of active context entries
     */
    public static Map<String, String> getCopyOfContextMap() {
        return Collections.unmodifiableMap(new HashMap<>(CONTEXT.get()));
    }

    /**
     * Replaces the current thread's diagnostic context with the contents of the given map.
     *
     * @param map context map to install, or {@code null} to simply clear the context
     */
    public static void setContextMap(Map<String, String> map) {
        CONTEXT.get().clear();
        if (map != null) {
            CONTEXT.get().putAll(map);
        }
    }

    /**
     * Clears all diagnostic properties from the current thread's context.
     */
    public static void clear() {
        CONTEXT.get().clear();
    }

    // Convenience helpers
    /**
     * Generates a new random hexadecimal trace ID, binds it to {@link #KEY_TRACE_ID}, and returns it.
     *
     * @return freshly generated trace ID string
     */
    public static String initTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        put(KEY_TRACE_ID, traceId);
        return traceId;
    }

    /**
     * Retrieves the current thread's active correlation trace ID.
     *
     * @return trace ID string, or {@code null} if unset
     */
    public static String getTraceId() {
        return get(KEY_TRACE_ID);
    }

    /**
     * Explicitly sets the correlation trace ID on the current thread.
     *
     * @param traceId trace ID to assign
     */
    public static void setTraceId(String traceId) {
        put(KEY_TRACE_ID, traceId);
    }

    /**
     * Sets the tenant identifier (e.g. college code) on the current thread.
     *
     * @param tenantId tenant identifier
     */
    public static void setTenantId(String tenantId) {
        put(KEY_TENANT_ID, tenantId);
    }

    /**
     * Retrieves the active tenant identifier from the current thread.
     *
     * @return tenant ID, or {@code null} if unset
     */
    public static String getTenantId() {
        return get(KEY_TENANT_ID);
    }

    /**
     * Sets the authenticated user identifier on the current thread.
     *
     * @param userId user account identifier
     */
    public static void setUserId(String userId) {
        put(KEY_USER_ID, userId);
    }

    /**
     * Retrieves the authenticated user identifier from the current thread.
     *
     * @return user ID, or {@code null} if unset
     */
    public static String getUserId() {
        return get(KEY_USER_ID);
    }

    /**
     * Sets the user authorization role on the current thread.
     *
     * @param role user RBAC role string
     */
    public static void setUserRole(String role) {
        put(KEY_USER_ROLE, role);
    }

    /**
     * Retrieves the user authorization role from the current thread.
     *
     * @return user role string, or {@code null} if unset
     */
    public static String getUserRole() {
        return get(KEY_USER_ROLE);
    }

    /**
     * Sets the microservice name property on the current thread.
     *
     * @param serviceName service identifier
     */
    public static void setService(String serviceName) {
        put(KEY_SERVICE, serviceName);
    }

    /**
     * Creates a scoped diagnostic property guard that restores the previous value upon close.
     * <pre>{@code
     * try (AutoCloseable guard = LogContext.withContext("orderId", orderId)) {
     *     processOrder();
     * }
     * }</pre>
     *
     * @param key   diagnostic key
     * @param value diagnostic value to set within the scope
     * @return an {@link AutoCloseable} that restores the prior diagnostic value
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
