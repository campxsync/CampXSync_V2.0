package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.model.StudentCorrectionRequestEntity;
import com.campx.student.security.UserSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * PostgreSQL JDBC repository for profile correction requests in {@code doc.lifecycle_requests}.
 * Enforces segregation of duties and audit tracking for student profile corrections (STM01-US-010, STM01-US-011).
 */
public class PostgresCorrectionRequestRepository implements CorrectionRequestRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCorrectionRequestRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DatabaseConnectionManager connectionManager;

    public PostgresCorrectionRequestRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCorrectionRequestRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public StudentCorrectionRequestEntity save(UserSecurityContext context, StudentCorrectionRequestEntity request) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (request == null) throw new IllegalArgumentException("request cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (request.getId() == null) {
                return insertRequest(conn, context, request);
            } else {
                return updateRequest(conn, context, request);
            }
        });
    }

    private StudentCorrectionRequestEntity insertRequest(Connection conn, UserSecurityContext context, StudentCorrectionRequestEntity request) throws SQLException {
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setRowVersion(1);

        String reasonPayload = encodePayload(request);

        String sql = "INSERT INTO doc.lifecycle_requests (" +
                "id, tenant_id, entity_type, entity_id, request_type, reason, status, " +
                "requested_by, new_document_id, created_at, updated_at, created_by, updated_by, row_version" +
                ") VALUES (?, ?, 'STUDENT', ?, 'CORRECTION', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, request.getId());
            ps.setObject(2, request.getTenantId());
            ps.setObject(3, request.getStudentId());
            ps.setString(4, reasonPayload);
            ps.setString(5, request.getStatus() != null ? request.getStatus() : "PENDING");
            ps.setObject(6, request.getRequestedBy() != null ? request.getRequestedBy() : context.getUserId());
            ps.setObject(7, request.getEvidenceDocumentId());
            ps.setTimestamp(8, Timestamp.from(now.toInstant()));
            ps.setTimestamp(9, Timestamp.from(now.toInstant()));
            ps.setObject(10, context.getUserId());
            ps.setObject(11, context.getUserId());
            ps.setInt(12, request.getRowVersion());

            ps.executeUpdate();
            return request;
        }
    }

    private StudentCorrectionRequestEntity updateRequest(Connection conn, UserSecurityContext context, StudentCorrectionRequestEntity request) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setUpdatedAt(now);
        int newVersion = request.getRowVersion() + 1;

        String reasonPayload = encodePayload(request);

        String sql = "UPDATE doc.lifecycle_requests SET " +
                "reason = ?, status = ?, completed_at = ?, updated_at = ?, updated_by = ?, row_version = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reasonPayload);
            ps.setString(2, request.getStatus());
            ps.setTimestamp(3, request.getDecidedAt() != null ? Timestamp.from(request.getDecidedAt().toInstant()) : null);
            ps.setTimestamp(4, Timestamp.from(now.toInstant()));
            ps.setObject(5, context.getUserId());
            ps.setInt(6, newVersion);
            ps.setObject(7, request.getId());
            ps.setObject(8, context.getTenantId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                request.setRowVersion(newVersion);
            }
            return request;
        }
    }

    @Override
    public Optional<StudentCorrectionRequestEntity> findById(UserSecurityContext context, UUID id) {
        if (context == null || id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, entity_id, reason, status, requested_by, new_document_id, " +
                    "completed_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM doc.lifecycle_requests WHERE id = ? AND tenant_id = ? AND entity_type = 'STUDENT' AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<StudentCorrectionRequestEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (context == null || studentId == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<StudentCorrectionRequestEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, entity_id, reason, status, requested_by, new_document_id, " +
                    "completed_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM doc.lifecycle_requests WHERE entity_id = ? AND tenant_id = ? AND entity_type = 'STUDENT' AND deleted_at IS NULL " +
                    "ORDER BY created_at DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, studentId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<StudentCorrectionRequestEntity> findPendingByTenant(UserSecurityContext context) {
        if (context == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<StudentCorrectionRequestEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, entity_id, reason, status, requested_by, new_document_id, " +
                    "completed_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM doc.lifecycle_requests WHERE tenant_id = ? AND entity_type = 'STUDENT' AND status = 'PENDING' AND deleted_at IS NULL " +
                    "ORDER BY created_at ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            }
            return list;
        });
    }

    private StudentCorrectionRequestEntity mapResultSet(ResultSet rs) throws SQLException {
        StudentCorrectionRequestEntity entity = new StudentCorrectionRequestEntity();
        entity.setId((UUID) rs.getObject("id"));
        entity.setTenantId((UUID) rs.getObject("tenant_id"));
        entity.setStudentId((UUID) rs.getObject("entity_id"));
        entity.setStatus(rs.getString("status"));
        entity.setRequestedBy((UUID) rs.getObject("requested_by"));
        entity.setEvidenceDocumentId((UUID) rs.getObject("new_document_id"));

        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) entity.setDecidedAt(completedAt.toInstant().atOffset(ZoneOffset.UTC));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) entity.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) entity.setUpdatedAt(updatedAt.toInstant().atOffset(ZoneOffset.UTC));

        entity.setRowVersion(rs.getInt("row_version"));

        decodePayload(rs.getString("reason"), entity);

        return entity;
    }

    private String encodePayload(StudentCorrectionRequestEntity req) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("targetField", req.getTargetField());
            map.put("oldValue", req.getOldValue());
            map.put("newValue", req.getNewValue());
            map.put("reason", req.getReason());
            map.put("personId", req.getPersonId() != null ? req.getPersonId().toString() : null);
            map.put("decidedBy", req.getDecidedBy() != null ? req.getDecidedBy().toString() : null);
            map.put("decisionReason", req.getDecisionReason());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return req.getReason() != null ? req.getReason() : "";
        }
    }

    private void decodePayload(String payload, StudentCorrectionRequestEntity entity) {
        if (payload == null || payload.trim().isEmpty()) return;
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("targetField")) entity.setTargetField(node.get("targetField").asText());
            if (node.has("oldValue")) entity.setOldValue(node.get("oldValue").asText(null));
            if (node.has("newValue")) entity.setNewValue(node.get("newValue").asText(null));
            if (node.has("reason")) entity.setReason(node.get("reason").asText());
            if (node.has("decisionReason")) entity.setDecisionReason(node.get("decisionReason").asText(null));
            if (node.has("personId") && !node.get("personId").isNull()) {
                entity.setPersonId(UUID.fromString(node.get("personId").asText()));
            }
            if (node.has("decidedBy") && !node.get("decidedBy").isNull()) {
                entity.setDecidedBy(UUID.fromString(node.get("decidedBy").asText()));
            }
        } catch (Exception e) {
            entity.setReason(payload);
        }
    }
}
