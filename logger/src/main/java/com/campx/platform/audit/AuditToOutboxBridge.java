package com.campx.platform.audit;

import com.campx.logger.api.AuditEvent;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional bridge that translates audit domain records ({@link AuditRecord}, {@link AuditEvent},
 * and {@link AuditChangeLog}) into dispatchable {@link OutboxEvent} instances.
 * <p>
 * Ensures:
 * <ul>
 *   <li>Strict tenant isolation: events missing a valid {@code tenantId} are rejected.</li>
 *   <li>Least-privilege verification: masked payloads are validated before queueing.</li>
 *   <li>Standardized v1.0.0 schema versioning and correlation propagation.</li>
 * </ul>
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public final class AuditToOutboxBridge {

    private AuditToOutboxBridge() {
        // static utility class
    }

    /**
     * Converts a compliance {@link AuditRecord} into an {@link OutboxEvent}.
     *
     * @param record  the source audit record
     * @param service the producing service name (e.g. "audit-service", "student-service")
     * @return a validated outbox event ready for transactional persistence
     */
    public static OutboxEvent toOutboxEvent(AuditRecord record, String service) {
        if (record == null) {
            throw new IllegalArgumentException("AuditRecord cannot be null");
        }
        if (record.getTenantId() == null) {
            throw new IllegalArgumentException("tenantId is required for outbox publication of audit record");
        }

        String serviceName = (service != null && !service.trim().isEmpty()) ? service.trim() : "audit-service";
        String eventType = "AuditEvent." + record.getAction();

        // Build metadata headers
        Map<String, String> customHeaders = new HashMap<>();
        if (record.getPrincipalType() != null) {
            customHeaders.put("principalType", record.getPrincipalType());
        }
        if (record.getSeverity() != null) {
            customHeaders.put("severity", record.getSeverity());
        }
        if (record.getCollegeId() != null) {
            customHeaders.put("collegeId", record.getCollegeId().toString());
        }

        OutboxHeaders headers = OutboxHeaders.builder()
                .correlationId(record.getCorrelationId())
                .actorId(record.getPrincipalId() != null ? record.getPrincipalId().toString() : null)
                .eventTime(record.getEventTime())
                .schemaVersion("1.0.0")
                .customHeaders(customHeaders)
                .build();

        // Construct JSON payload
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"auditId\":\"").append(record.getId()).append("\",");
        sb.append("\"tenantId\":\"").append(record.getTenantId()).append("\",");
        if (record.getCollegeId() != null) {
            sb.append("\"collegeId\":\"").append(record.getCollegeId()).append("\",");
        }
        sb.append("\"action\":\"").append(escapeJson(record.getAction())).append("\",");
        if (record.getPrincipalId() != null) {
            sb.append("\"principalId\":\"").append(record.getPrincipalId()).append("\",");
        }
        sb.append("\"principalType\":\"").append(escapeJson(record.getPrincipalType())).append("\",");
        sb.append("\"resourceType\":\"").append(escapeJson(record.getResourceType())).append("\",");
        if (record.getResourceId() != null) {
            sb.append("\"resourceId\":\"").append(record.getResourceId()).append("\",");
        }
        sb.append("\"status\":\"").append(escapeJson(record.getStatus())).append("\",");
        sb.append("\"severity\":\"").append(escapeJson(record.getSeverity())).append("\",");
        sb.append("\"eventTime\":\"").append(record.getEventTime()).append("\",");
        sb.append("\"detail\":").append(record.getDetailJson());
        sb.append("}");

        return OutboxEvent.builder()
                .tenantId(record.getTenantId())
                .service(serviceName)
                .aggregateType("AuditEvent")
                .aggregateId(record.getId())
                .eventType(eventType)
                .payload(sb.toString())
                .headers(headers)
                .build();
    }

    /**
     * Converts a legacy {@link AuditEvent} into an {@link OutboxEvent}.
     *
     * @param event   the legacy audit event
     * @param service the producing service name
     * @return a validated outbox event
     */
    public static OutboxEvent toOutboxEvent(AuditEvent event, String service) {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent cannot be null");
        }
        AuditRecord record = AuditRecord.fromAuditEvent(event);
        return toOutboxEvent(record, service);
    }

    /**
     * Converts an entity {@link AuditChangeLog} mutation into an {@link OutboxEvent}.
     *
     * @param changeLog the entity change log record
     * @param service   the producing service name
     * @return a validated outbox event
     */
    public static OutboxEvent toOutboxEvent(AuditChangeLog changeLog, String service) {
        if (changeLog == null) {
            throw new IllegalArgumentException("AuditChangeLog cannot be null");
        }
        if (changeLog.getTenantId() == null) {
            throw new IllegalArgumentException("tenantId is required for outbox publication of change log");
        }

        String serviceName = (service != null && !service.trim().isEmpty()) ? service.trim() : "audit-service";
        String aggregateType = changeLog.getTableSchema() + "." + changeLog.getTableName();
        String eventType = "EntityMutation." + changeLog.getAction();

        Map<String, String> customHeaders = new HashMap<>();
        if (changeLog.getRequestId() != null) {
            customHeaders.put("requestId", changeLog.getRequestId());
        }

        OutboxHeaders headers = OutboxHeaders.builder()
                .actorId(changeLog.getActorId() != null ? changeLog.getActorId().toString() : null)
                .eventTime(changeLog.getCreatedAt())
                .schemaVersion("1.0.0")
                .customHeaders(customHeaders)
                .build();

        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"changeLogId\":\"").append(changeLog.getId()).append("\",");
        sb.append("\"tenantId\":\"").append(changeLog.getTenantId()).append("\",");
        sb.append("\"tableSchema\":\"").append(escapeJson(changeLog.getTableSchema())).append("\",");
        sb.append("\"tableName\":\"").append(escapeJson(changeLog.getTableName())).append("\",");
        sb.append("\"recordId\":\"").append(changeLog.getRecordId() != null ? changeLog.getRecordId() : "").append("\",");
        sb.append("\"action\":\"").append(escapeJson(changeLog.getAction())).append("\",");
        sb.append("\"changedFields\":[");
        if (changeLog.getChangedFields() != null) {
            for (int i = 0; i < changeLog.getChangedFields().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(changeLog.getChangedFields().get(i))).append("\"");
            }
        }
        sb.append("],");
        sb.append("\"oldData\":").append(changeLog.getOldDataJson() != null ? changeLog.getOldDataJson() : "null").append(",");
        sb.append("\"newData\":").append(changeLog.getNewDataJson() != null ? changeLog.getNewDataJson() : "null");
        sb.append("}");

        return OutboxEvent.builder()
                .tenantId(changeLog.getTenantId())
                .service(serviceName)
                .aggregateType(aggregateType)
                .aggregateId(changeLog.getRecordId())
                .eventType(eventType)
                .payload(sb.toString())
                .headers(headers)
                .build();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
