package com.campx.admissions.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ApplicationReviewEntity {

    private UUID tenantId;
    private UUID id;
    private UUID applicationId;
    private UUID reviewerId;
    private String rubricVersion = "1.0.0";
    private String rubricScores = "{}";
    private BigDecimal totalScore = BigDecimal.ZERO;
    private ReviewRecommendation recommendation;
    private String confidentialComments;
    private String applicantFacingRemarks;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private int rowVersion = 1;

    public ApplicationReviewEntity() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public void setRubricVersion(String rubricVersion) {
        this.rubricVersion = rubricVersion;
    }

    public String getRubricScores() {
        return rubricScores;
    }

    public void setRubricScores(String rubricScores) {
        this.rubricScores = rubricScores;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public ReviewRecommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(ReviewRecommendation recommendation) {
        this.recommendation = recommendation;
    }

    public String getConfidentialComments() {
        return confidentialComments;
    }

    public void setConfidentialComments(String confidentialComments) {
        this.confidentialComments = confidentialComments;
    }

    public String getApplicantFacingRemarks() {
        return applicantFacingRemarks;
    }

    public void setApplicantFacingRemarks(String applicantFacingRemarks) {
        this.applicantFacingRemarks = applicantFacingRemarks;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }
}
