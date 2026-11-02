package com.campx.admissions.service;

import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.EligibilityRuleEntity;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/**
 * Deterministic versioned eligibility evaluation engine (US-002, US-010, US-043
 * / ADR D-04).
 */
public class EligibilityEvaluationEngine {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EligibilityEvaluationEngine.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class EvaluationResult {
        public final boolean eligible;
        public final String ruleVersion;
        public final String resultCode; // ELIGIBLE, INELIGIBLE_AGE, INELIGIBLE_GRADE, INELIGIBLE_PREREQUISITE
        public final String detailJson;

        public EvaluationResult(boolean eligible, String ruleVersion, String resultCode, String detailJson) {
            this.eligible = eligible;
            this.ruleVersion = ruleVersion;
            this.resultCode = resultCode;
            this.detailJson = detailJson;
        }
    }

    public EvaluationResult evaluate(ApplicationEntity application, EligibilityRuleEntity rule) {
        if (rule == null) {
            // If no explicit rule is configured, pass as eligible by default
            return new EvaluationResult(true, "DEFAULT", "ELIGIBLE", "{\"status\":\"NO_RULE_CONFIGURED\"}");
        }

        try {
            JsonNode applicantData = objectMapper.readTree(
                    application.getApplicantData() != null ? application.getApplicantData() : "{}");

            // 1. Evaluate Age Cutoff
            if (applicantData.has("dateOfBirth")) {
                String dobStr = applicantData.get("dateOfBirth").asText();
                try {
                    LocalDate dob = LocalDate.parse(dobStr);
                    int age = Period.between(dob, LocalDate.now()).getYears();
                    if (age < rule.getMinAge()) {
                        return new EvaluationResult(false, rule.getVersion(), "INELIGIBLE_AGE_TOO_YOUNG",
                                "{\"age\":" + age + ",\"minAge\":" + rule.getMinAge() + "}");
                    }
                    if (rule.getMaxAge() != null && age > rule.getMaxAge()) {
                        return new EvaluationResult(false, rule.getVersion(), "INELIGIBLE_AGE_TOO_OLD",
                                "{\"age\":" + age + ",\"maxAge\":" + rule.getMaxAge() + "}");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse dateOfBirth for application {}: {}", application.getId(), dobStr);
                }
            }

            // 2. Evaluate Qualification Grade
            if (applicantData.has("qualifyingPercentage")) {
                double pct = applicantData.get("qualifyingPercentage").asDouble();
                if (rule.getMinQualificationGrade() != null
                        && BigDecimal.valueOf(pct).compareTo(rule.getMinQualificationGrade()) < 0) {
                    return new EvaluationResult(false, rule.getVersion(), "INELIGIBLE_GRADE_BELOW_MINIMUM",
                            "{\"qualifyingPercentage\":" + pct + ",\"minRequired\":" + rule.getMinQualificationGrade()
                                    + "}");
                }
            }

            // 3. Evaluate Prerequisites if configured
            if (rule.getRequiredPrerequisites() != null && !rule.getRequiredPrerequisites().equals("[]")) {
                JsonNode prereqs = objectMapper.readTree(rule.getRequiredPrerequisites());
                if (prereqs.isArray() && prereqs.size() > 0) {
                    JsonNode passedSubjects = applicantData.get("completedSubjects");
                    if (passedSubjects == null || !passedSubjects.isArray()) {
                        return new EvaluationResult(false, rule.getVersion(), "INELIGIBLE_MISSING_PREREQUISITES",
                                "{\"missing\":\"ALL_PREREQUISITES\"}");
                    }
                }
            }

            return new EvaluationResult(true, rule.getVersion(), "ELIGIBLE",
                    "{\"evaluatedAt\":\"" + LocalDate.now() + "\",\"ruleVersion\":\"" + rule.getVersion() + "\"}");

        } catch (Exception e) {
            logger.error("Error evaluating eligibility for application {}: {}", application.getId(), e.getMessage());
            return new EvaluationResult(false, rule.getVersion(), "EVALUATION_ERROR",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
