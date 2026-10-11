package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AccessEvent;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link AccessEventRepository}.
 */
public class InMemoryAccessEventRepository implements AccessEventRepository {

    private final Map<String, AccessEvent> events = new ConcurrentHashMap<>();

    private static final Set<String> VALID_ACCESS_TYPES = new HashSet<>(Arrays.asList(
            "READ", "EXPORT", "PRINT", "DOWNLOAD"));

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public AccessEvent recordEvent(UserSecurityContext context, AccessEvent event) {
        checkContext(context);
        if (event == null) throw new MalformedPayloadException("AccessEvent cannot be null");
        if (event.getResourceType() == null || event.getResourceType().trim().isEmpty()) {
            throw new MalformedPayloadException("resourceType is required");
        }
        String accessType = event.getAccessType() != null ? event.getAccessType().trim().toUpperCase(Locale.ROOT) : "READ";
        if (!VALID_ACCESS_TYPES.contains(accessType)) {
            throw new MalformedPayloadException("Invalid accessType: " + accessType);
        }

        String tenantStr = context.getTenantId().toString();
        String id = event.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        AccessEvent copy = new AccessEvent();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setPrincipalId(event.getPrincipalId() != null ? event.getPrincipalId() : (context.getUserId() != null ? context.getUserId().toString() : null));
        copy.setResourceType(event.getResourceType().trim());
        copy.setResourceId(event.getResourceId());
        copy.setAccessType(accessType);
        copy.setSensitivity(event.getSensitivity());
        copy.setIp(event.getIp());
        copy.setReason(event.getReason());
        copy.setCreatedAt(event.getCreatedAt() > 0 ? event.getCreatedAt() : System.currentTimeMillis());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);

        events.put(id, copy);
        return copy;
    }

    @Override
    public Optional<AccessEvent> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        AccessEvent e = events.get(id);
        if (e != null && tenantStr.equals(e.getTenantId())) {
            return Optional.of(e);
        }
        return Optional.empty();
    }

    @Override
    public List<AccessEvent> listEventsByResource(UserSecurityContext context, String resourceType, String resourceId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return events.values().stream()
                .filter(e -> tenantStr.equals(e.getTenantId())
                        && Objects.equals(resourceType, e.getResourceType())
                        && (resourceId == null || Objects.equals(resourceId, e.getResourceId())))
                .sorted(Comparator.comparingLong(AccessEvent::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<AccessEvent> listEventsByPrincipal(UserSecurityContext context, String principalId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return events.values().stream()
                .filter(e -> tenantStr.equals(e.getTenantId()) && Objects.equals(principalId, e.getPrincipalId()))
                .sorted(Comparator.comparingLong(AccessEvent::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<AccessEvent> listRecentEvents(UserSecurityContext context, int limit) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        int max = limit > 0 ? limit : 50;
        return events.values().stream()
                .filter(e -> tenantStr.equals(e.getTenantId()))
                .sorted(Comparator.comparingLong(AccessEvent::getCreatedAt).reversed())
                .limit(max)
                .collect(Collectors.toList());
    }
}
