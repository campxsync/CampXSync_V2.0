package com.campx.admissions;

import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.EligibilityRuleEntity;
import com.campx.admissions.service.EligibilityEvaluationEngine;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.*;

public class EligibilityEvaluationEngineTest {

    private EligibilityEvaluationEngine engine;

    @Before
    public void setUp() {
        engine = new EligibilityEvaluationEngine();
    }

    @Test
    public void testDefaultEligibleWhenNoRuleConfigured() {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(UUID.randomUUID());
        app.setApplicantData("{}");

        EligibilityEvaluationEngine.EvaluationResult result = engine.evaluate(app, null);
        assertTrue(result.eligible);
        assertEquals("DEFAULT", result.ruleVersion);
        assertEquals("ELIGIBLE", result.resultCode);
    }

    @Test
    public void testAgeCutoffValidation() {
        EligibilityRuleEntity rule = new EligibilityRuleEntity();
        rule.setVersion("1.0.0");
        rule.setMinAge(18);
        rule.setMaxAge(30);

        // Underage applicant (16 years old)
        ApplicationEntity youngApp = new ApplicationEntity();
        youngApp.setId(UUID.randomUUID());
        LocalDate dobYoung = LocalDate.now().minusYears(16);
        youngApp.setApplicantData("{\"dateOfBirth\":\"" + dobYoung.toString() + "\"}");

        EligibilityEvaluationEngine.EvaluationResult youngResult = engine.evaluate(youngApp, rule);
        assertFalse(youngResult.eligible);
        assertEquals("INELIGIBLE_AGE_TOO_YOUNG", youngResult.resultCode);

        // Overage applicant (35 years old)
        ApplicationEntity oldApp = new ApplicationEntity();
        oldApp.setId(UUID.randomUUID());
        LocalDate dobOld = LocalDate.now().minusYears(35);
        oldApp.setApplicantData("{\"dateOfBirth\":\"" + dobOld.toString() + "\"}");

        EligibilityEvaluationEngine.EvaluationResult oldResult = engine.evaluate(oldApp, rule);
        assertFalse(oldResult.eligible);
        assertEquals("INELIGIBLE_AGE_TOO_OLD", oldResult.resultCode);

        // Valid age (20 years old)
        ApplicationEntity validApp = new ApplicationEntity();
        validApp.setId(UUID.randomUUID());
        LocalDate dobValid = LocalDate.now().minusYears(20);
        validApp.setApplicantData("{\"dateOfBirth\":\"" + dobValid.toString() + "\"}");

        EligibilityEvaluationEngine.EvaluationResult validResult = engine.evaluate(validApp, rule);
        assertTrue(validResult.eligible);
        assertEquals("ELIGIBLE", validResult.resultCode);
    }

    @Test
    public void testQualificationGradeCutoffValidation() {
        EligibilityRuleEntity rule = new EligibilityRuleEntity();
        rule.setVersion("1.0.0");
        rule.setMinAge(16);
        rule.setMinQualificationGrade(new BigDecimal("75.00"));

        // Grade below cutoff (70%)
        ApplicationEntity lowGradeApp = new ApplicationEntity();
        lowGradeApp.setId(UUID.randomUUID());
        LocalDate dob = LocalDate.now().minusYears(19);
        lowGradeApp.setApplicantData("{\"dateOfBirth\":\"" + dob + "\",\"qualifyingPercentage\":70.0}");

        EligibilityEvaluationEngine.EvaluationResult lowResult = engine.evaluate(lowGradeApp, rule);
        assertFalse(lowResult.eligible);
        assertEquals("INELIGIBLE_GRADE_BELOW_MINIMUM", lowResult.resultCode);

        // Grade meeting cutoff (82%)
        ApplicationEntity passGradeApp = new ApplicationEntity();
        passGradeApp.setId(UUID.randomUUID());
        passGradeApp.setApplicantData("{\"dateOfBirth\":\"" + dob + "\",\"qualifyingPercentage\":82.5}");

        EligibilityEvaluationEngine.EvaluationResult passResult = engine.evaluate(passGradeApp, rule);
        assertTrue(passResult.eligible);
        assertEquals("ELIGIBLE", passResult.resultCode);
    }

    @Test
    public void testMissingPrerequisitesValidation() {
        EligibilityRuleEntity rule = new EligibilityRuleEntity();
        rule.setVersion("1.0.0");
        rule.setMinAge(16);
        rule.setRequiredPrerequisites("[\"PHYSICS\", \"MATHEMATICS\"]");

        ApplicationEntity appWithoutPrereqs = new ApplicationEntity();
        appWithoutPrereqs.setId(UUID.randomUUID());
        LocalDate dob = LocalDate.now().minusYears(19);
        appWithoutPrereqs.setApplicantData("{\"dateOfBirth\":\"" + dob + "\",\"qualifyingPercentage\":90.0}");

        EligibilityEvaluationEngine.EvaluationResult result = engine.evaluate(appWithoutPrereqs, rule);
        assertFalse(result.eligible);
        assertEquals("INELIGIBLE_MISSING_PREREQUISITES", result.resultCode);
    }
}

