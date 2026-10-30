package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.model.StudentDocumentEntity;
import com.campx.student.security.UserSecurityContext;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code doc.documents}.
 * Persists document metadata (mime, storage path, verification state, size) for student records (STM01-US-020, STM01-US-021).
 */
public class PostgresStudentDocumentRepository implements StudentDocumentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentDocumentRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresStudentDocumentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresStudentDocumentRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public StudentDocumentEntity save(UserSecurityContext context, StudentDocumentEntity document) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (document == null) throw new IllegalArgumentException("document cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (document.getId() == null) {
                return insertDocument(conn, context, document);
            } else {
                return updateDocument(conn, context, document);
            }
        });
    }

    private StudentDocumentEntity insertDocument(Connection conn, UserSecurityContext context, StudentDocumentEntity document) throws SQLException {
        UUID id = UUID.randomUUID();
        document.setId(id);
        document.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setRowVersion(1);

        String sql = "INSERT INTO doc.documents (" +
                "id, tenant_id, title, classification, status, owner_id, storage_bucket, storage_path, " +
                "mime_type, size_bytes, current_version_no, expires_at, created_at, updated_at, " +
                "created_by, updated_by, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, document.getId());
            ps.setObject(2, document.getTenantId());
            ps.setString(3, document.getTitle() != null ? document.getTitle() : "Document");
            ps.setString(4, document.getClassification() != null ? document.getClassification() : "CONFIDENTIAL");
            ps.setString(5, document.getStatus() != null ? document.getStatus() : "UPLOADED");
            ps.setObject(6, document.getStudentId());
            ps.setString(7, document.getStorageBucket());
            ps.setString(8, document.getStoragePath());
            ps.setString(9, document.getMimeType());
            ps.setLong(10, document.getSizeBytes());
            ps.setInt(11, 1);
            ps.setDate(12, document.getValidTo() != null ? Date.valueOf(document.getValidTo()) : null);
            ps.setTimestamp(13, Timestamp.from(now.toInstant()));
            ps.setTimestamp(14, Timestamp.from(now.toInstant()));
            ps.setObject(15, context.getUserId());
            ps.setObject(16, context.getUserId());
            ps.setInt(17, document.getRowVersion());

            ps.executeUpdate();
            return document;
        }
    }

    private StudentDocumentEntity updateDocument(Connection conn, UserSecurityContext context, StudentDocumentEntity document) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        document.setUpdatedAt(now);
        int newVersion = document.getRowVersion() + 1;

        String sql = "UPDATE doc.documents SET " +
                "title = ?, classification = ?, status = ?, storage_bucket = ?, storage_path = ?, " +
                "mime_type = ?, size_bytes = ?, expires_at = ?, updated_at = ?, updated_by = ?, row_version = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, document.getTitle());
            ps.setString(2, document.getClassification());
            ps.setString(3, document.getStatus());
            ps.setString(4, document.getStorageBucket());
            ps.setString(5, document.getStoragePath());
            ps.setString(6, document.getMimeType());
            ps.setLong(7, document.getSizeBytes());
            ps.setDate(8, document.getValidTo() != null ? Date.valueOf(document.getValidTo()) : null);
            ps.setTimestamp(9, Timestamp.from(now.toInstant()));
            ps.setObject(10, context.getUserId());
            ps.setInt(11, newVersion);
            ps.setObject(12, document.getId());
            ps.setObject(13, context.getTenantId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                document.setRowVersion(newVersion);
            }
            return document;
        }
    }

    @Override
    public Optional<StudentDocumentEntity> findById(UserSecurityContext context, UUID id) {
        if (context == null || id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, title, classification, status, owner_id, storage_bucket, " +
                    "storage_path, mime_type, size_bytes, expires_at, created_at, updated_at, created_by, " +
                    "updated_by, row_version, deleted_at " +
                    "FROM doc.documents WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

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
    public List<StudentDocumentEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (context == null || studentId == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<StudentDocumentEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, title, classification, status, owner_id, storage_bucket, " +
                    "storage_path, mime_type, size_bytes, expires_at, created_at, updated_at, created_by, " +
                    "updated_by, row_version, deleted_at " +
                    "FROM doc.documents WHERE owner_id = ? AND tenant_id = ? AND deleted_at IS NULL " +
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

    private StudentDocumentEntity mapResultSet(ResultSet rs) throws SQLException {
        StudentDocumentEntity entity = new StudentDocumentEntity();
        entity.setId((UUID) rs.getObject("id"));
        entity.setTenantId((UUID) rs.getObject("tenant_id"));
        entity.setStudentId((UUID) rs.getObject("owner_id"));
        entity.setTitle(rs.getString("title"));
        entity.setClassification(rs.getString("classification"));
        entity.setStatus(rs.getString("status"));
        entity.setVerified("VERIFIED".equalsIgnoreCase(entity.getStatus()));
        entity.setStorageBucket(rs.getString("storage_bucket"));
        entity.setStoragePath(rs.getString("storage_path"));
        entity.setMimeType(rs.getString("mime_type"));
        entity.setSizeBytes(rs.getLong("size_bytes"));

        Date exp = rs.getDate("expires_at");
        if (exp != null) entity.setValidTo(exp.toLocalDate());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) entity.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) entity.setUpdatedAt(updatedAt.toInstant().atOffset(ZoneOffset.UTC));

        entity.setCreatedBy((UUID) rs.getObject("created_by"));
        entity.setUpdatedBy((UUID) rs.getObject("updated_by"));
        entity.setRowVersion(rs.getInt("row_version"));

        return entity;
    }
}
