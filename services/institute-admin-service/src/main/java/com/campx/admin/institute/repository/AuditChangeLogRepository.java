package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.AuditChangeLog;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for capturing row-level data mutation audit trails (Item 27: {@code audit.change_log}).
 */
public interface AuditChangeLogRepository {

    AuditChangeLog recordChange(UserSecurityContext context, AuditChangeLog log);

    Optional<AuditChangeLog> findById(UserSecurityContext context, String id);

    List<AuditChangeLog> listChangesByRecord(UserSecurityContext context, String tableSchema, String tableName, String recordId);

    List<AuditChangeLog> listChangesByActor(UserSecurityContext context, String actorId);

    List<AuditChangeLog> listRecentChanges(UserSecurityContext context, int limit);
}
