package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.AdmissionIntakeEntity;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.admission_intakes} table.
 */
public class PostgresAdmissionIntakeRepository implements AdmissionIntakeRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAdmissionIntakeRepository.class);
    private final DatabaseConnectionManager connectionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgresAdmissionIntakeRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAdmissionIntakeRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public AdmissionIntakeEntity save(AdmissionIntakeEntity intake) {
        if (intake == null) throw new IllegalArgumentException("Intake cannot be null");
        if (intake.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (intake.getId() == null) {
            intake.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.admission_intakes (" +
                "tenant_id, id, college_id, program_id, academic_year_id, intake_name, intake_code, " +
                "application_window_start, application_window_end, capacity, admitted_count, is_active, " +
                "fee_required, fee_amount, currency, fee_timing, scoring_rubric, created_at, updated_at, " +
                "created_by, updated_by, deleted_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "intake_name = EXCLUDED.intake_name, " +
                "application_window_start = EXCLUDED.application_window_start, " +
                "application_window_end = EXCLUDED.application_window_end, " +
                "capacity = EXCLUDED.capacity, " +
                "admitted_count = EXCLUDED.admitted_count, " +
                "is_active = EXCLUDED.is_active, " +
                "fee_required = EXCLUDED.fee_required, " +
                "fee_amount = EXCLUDED.fee_amount, " +
                "currency = EXCLUDED.currency, " +
                "fee_timing = EXCLUDED.fee_timing, " +
                "scoring_rubric = EXCLUDED.scoring_rubric, " +
                "updated_at = clock_timestamp(), " +
                "updated_by = EXCLUDED.updated_by, " +
                "deleted_at = EXCLUDED.deleted_at, " +
                "row_version = stu.admission_intakes.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, intake.getTenantId());
            ps.setObject(idx++, intake.getId());
            ps.setObject(idx++, intake.getCollegeId());
            ps.setObject(idx++, intake.getProgramId());
            ps.setObject(idx++, intake.getAcademicYearId());
            ps.setString(idx++, intake.getIntakeName());
            ps.setString(idx++, intake.getIntakeCode());
            ps.setObject(idx++, intake.getApplicationWindowStart());
            ps.setObject(idx++, intake.getApplicationWindowEnd());
            ps.setInt(idx++, intake.getCapacity());
            ps.setInt(idx++, intake.getAdmittedCount());
            ps.setBoolean(idx++, intake.isActive());
            ps.setBoolean(idx++, intake.isFeeRequired());
            ps.setBigDecimal(idx++, intake.getFeeAmount() != null ? intake.getFeeAmount() : BigDecimal.ZERO);
            ps.setString(idx++, intake.getCurrency() != null ? intake.getCurrency() : "INR");
            ps.setString(idx++, intake.getFeeTiming() != null ? intake.getFeeTiming().name() : "AT_SUBMISSION");
            ps.setString(idx++, intake.getScoringRubric() != null ? intake.getScoringRubric() : "{}");
            ps.setObject(idx++, intake.getCreatedAt() != null ? intake.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setObject(idx++, intake.getCreatedBy());
            ps.setObject(idx++, intake.getUpdatedBy());
            ps.setObject(idx++, intake.getDeletedAt());
            ps.setInt(idx++, intake.getRowVersion() > 0 ? intake.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    intake.setRowVersion(rs.getInt("row_version"));
                    intake.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return intake;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Duplicate intake code detected: " + intake.getIntakeCode());
            }
            throw new RuntimeException("PostgreSQL save failed for admission intake: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AdmissionIntakeEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.admission_intakes WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
        List<AdmissionIntakeEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionIntakeEntity> findByCode(UUID tenantId, UUID academicYearId, String intakeCode) {
        String sql = "SELECT * FROM stu.admission_intakes WHERE tenant_id = ? AND academic_year_id = ? AND intake_code = ? AND deleted_at IS NULL";
        List<AdmissionIntakeEntity> list = queryList(sql, tenantId, academicYearId, intakeCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<AdmissionIntakeEntity> findActiveIntakes(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_intakes WHERE tenant_id = ? AND is_active = true AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    @Override
    public List<AdmissionIntakeEntity> findByProgram(UUID tenantId, UUID programId) {
        String sql = "SELECT * FROM stu.admission_intakes WHERE tenant_id = ? AND program_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, programId);
    }

    @Override
    public List<AdmissionIntakeEntity> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_intakes WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    @Override
    public boolean existsByCode(UUID tenantId, UUID academicYearId, String intakeCode) {
        String sql = "SELECT COUNT(*) FROM stu.admission_intakes WHERE tenant_id = ? AND academic_year_id = ? AND intake_code = ? AND deleted_at IS NULL";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, academicYearId);
            ps.setString(3, intakeCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL query failed: " + e.getMessage(), e);
        }
    }

    private List<AdmissionIntakeEntity> queryList(String sql, Object... params) {
        List<AdmissionIntakeEntity> results = new ArrayList<>();
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

    private AdmissionIntakeEntity mapRow(ResultSet rs) throws SQLException {
        AdmissionIntakeEntity i = new AdmissionIntakeEntity();
        i.setTenantId(rs.getObject("tenant_id", UUID.class));
        i.setId(rs.getObject("id", UUID.class));
        i.setCollegeId(rs.getObject("college_id", UUID.class));
        i.setProgramId(rs.getObject("program_id", UUID.class));
        i.setAcademicYearId(rs.getObject("academic_year_id", UUID.class));
        i.setIntakeName(rs.getString("intake_name"));
        i.setIntakeCode(rs.getString("intake_code"));
        i.setApplicationWindowStart(rs.getObject("application_window_start", OffsetDateTime.class));
        i.setApplicationWindowEnd(rs.getObject("application_window_end", OffsetDateTime.class));
        i.setCapacity(rs.getInt("capacity"));
        i.setAdmittedCount(rs.getInt("admitted_count"));
        i.setActive(rs.getBoolean("is_active"));
        i.setFeeRequired(rs.getBoolean("fee_required"));
        i.setFeeAmount(rs.getBigDecimal("fee_amount"));
        i.setCurrency(rs.getString("currency"));
        String ft = rs.getString("fee_timing");
        if (ft != null) {
            try { i.setFeeTiming(com.campx.admissions.model.FeeTiming.valueOf(ft)); } catch (Exception ignored) {}
        }
        i.setScoringRubric(rs.getString("scoring_rubric"));

        i.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        i.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        i.setCreatedBy(rs.getObject("created_by", UUID.class));
        i.setUpdatedBy(rs.getObject("updated_by", UUID.class));
        i.setDeletedAt(rs.getObject("deleted_at", OffsetDateTime.class));
        i.setRowVersion(rs.getInt("row_version"));
        return i;
    }
}
