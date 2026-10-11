package com.campx.admin.institute.repository;

import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.api.AuditEvent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory test double implementation of {@link SystemAuditLogRepository}.
 */
public class InMemorySystemAuditLogRepository implements SystemAuditLogRepository {

    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

    @Override
    public String recordEvent(UserSecurityContext context, AuditEvent event) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", eventId);
        record.put("tenantId", context != null && context.getTenantId() != null ? context.getTenantId().toString() : null);
        record.put("action", event != null ? event.getAction() : "ACTION");
        record.put("principalId", event != null ? event.getPrincipalId() : (context != null ? context.getUserId().toString() : null));
        record.put("resourceType", event != null ? event.getResourceType() : "SYSTEM");
        record.put("resourceId", event != null ? event.getResourceId() : null);
        record.put("status", event != null ? event.getStatus() : "SUCCESS");
        record.put("timestamp", event != null ? event.getTimestamp() : System.currentTimeMillis());
        events.add(record);
        return eventId;
    }

    @Override
    public List<Map<String, Object>> listRecentEvents(UserSecurityContext context, int limit) {
        String tenantStr = context != null && context.getTenantId() != null ? context.getTenantId().toString() : null;
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && results.size() < limit; i--) {
            Map<String, Object> evt = events.get(i);
            if (tenantStr == null || tenantStr.equals(evt.get("tenantId"))) {
                results.add(evt);
            }
        }
        return results;
    }
}
