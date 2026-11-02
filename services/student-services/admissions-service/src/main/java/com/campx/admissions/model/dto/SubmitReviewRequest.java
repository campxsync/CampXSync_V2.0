package com.campx.admissions.model.dto;

import com.campx.admissions.model.ReviewRecommendation;

import java.math.BigDecimal;

public class SubmitReviewRequest {

    private String rubricVersion = "1.0.0";
    private String rubricScores = "{}";
    private BigDecimal totalScore = BigDecimal.ZERO;
    private ReviewRecommendation recommendation;
    private String confidentialComments;
    private String applicantFacingRemarks;

    public SubmitReviewRequest() {
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
}
