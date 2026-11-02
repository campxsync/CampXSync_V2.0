package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.EligibilityRuleEntity;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.admission_eligibility_rules} table.
 */
public class PostgresEligibilityRuleRepository implements EligibilityRuleRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresEligibilityRuleRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresEligibilityRuleRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresEligibilityRuleRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public EligibilityRuleEntity save(EligibilityRuleEntity rule) {
        if (rule == null) throw new IllegalArgumentException("Rule cannot be null");
        if (rule.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (rule.getId() == null) {
            rule.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.admission_eligibility_rules (" +
                "tenant_id, id, intake_id, version, min_qualification_grade, min_age, max_age, " +
                "required_prerequisites, rule_definitions, is_active, effective_from, effective_to, " +
                "created_at, updated_at, created_by, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "min_qualification_grade = EXCLUDED.min_qualification_grade, " +
                "min_age = EXCLUDED.min_age, " +
                "max_age = EXCLUDED.max_age, " +
                "required_prerequisites = EXCLUDED.required_prerequisites, " +
                "rule_definitions = EXCLUDED.rule_definitions, " +
                "is_active = EXCLUDED.is_active, " +
                "effective_to = EXCLUDED.effective_to, " +
                "updated_at = clock_timestamp(), " +
                "row_version = stu.admission_eligibility_rules.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, rule.getTenantId());
            ps.setObject(idx++, rule.getId());
            ps.setObject(idx++, rule.getIntakeId());
            ps.setString(idx++, rule.getVersion() != null ? rule.getVersion() : "1.0.0");
            ps.setBigDecimal(idx++, rule.getMinQualificationGrade() != null ? rule.getMinQualificationGrade() : BigDecimal.ZERO);
            ps.setInt(idx++, rule.getMinAge());
            ps.setObject(idx++, rule.getMaxAge());
            ps.setString(idx++, rule.getRequiredPrerequisites() != null ? rule.getRequiredPrerequisites() : "[]");
            ps.setString(idx++, rule.getRuleDefinitions() != null ? rule.getRuleDefinitions() : "{}");
            ps.setBoolean(idx++, rule.isActive());
            ps.setObject(idx++, rule.getEffectiveFrom() != null ? rule.getEffectiveFrom() : OffsetDateTime.now());
            ps.setObject(idx++, rule.getEffectiveTo());
            ps.setObject(idx++, rule.getCreatedAt() != null ? rule.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setObject(idx++, rule.getCreatedBy());
            ps.setInt(idx++, rule.getRowVersion() > 0 ? rule.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rule.setRowVersion(rs.getInt("row_version"));
                    rule.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return rule;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Duplicate eligibility rule version detected for intake: " + rule.getVersion());
            }
            throw new RuntimeException("PostgreSQL save failed for eligibility rule: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<EligibilityRuleEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.admission_eligibility_rules WHERE tenant_id = ? AND id = ?";
        List<EligibilityRuleEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<EligibilityRuleEntity> findLatestActiveByIntake(UUID tenantId, UUID intakeId) {
        String sql = "SELECT * FROM stu.admission_eligibility_rules WHERE tenant_id = ? AND intake_id = ? " +
                "AND is_active = true ORDER BY effective_from DESC LIMIT 1";
        List<EligibilityRuleEntity> list = queryList(sql, tenantId, intakeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<EligibilityRuleEntity> findAllByIntake(UUID tenantId, UUID intakeId) {
        String sql = "SELECT * FROM stu.admission_eligibility_rules WHERE tenant_id = ? AND intake_id = ? ORDER BY effective_from DESC";
        return queryList(sql, tenantId, intakeId);
    }

    private List<EligibilityRuleEntity> queryList(String sql, Object... params) {
        List<EligibilityRuleEntity> results = new ArrayList<>();
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

    private EligibilityRuleEntity mapRow(ResultSet rs) throws SQLException {
        EligibilityRuleEntity r = new EligibilityRuleEntity();
        r.setTenantId(rs.getObject("tenant_id", UUID.class));
        r.setId(rs.getObject("id", UUID.class));
        r.setIntakeId(rs.getObject("intake_id", UUID.class));
        r.setVersion(rs.getString("version"));
        r.setMinQualificationGrade(rs.getBigDecimal("min_qualification_grade"));
        r.setMinAge(rs.getInt("min_age"));
        r.setMaxAge(rs.getObject("max_age", Integer.class));
        r.setRequiredPrerequisites(rs.getString("required_prerequisites"));
        r.setRuleDefinitions(rs.getString("rule_definitions"));
        r.setActive(rs.getBoolean("is_active"));
        r.setEffectiveFrom(rs.getObject("effective_from", OffsetDateTime.class));
        r.setEffectiveTo(rs.getObject("effective_to", OffsetDateTime.class));
        r.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        r.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        r.setCreatedBy(rs.getObject("created_by", UUID.class));
        r.setRowVersion(rs.getInt("row_version"));
        return r;
    }
}

