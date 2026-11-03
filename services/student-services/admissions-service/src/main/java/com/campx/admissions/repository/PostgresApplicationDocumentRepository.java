package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.ApplicationDocumentEntity;
import com.campx.admissions.model.DocumentVerificationStatus;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.application_documents} table.
 */
public class PostgresApplicationDocumentRepository implements ApplicationDocumentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresApplicationDocumentRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresApplicationDocumentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresApplicationDocumentRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ApplicationDocumentEntity save(ApplicationDocumentEntity document) {
        if (document == null) throw new IllegalArgumentException("Document cannot be null");
        if (document.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.application_documents (" +
                "tenant_id, id, application_id, document_id, document_type, storage_path, " +
                "file_name, mime_type, file_size_bytes, verification_status, verified_by, " +
                "verified_at, rejection_reason, is_fraudulent, fraud_notes, created_at, updated_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "verification_status = EXCLUDED.verification_status, " +
                "verified_by = EXCLUDED.verified_by, " +
                "verified_at = EXCLUDED.verified_at, " +
                "rejection_reason = EXCLUDED.rejection_reason, " +
                "is_fraudulent = EXCLUDED.is_fraudulent, " +
                "fraud_notes = EXCLUDED.fraud_notes, " +
                "updated_at = clock_timestamp(), " +
                "row_version = stu.application_documents.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, document.getTenantId());
            ps.setObject(idx++, document.getId());
            ps.setObject(idx++, document.getApplicationId());
            ps.setObject(idx++, document.getDocumentId());
            ps.setString(idx++, document.getDocumentType());
            ps.setString(idx++, document.getStoragePath());
            ps.setString(idx++, document.getFileName());
            ps.setString(idx++, document.getMimeType());
            ps.setLong(idx++, document.getFileSizeBytes());
            ps.setString(idx++, document.getVerificationStatus() != null ? document.getVerificationStatus().name() : DocumentVerificationStatus.PENDING.name());
            ps.setObject(idx++, document.getVerifiedBy());
            ps.setObject(idx++, document.getVerifiedAt());
            ps.setString(idx++, document.getRejectionReason());
            ps.setBoolean(idx++, document.isFraudulent());
            ps.setString(idx++, document.getFraudNotes());
            ps.setObject(idx++, document.getCreatedAt() != null ? document.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setInt(idx++, document.getRowVersion() > 0 ? document.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    document.setRowVersion(rs.getInt("row_version"));
                    document.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return document;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Document already registered for application");
            }
            throw new RuntimeException("PostgreSQL save failed for application document: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ApplicationDocumentEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.application_documents WHERE tenant_id = ? AND id = ?";
        List<ApplicationDocumentEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<ApplicationDocumentEntity> findByDocumentId(UUID tenantId, UUID documentId) {
        String sql = "SELECT * FROM stu.application_documents WHERE tenant_id = ? AND document_id = ?";
        List<ApplicationDocumentEntity> list = queryList(sql, tenantId, documentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<ApplicationDocumentEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.application_documents WHERE tenant_id = ? AND application_id = ? ORDER BY created_at ASC";
        return queryList(sql, tenantId, applicationId);
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        String sql = "DELETE FROM stu.application_documents WHERE tenant_id = ? AND id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL delete failed for document: " + e.getMessage(), e);
        }
    }

    private List<ApplicationDocumentEntity> queryList(String sql, Object... params) {
        List<ApplicationDocumentEntity> results = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL query failed: " + e.getMessage(), e);
        }
        return results;
    }

    private ApplicationDocumentEntity mapRow(ResultSet rs) throws SQLException {
        ApplicationDocumentEntity d = new ApplicationDocumentEntity();
        d.setTenantId(rs.getObject("tenant_id", UUID.class));
        d.setId(rs.getObject("id", UUID.class));
        d.setApplicationId(rs.getObject("application_id", UUID.class));
        d.setDocumentId(rs.getObject("document_id", UUID.class));
        d.setDocumentType(rs.getString("document_type"));
        d.setStoragePath(rs.getString("storage_path"));
        d.setFileName(rs.getString("file_name"));
        d.setMimeType(rs.getString("mime_type"));
        d.setFileSizeBytes(rs.getLong("file_size_bytes"));

        String vs = rs.getString("verification_status");
        if (vs != null) d.setVerificationStatus(DocumentVerificationStatus.valueOf(vs));

        d.setVerifiedBy(rs.getObject("verified_by", UUID.class));
        d.setVerifiedAt(rs.getObject("verified_at", OffsetDateTime.class));
        d.setRejectionReason(rs.getString("rejection_reason"));
        d.setFraudulent(rs.getBoolean("is_fraudulent"));
        d.setFraudNotes(rs.getString("fraud_notes"));
        d.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        d.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        d.setRowVersion(rs.getInt("row_version"));
        return d;
    }
}

