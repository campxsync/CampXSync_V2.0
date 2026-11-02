package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.ApplicationReviewEntity;
import com.campx.admissions.model.ReviewRecommendation;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.application_reviews} table.
 */
public class PostgresApplicationReviewRepository implements ApplicationReviewRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresApplicationReviewRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresApplicationReviewRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresApplicationReviewRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ApplicationReviewEntity save(ApplicationReviewEntity review) {
        if (review == null) throw new IllegalArgumentException("Review cannot be null");
        if (review.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (review.getId() == null) {
            review.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.application_reviews (" +
                "tenant_id, id, application_id, reviewer_id, rubric_version, rubric_scores, " +
                "total_score, recommendation, confidential_comments, applicant_facing_remarks, " +
                "submitted_at, created_at, updated_at, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "rubric_scores = EXCLUDED.rubric_scores, " +
                "total_score = EXCLUDED.total_score, " +
                "recommendation = EXCLUDED.recommendation, " +
                "confidential_comments = EXCLUDED.confidential_comments, " +
                "applicant_facing_remarks = EXCLUDED.applicant_facing_remarks, " +
                "submitted_at = EXCLUDED.submitted_at, " +
                "updated_at = clock_timestamp(), " +
                "row_version = stu.application_reviews.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, review.getTenantId());
            ps.setObject(idx++, review.getId());
            ps.setObject(idx++, review.getApplicationId());
            ps.setObject(idx++, review.getReviewerId());
            ps.setString(idx++, review.getRubricVersion() != null ? review.getRubricVersion() : "1.0.0");
            ps.setString(idx++, review.getRubricScores() != null ? review.getRubricScores() : "{}");
            ps.setBigDecimal(idx++, review.getTotalScore() != null ? review.getTotalScore() : BigDecimal.ZERO);
            ps.setString(idx++, review.getRecommendation() != null ? review.getRecommendation().name() : null);
            ps.setString(idx++, review.getConfidentialComments());
            ps.setString(idx++, review.getApplicantFacingRemarks());
            ps.setObject(idx++, review.getSubmittedAt());
            ps.setObject(idx++, review.getCreatedAt() != null ? review.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setInt(idx++, review.getRowVersion() > 0 ? review.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    review.setRowVersion(rs.getInt("row_version"));
                    review.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return review;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Review already submitted by this reviewer for this application");
            }
            throw new RuntimeException("PostgreSQL save failed for application review: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ApplicationReviewEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.application_reviews WHERE tenant_id = ? AND id = ?";
        List<ApplicationReviewEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<ApplicationReviewEntity> findByApplicationAndReviewer(UUID tenantId, UUID applicationId, UUID reviewerId) {
        String sql = "SELECT * FROM stu.application_reviews WHERE tenant_id = ? AND application_id = ? AND reviewer_id = ?";
        List<ApplicationReviewEntity> list = queryList(sql, tenantId, applicationId, reviewerId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<ApplicationReviewEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.application_reviews WHERE tenant_id = ? AND application_id = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId, applicationId);
    }

    @Override
    public List<ApplicationReviewEntity> findByReviewer(UUID tenantId, UUID reviewerId) {
        String sql = "SELECT * FROM stu.application_reviews WHERE tenant_id = ? AND reviewer_id = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId, reviewerId);
    }

    private List<ApplicationReviewEntity> queryList(String sql, Object... params) {
        List<ApplicationReviewEntity> results = new ArrayList<>();
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

    private ApplicationReviewEntity mapRow(ResultSet rs) throws SQLException {
        ApplicationReviewEntity r = new ApplicationReviewEntity();
        r.setTenantId(rs.getObject("tenant_id", UUID.class));
        r.setId(rs.getObject("id", UUID.class));
        r.setApplicationId(rs.getObject("application_id", UUID.class));
        r.setReviewerId(rs.getObject("reviewer_id", UUID.class));
        r.setRubricVersion(rs.getString("rubric_version"));
        r.setRubricScores(rs.getString("rubric_scores"));
        r.setTotalScore(rs.getBigDecimal("total_score"));

        String rec = rs.getString("recommendation");
        if (rec != null) r.setRecommendation(ReviewRecommendation.valueOf(rec));

        r.setConfidentialComments(rs.getString("confidential_comments"));
        r.setApplicantFacingRemarks(rs.getString("applicant_facing_remarks"));
        r.setSubmittedAt(rs.getObject("submitted_at", OffsetDateTime.class));
        r.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        r.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        r.setRowVersion(rs.getInt("row_version"));
        return r;
    }
}

