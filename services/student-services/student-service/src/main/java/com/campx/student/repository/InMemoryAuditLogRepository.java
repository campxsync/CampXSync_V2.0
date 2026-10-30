package com.campx.student.repository;

import com.campx.platform.audit.AuditChangeLog;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory test and local development double for {@link AuditLogRepository}.
 * Captures audit events and row-level change logs without requiring live PostgreSQL.
 */
public class InMemoryAuditLogRepository implements AuditLogRepository {

    private final List<AuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final List<AuditChangeLog> changeLogs = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<AuditRecord>> eventsByTenant = new ConcurrentHashMap<>();

    @Override
    public void appendAuditEvent(Connection conn, AuditRecord record) throws SQLException {
        if (record == null) throw new IllegalArgumentException("AuditRecord cannot be null");
        auditRecords.add(record);
        if (record.getTenantId() != null) {
            eventsByTenant.computeIfAbsent(record.getTenantId(), k -> new CopyOnWriteArrayList<>()).add(record);
        }
    }

    @Override
    public void appendChangeLog(Connection conn, AuditChangeLog changeLog) throws SQLException {
        if (changeLog == null) throw new IllegalArgumentException("AuditChangeLog cannot be null");
        changeLogs.add(changeLog);
    }

    @Override
    public List<AuditRecord> findAuditEventsByTenant(Connection conn, UUID tenantId, int limit) throws SQLException {
        if (tenantId == null) return Collections.emptyList();
        List<AuditRecord> list = eventsByTenant.getOrDefault(tenantId, Collections.emptyList());
        return list.stream()
                .sorted((a, b) -> b.getEventTime().compareTo(a.getEventTime()))
                .limit(limit > 0 ? limit : 50)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditChangeLog> findChangeLogsByRecord(Connection conn, UUID tenantId, String tableSchema, String tableName, UUID recordId) throws SQLException {
        if (tenantId == null || recordId == null) return Collections.emptyList();
        return changeLogs.stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .filter(c -> recordId.equals(c.getRecordId()))
                .filter(c -> tableSchema == null || tableSchema.equalsIgnoreCase(c.getTableSchema()))
                .filter(c -> tableName == null || tableName.equalsIgnoreCase(c.getTableName()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<AuditRecord> getAuditRecords() {
        return Collections.unmodifiableList(auditRecords);
    }

    public List<AuditChangeLog> getChangeLogs() {
        return Collections.unmodifiableList(changeLogs);
    }

    public void clear() {
        auditRecords.clear();
        changeLogs.clear();
        eventsByTenant.clear();
    }
}
