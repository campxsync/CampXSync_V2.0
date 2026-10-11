package com.campx.admin.institute.repository;

import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.api.AuditEvent;

import java.util.List;
import java.util.Map;

/**
 * Repository interface for managing tamper-evident audit records in {@code audit.events} (ADM-01 Item 10).
 */
public interface SystemAuditLogRepository {

    /**
     * Records a cryptographically bound immutable audit event.
     *
     * @param context caller security context
     * @param event   audit event details
     * @return persisted audit event ID
     */
    String recordEvent(UserSecurityContext context, AuditEvent event);

    /**
     * Lists recent audit events for the caller's tenant.
     *
     * @param context caller security context
     * @param limit   maximum number of events
     * @return list of audit event maps
     */
    List<Map<String, Object>> listRecentEvents(UserSecurityContext context, int limit);
}
