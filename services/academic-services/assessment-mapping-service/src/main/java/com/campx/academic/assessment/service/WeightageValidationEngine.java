package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.WeightageMismatchException;
import com.campx.academic.assessment.model.AssessmentModels.*;

import java.util.List;

/**
 * Reconciles component weightages and maximum marks against assessment totals (US-009, US-016, US-017, US-018, US-019).
 */
public class WeightageValidationEngine {

    private static final double EPSILON = 0.001;

    public AssessmentValidationResult reconcile(AssessmentStructure structure, List<AssessmentComponent> components, List<OutcomeMapping> mappings) {
        AssessmentValidationResult result = new AssessmentValidationResult();
        result.assessmentId = structure.getId();
        result.expectedWeightage = structure.getTotalWeightage();
        result.expectedTotalMarks = structure.getTotalMarks();

        double sumWeightage = 0.0;
        double sumMarks = 0.0;

        for (AssessmentComponent c : components) {
            sumWeightage += c.getWeightage();
            sumMarks += c.getMaxMarks();

            if (c.getPassingMarks() > c.getMaxMarks()) {
                result.errors.add("Component " + c.getComponentCode() + " passing marks (" + c.getPassingMarks() + ") exceed maximum marks (" + c.getMaxMarks() + ").");
            }
        }

        result.totalComponentWeightage = Math.round(sumWeightage * 100.0) / 100.0;
        result.totalComponentMarks = Math.round(sumMarks * 100.0) / 100.0;

        // Check components presence
        if (components.isEmpty()) {
            result.errors.add("Assessment structure has no components defined.");
        }

        // Weightage check
        if (Math.abs(sumWeightage - structure.getTotalWeightage()) > EPSILON) {
            result.errors.add("Total component weightage (" + result.totalComponentWeightage + "%) does not match required assessment total weightage (" + structure.getTotalWeightage() + "%).");
        }

        // Total marks check
        if (Math.abs(sumMarks - structure.getTotalMarks()) > EPSILON) {
            result.errors.add("Total component maximum marks (" + result.totalComponentMarks + ") does not match required assessment total marks (" + structure.getTotalMarks() + ").");
        }

        result.valid = result.errors.isEmpty();
        return result;
    }

    public void validateForPublish(AssessmentStructure structure, List<AssessmentComponent> components, List<OutcomeMapping> mappings) {
        AssessmentValidationResult result = reconcile(structure, components, mappings);
        if (!result.valid) {
            throw new WeightageMismatchException("Assessment publication blocked by validation failures: " + String.join("; ", result.errors));
        }
    }
}
