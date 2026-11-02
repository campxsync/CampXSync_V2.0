package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.AdmissionHandoverEntity;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.admission_handovers} table.
 */
public class PostgresAdmissionHandoverRepository implements AdmissionHandoverRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAdmissionHandoverRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresAdmissionHandoverRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAdmissionHandoverRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public AdmissionHandoverEntity save(AdmissionHandoverEntity handover) {
        if (handover == null) throw new IllegalArgumentException("Handover cannot be null");
        if (handover.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (handover.getId() == null) {
            handover.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.admission_handovers (" +
                "tenant_id, id, application_id, offer_id, idempotency_key, created_student_id, " +
                "enrolment_status, retry_count, last_error, handover_completed_at, created_at, updated_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "created_student_id = EXCLUDED.created_student_id, " +
                "enrolment_status = EXCLUDED.enrolment_status, " +
                "retry_count = EXCLUDED.retry_count, " +
                "last_error = EXCLUDED.last_error, " +
                "handover_completed_at = EXCLUDED.handover_completed_at, " +
                "updated_at = clock_timestamp(), " +
                "row_version = stu.admission_handovers.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, handover.getTenantId());
            ps.setObject(idx++, handover.getId());
            ps.setObject(idx++, handover.getApplicationId());
            ps.setObject(idx++, handover.getOfferId());
            ps.setString(idx++, handover.getIdempotencyKey());
            ps.setObject(idx++, handover.getCreatedStudentId());
            ps.setString(idx++, handover.getEnrolmentStatus() != null ? handover.getEnrolmentStatus() : "PENDING");
            ps.setInt(idx++, handover.getRetryCount());
            ps.setString(idx++, handover.getLastError());
            ps.setObject(idx++, handover.getHandoverCompletedAt());
            ps.setObject(idx++, handover.getCreatedAt() != null ? handover.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setInt(idx++, handover.getRowVersion() > 0 ? handover.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    handover.setRowVersion(rs.getInt("row_version"));
                    handover.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return handover;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Handover record already exists for idempotency key: " + handover.getIdempotencyKey());
            }
            throw new RuntimeException("PostgreSQL save failed for admission handover: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AdmissionHandoverEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.admission_handovers WHERE tenant_id = ? AND id = ?";
        List<AdmissionHandoverEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionHandoverEntity> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        String sql = "SELECT * FROM stu.admission_handovers WHERE tenant_id = ? AND idempotency_key = ?";
        List<AdmissionHandoverEntity> list = queryList(sql, tenantId, idempotencyKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionHandoverEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.admission_handovers WHERE tenant_id = ? AND application_id = ? ORDER BY created_at DESC LIMIT 1";
        List<AdmissionHandoverEntity> list = queryList(sql, tenantId, applicationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<AdmissionHandoverEntity> findPendingOrFailed(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_handovers WHERE tenant_id = ? AND enrolment_status IN ('PENDING', 'FAILED') ORDER BY created_at ASC";
        return queryList(sql, tenantId);
    }

    @Override
    public List<AdmissionHandoverEntity> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_handovers WHERE tenant_id = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    private List<AdmissionHandoverEntity> queryList(String sql, Object... params) {
        List<AdmissionHandoverEntity> results = new ArrayList<>();
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

    private AdmissionHandoverEntity mapRow(ResultSet rs) throws SQLException {
        AdmissionHandoverEntity h = new AdmissionHandoverEntity();
        h.setTenantId(rs.getObject("tenant_id", UUID.class));
        h.setId(rs.getObject("id", UUID.class));
        h.setApplicationId(rs.getObject("application_id", UUID.class));
        h.setOfferId(rs.getObject("offer_id", UUID.class));
        h.setIdempotencyKey(rs.getString("idempotency_key"));
        h.setCreatedStudentId(rs.getObject("created_student_id", UUID.class));
        h.setEnrolmentStatus(rs.getString("enrolment_status"));
        h.setRetryCount(rs.getInt("retry_count"));
        h.setLastError(rs.getString("last_error"));
        h.setHandoverCompletedAt(rs.getObject("handover_completed_at", OffsetDateTime.class));
        h.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        h.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        h.setRowVersion(rs.getInt("row_version"));
        return h;
    }
}

