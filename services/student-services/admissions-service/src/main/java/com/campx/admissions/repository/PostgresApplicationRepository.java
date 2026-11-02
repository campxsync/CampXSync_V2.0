package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsException;
import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.model.DocumentVerificationStatus;
import com.campx.admissions.model.PaymentStatus;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.applications} table.
 * Executes parameterized queries bound to multi-tenant isolation rules.
 */
public class PostgresApplicationRepository implements ApplicationRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresApplicationRepository.class);
    private final DatabaseConnectionManager connectionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgresApplicationRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresApplicationRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ApplicationEntity save(ApplicationEntity application) {
        if (application == null) throw new IllegalArgumentException("Application cannot be null");
        if (application.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (application.getId() == null) {
            application.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.applications (" +
                "tenant_id, id, application_number, intake_id, applicant_person_id, " +
                "assigned_department_id, assigned_reviewer_id, status, document_verification_status, " +
                "payment_status, fee_obligation_id, payment_reference, payment_amount, " +
                "submission_timestamp, eligibility_rule_version, eligibility_result, " +
                "eligibility_evaluation_detail, applicant_data, review_comments_summary, " +
                "created_at, updated_at, deleted_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "assigned_department_id = EXCLUDED.assigned_department_id, " +
                "assigned_reviewer_id = EXCLUDED.assigned_reviewer_id, " +
                "status = EXCLUDED.status, " +
                "document_verification_status = EXCLUDED.document_verification_status, " +
                "payment_status = EXCLUDED.payment_status, " +
                "payment_reference = EXCLUDED.payment_reference, " +
                "payment_amount = EXCLUDED.payment_amount, " +
                "submission_timestamp = EXCLUDED.submission_timestamp, " +
                "eligibility_result = EXCLUDED.eligibility_result, " +
                "eligibility_evaluation_detail = EXCLUDED.eligibility_evaluation_detail, " +
                "applicant_data = EXCLUDED.applicant_data, " +
                "review_comments_summary = EXCLUDED.review_comments_summary, " +
                "updated_at = clock_timestamp(), " +
                "deleted_at = EXCLUDED.deleted_at, " +
                "row_version = stu.applications.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, application.getTenantId());
            ps.setObject(idx++, application.getId());
            ps.setString(idx++, application.getApplicationNumber());
            ps.setObject(idx++, application.getIntakeId());
            ps.setObject(idx++, application.getApplicantPersonId());
            ps.setObject(idx++, application.getAssignedDepartmentId());
            ps.setObject(idx++, application.getAssignedReviewerId());
            ps.setString(idx++, application.getStatus() != null ? application.getStatus().name() : ApplicationStatus.DRAFT.name());
            ps.setString(idx++, application.getDocumentVerificationStatus() != null ? application.getDocumentVerificationStatus().name() : DocumentVerificationStatus.PENDING.name());
            ps.setString(idx++, application.getPaymentStatus() != null ? application.getPaymentStatus().name() : PaymentStatus.PENDING.name());
            ps.setObject(idx++, application.getFeeObligationId());
            ps.setString(idx++, application.getPaymentReference());
            ps.setBigDecimal(idx++, application.getPaymentAmount() != null ? application.getPaymentAmount() : BigDecimal.ZERO);
            ps.setObject(idx++, application.getSubmissionTimestamp());
            ps.setString(idx++, application.getEligibilityRuleVersion());
            ps.setString(idx++, application.getEligibilityResult());
            ps.setString(idx++, application.getEligibilityEvaluationDetail() != null ? application.getEligibilityEvaluationDetail() : "{}");
            ps.setString(idx++, application.getApplicantData() != null ? application.getApplicantData() : "{}");
            ps.setString(idx++, application.getReviewCommentsSummary());
            ps.setObject(idx++, application.getCreatedAt() != null ? application.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setObject(idx++, application.getDeletedAt());
            ps.setInt(idx++, application.getRowVersion() > 0 ? application.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    application.setRowVersion(rs.getInt("row_version"));
                    application.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return application;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Duplicate application detected: " + application.getApplicationNumber());
            }
            throw new RuntimeException("PostgreSQL save failed for application: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ApplicationEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
        return querySingle(sql, tenantId, id);
    }

    @Override
    public Optional<ApplicationEntity> findByApplicationNumber(UUID tenantId, UUID intakeId, String appNumber) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND intake_id = ? AND application_number = ? AND deleted_at IS NULL";
        return querySingle(sql, tenantId, intakeId, appNumber);
    }

    @Override
    public Optional<ApplicationEntity> findActiveByApplicant(UUID tenantId, UUID intakeId, UUID applicantPersonId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND intake_id = ? AND applicant_person_id = ? " +
                "AND deleted_at IS NULL AND status NOT IN ('REJECTED', 'WITHDRAWN', 'DECLINED', 'EXPIRED')";
        return querySingle(sql, tenantId, intakeId, applicantPersonId);
    }

    @Override
    public List<ApplicationEntity> findByIntake(UUID tenantId, UUID intakeId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND intake_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, intakeId);
    }

    @Override
    public List<ApplicationEntity> findByApplicant(UUID tenantId, UUID applicantPersonId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND applicant_person_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, applicantPersonId);
    }

    @Override
    public List<ApplicationEntity> findByStatus(UUID tenantId, ApplicationStatus status) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND status = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, status != null ? status.name() : null);
    }

    @Override
    public List<ApplicationEntity> findByReviewer(UUID tenantId, UUID reviewerId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND assigned_reviewer_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, reviewerId);
    }

    @Override
    public List<ApplicationEntity> findByDepartment(UUID tenantId, UUID departmentId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND assigned_department_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId, departmentId);
    }

    @Override
    public List<ApplicationEntity> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM stu.applications WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    @Override
    public boolean existsByApplicationNumber(UUID tenantId, UUID intakeId, String appNum) {
        String sql = "SELECT COUNT(*) FROM stu.applications WHERE tenant_id = ? AND intake_id = ? AND application_number = ? AND deleted_at IS NULL";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, intakeId);
            ps.setString(3, appNum);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL query failed: " + e.getMessage(), e);
        }
    }

    private Optional<ApplicationEntity> querySingle(String sql, Object... params) {
        List<ApplicationEntity> list = queryList(sql, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<ApplicationEntity> queryList(String sql, Object... params) {
        List<ApplicationEntity> results = new ArrayList<>();
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

    private ApplicationEntity mapRow(ResultSet rs) throws SQLException {
        ApplicationEntity a = new ApplicationEntity();
        a.setTenantId(rs.getObject("tenant_id", UUID.class));
        a.setId(rs.getObject("id", UUID.class));
        a.setApplicationNumber(rs.getString("application_number"));
        a.setIntakeId(rs.getObject("intake_id", UUID.class));
        a.setApplicantPersonId(rs.getObject("applicant_person_id", UUID.class));
        a.setAssignedDepartmentId(rs.getObject("assigned_department_id", UUID.class));
        a.setAssignedReviewerId(rs.getObject("assigned_reviewer_id", UUID.class));

        String st = rs.getString("status");
        if (st != null) a.setStatus(ApplicationStatus.valueOf(st));

        String dvs = rs.getString("document_verification_status");
        if (dvs != null) a.setDocumentVerificationStatus(DocumentVerificationStatus.valueOf(dvs));

        String ps = rs.getString("payment_status");
        if (ps != null) a.setPaymentStatus(PaymentStatus.valueOf(ps));

        a.setFeeObligationId(rs.getObject("fee_obligation_id", UUID.class));
        a.setPaymentReference(rs.getString("payment_reference"));
        a.setPaymentAmount(rs.getBigDecimal("payment_amount"));
        a.setSubmissionTimestamp(rs.getObject("submission_timestamp", OffsetDateTime.class));
        a.setEligibilityRuleVersion(rs.getString("eligibility_rule_version"));
        a.setEligibilityResult(rs.getString("eligibility_result"));

        a.setEligibilityEvaluationDetail(rs.getString("eligibility_evaluation_detail"));
        a.setApplicantData(rs.getString("applicant_data"));

        a.setReviewCommentsSummary(rs.getString("review_comments_summary"));
        a.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        a.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        a.setDeletedAt(rs.getObject("deleted_at", OffsetDateTime.class));
        a.setRowVersion(rs.getInt("row_version"));
        return a;
    }
}
