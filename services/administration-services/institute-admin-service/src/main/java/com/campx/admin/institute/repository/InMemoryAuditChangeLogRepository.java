package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AuditChangeLog;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link AuditChangeLogRepository}.
 */
public class InMemoryAuditChangeLogRepository implements AuditChangeLogRepository {

    private final Map<String, AuditChangeLog> logs = new ConcurrentHashMap<>();

    private static final Set<String> VALID_ACTIONS = new HashSet<>(Arrays.asList("I", "U", "D"));

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public AuditChangeLog recordChange(UserSecurityContext context, AuditChangeLog log) {
        checkContext(context);
        if (log == null) throw new MalformedPayloadException("AuditChangeLog cannot be null");
        if (log.getTableSchema() == null || log.getTableSchema().trim().isEmpty()) {
            throw new MalformedPayloadException("tableSchema is required");
        }
        if (log.getTableName() == null || log.getTableName().trim().isEmpty()) {
            throw new MalformedPayloadException("tableName is required");
        }
        String action = log.getAction() != null ? log.getAction().trim().toUpperCase(Locale.ROOT) : "I";
        if (!VALID_ACTIONS.contains(action)) {
            throw new MalformedPayloadException("Invalid action (must be I, U, or D): " + action);
        }

        String tenantStr = context.getTenantId().toString();
        String id = log.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        AuditChangeLog copy = new AuditChangeLog();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setTableSchema(log.getTableSchema().trim().toLowerCase(Locale.ROOT));
        copy.setTableName(log.getTableName().trim().toLowerCase(Locale.ROOT));
        copy.setRecordId(log.getRecordId());
        copy.setAction(action);
        copy.setChangedFields(log.getChangedFields() != null ? new ArrayList<>(log.getChangedFields()) : Collections.emptyList());
        copy.setOldData(log.getOldData());
        copy.setNewData(log.getNewData());
        copy.setActorId(log.getActorId() != null ? log.getActorId() : (context.getUserId() != null ? context.getUserId().toString() : null));
        copy.setRequestId(log.getRequestId());
        copy.setCreatedAt(log.getCreatedAt() > 0 ? log.getCreatedAt() : System.currentTimeMillis());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);

        logs.put(id, copy);
        return copy;
    }

    @Override
    public Optional<AuditChangeLog> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        AuditChangeLog l = logs.get(id);
        if (l != null && tenantStr.equals(l.getTenantId())) {
            return Optional.of(l);
        }
        return Optional.empty();
    }

    @Override
    public List<AuditChangeLog> listChangesByRecord(UserSecurityContext context, String tableSchema, String tableName, String recordId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        String schema = tableSchema != null ? tableSchema.trim().toLowerCase(Locale.ROOT) : null;
        String table = tableName != null ? tableName.trim().toLowerCase(Locale.ROOT) : null;

        return logs.values().stream()
                .filter(l -> tenantStr.equals(l.getTenantId())
                        && Objects.equals(schema, l.getTableSchema())
                        && Objects.equals(table, l.getTableName())
                        && Objects.equals(recordId, l.getRecordId()))
                .sorted(Comparator.comparingLong(AuditChangeLog::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditChangeLog> listChangesByActor(UserSecurityContext context, String actorId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return logs.values().stream()
                .filter(l -> tenantStr.equals(l.getTenantId()) && Objects.equals(actorId, l.getActorId()))
                .sorted(Comparator.comparingLong(AuditChangeLog::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditChangeLog> listRecentChanges(UserSecurityContext context, int limit) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        int max = limit > 0 ? limit : 50;
        return logs.values().stream()
                .filter(l -> tenantStr.equals(l.getTenantId()))
                .sorted(Comparator.comparingLong(AuditChangeLog::getCreatedAt).reversed())
                .limit(max)
                .collect(Collectors.toList());
    }
}
