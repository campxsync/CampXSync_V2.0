package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.AdmissionDecisionEntity;
import com.campx.admissions.model.DecisionType;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.admission_decisions} table.
 */
public class PostgresAdmissionDecisionRepository implements AdmissionDecisionRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAdmissionDecisionRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresAdmissionDecisionRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAdmissionDecisionRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public AdmissionDecisionEntity save(AdmissionDecisionEntity decision) {
        if (decision == null) throw new IllegalArgumentException("Decision cannot be null");
        if (decision.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (decision.getId() == null) {
            decision.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.admission_decisions (" +
                "tenant_id, id, application_id, decision_type, decision_reason_code, " +
                "decision_notes, decided_by, decided_at, created_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "decision_type = EXCLUDED.decision_type, " +
                "decision_reason_code = EXCLUDED.decision_reason_code, " +
                "decision_notes = EXCLUDED.decision_notes, " +
                "decided_by = EXCLUDED.decided_by, " +
                "decided_at = EXCLUDED.decided_at, " +
                "row_version = stu.admission_decisions.row_version + 1 " +
                "RETURNING row_version";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, decision.getTenantId());
            ps.setObject(idx++, decision.getId());
            ps.setObject(idx++, decision.getApplicationId());
            ps.setString(idx++, decision.getDecisionType() != null ? decision.getDecisionType().name() : null);
            ps.setString(idx++, decision.getDecisionReasonCode());
            ps.setString(idx++, decision.getDecisionNotes());
            ps.setObject(idx++, decision.getDecidedBy());
            ps.setObject(idx++, decision.getDecidedAt() != null ? decision.getDecidedAt() : OffsetDateTime.now());
            ps.setObject(idx++, decision.getCreatedAt() != null ? decision.getCreatedAt() : OffsetDateTime.now());
            ps.setInt(idx++, decision.getRowVersion() > 0 ? decision.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    decision.setRowVersion(rs.getInt("row_version"));
                }
            }
            return decision;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Admission decision already recorded for this application");
            }
            throw new RuntimeException("PostgreSQL save failed for admission decision: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AdmissionDecisionEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.admission_decisions WHERE tenant_id = ? AND id = ?";
        List<AdmissionDecisionEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionDecisionEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.admission_decisions WHERE tenant_id = ? AND application_id = ? ORDER BY created_at DESC LIMIT 1";
        List<AdmissionDecisionEntity> list = queryList(sql, tenantId, applicationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<AdmissionDecisionEntity> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_decisions WHERE tenant_id = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    private List<AdmissionDecisionEntity> queryList(String sql, Object... params) {
        List<AdmissionDecisionEntity> results = new ArrayList<>();
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

    private AdmissionDecisionEntity mapRow(ResultSet rs) throws SQLException {
        AdmissionDecisionEntity d = new AdmissionDecisionEntity();
        d.setTenantId(rs.getObject("tenant_id", UUID.class));
        d.setId(rs.getObject("id", UUID.class));
        d.setApplicationId(rs.getObject("application_id", UUID.class));

        String dt = rs.getString("decision_type");
        if (dt != null) d.setDecisionType(DecisionType.valueOf(dt));

        d.setDecisionReasonCode(rs.getString("decision_reason_code"));
        d.setDecisionNotes(rs.getString("decision_notes"));
        d.setDecidedBy(rs.getObject("decided_by", UUID.class));
        d.setDecidedAt(rs.getObject("decided_at", OffsetDateTime.class));
        d.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        d.setRowVersion(rs.getInt("row_version"));
        return d;
    }
}

