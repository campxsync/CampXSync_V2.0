package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.AccessEvent;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for recording and auditing sensitive data access events (Item 26:
 * {@code audit.access_events}).
 */
public interface AccessEventRepository {

    AccessEvent recordEvent(UserSecurityContext context, AccessEvent event);

    Optional<AccessEvent> findById(UserSecurityContext context, String id);

    List<AccessEvent> listEventsByResource(UserSecurityContext context, String resourceType, String resourceId);

    List<AccessEvent> listEventsByPrincipal(UserSecurityContext context, String principalId);

    List<AccessEvent> listRecentEvents(UserSecurityContext context, int limit);
}
