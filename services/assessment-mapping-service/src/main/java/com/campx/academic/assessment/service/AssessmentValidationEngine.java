package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.AssessmentValidationException;
import com.campx.academic.assessment.model.AssessmentModels.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation engine for assessment structures, components, and policy rules (US-004, US-008, US-010, US-011, US-012, US-018).
 */
public class AssessmentValidationEngine {

    public void validateCreateAssessment(CreateAssessmentRequest req, String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new AssessmentValidationException("Missing tenantId in request context.");
        }
        if (req == null) {
            throw new AssessmentValidationException("Assessment request body cannot be null.");
        }
        if (req.assessmentCode == null || req.assessmentCode.trim().isEmpty()) {
            throw new AssessmentValidationException("Assessment code is required.");
        }
        if (req.assessmentName == null || req.assessmentName.trim().isEmpty()) {
            throw new AssessmentValidationException("Assessment name is required.");
        }
        if (req.subjectId == null || req.subjectId.trim().isEmpty()) {
            throw new AssessmentValidationException("Subject ID is required.");
        }
        if (req.courseId == null || req.courseId.trim().isEmpty()) {
            throw new AssessmentValidationException("Course ID is required.");
        }
        if (req.academicYear == null || req.academicYear.trim().isEmpty()) {
            throw new AssessmentValidationException("Academic year is required.");
        }
        if (req.termId == null || req.termId.trim().isEmpty()) {
            throw new AssessmentValidationException("Term ID is required.");
        }
        if (req.assessmentType == null || req.assessmentType.trim().isEmpty()) {
            throw new AssessmentValidationException("Assessment type is required.");
        }
        try {
            AssessmentType.valueOf(req.assessmentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new AssessmentValidationException("Invalid assessment type: " + req.assessmentType + ". Supported: INTERNAL, EXTERNAL, PRACTICAL, CONTINUOUS, PROJECT, VIVA, LAB.");
        }
        if (req.totalMarks == null || req.totalMarks <= 0) {
            throw new AssessmentValidationException("Total marks must be greater than zero.");
        }
        if (req.totalWeightage != null && req.totalWeightage <= 0) {
            throw new AssessmentValidationException("Total weightage must be greater than zero.");
        }
    }

    public void validateComponent(CreateComponentRequest req, List<AssessmentComponent> existingComponents) {
        if (req == null) {
            throw new AssessmentValidationException("Component request body cannot be null.");
        }
        if (req.componentCode == null || req.componentCode.trim().isEmpty()) {
            throw new AssessmentValidationException("Component code is required.");
        }
        if (req.componentName == null || req.componentName.trim().isEmpty()) {
            throw new AssessmentValidationException("Component name is required.");
        }
        if (req.componentType == null || req.componentType.trim().isEmpty()) {
            throw new AssessmentValidationException("Component type is required.");
        }
        try {
            ComponentType.valueOf(req.componentType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new AssessmentValidationException("Invalid component type: " + req.componentType);
        }
        if (req.sequenceNo == null || req.sequenceNo <= 0) {
            throw new AssessmentValidationException("Component sequence number must be a positive integer.");
        }
        if (req.maxMarks == null || req.maxMarks <= 0) {
            throw new AssessmentValidationException("Component maximum marks must be greater than zero.");
        }
        if (req.passingMarks != null) {
            if (req.passingMarks < 0) {
                throw new AssessmentValidationException("Component passing marks cannot be negative.");
            }
            if (req.passingMarks > req.maxMarks) {
                throw new AssessmentValidationException("Component passing marks (" + req.passingMarks + ") cannot exceed maximum marks (" + req.maxMarks + ").");
            }
        }
        if (req.weightage == null || req.weightage <= 0) {
            throw new AssessmentValidationException("Component weightage must be greater than zero.");
        }
        if (req.evaluationMethod != null && !req.evaluationMethod.trim().isEmpty()) {
            try {
                EvaluationMethod.valueOf(req.evaluationMethod.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                throw new AssessmentValidationException("Invalid evaluation method: " + req.evaluationMethod);
            }
        }
        if (req.attemptPolicy != null && !req.attemptPolicy.trim().isEmpty()) {
            try {
                AttemptPolicy.valueOf(req.attemptPolicy.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                throw new AssessmentValidationException("Invalid attempt policy: " + req.attemptPolicy);
            }
        }

        // Check uniqueness of code and sequenceNo in existing components
        for (AssessmentComponent c : existingComponents) {
            if (c.getComponentCode().equalsIgnoreCase(req.componentCode.trim())) {
                throw new AssessmentValidationException("Component code '" + req.componentCode + "' already exists in this assessment.");
            }
            if (c.getSequenceNo() == req.sequenceNo) {
                throw new AssessmentValidationException("Component sequence number '" + req.sequenceNo + "' is already assigned to component '" + c.getComponentCode() + "'.");
            }
        }
    }

    public void validateUpdateComponent(UpdateComponentRequest req, AssessmentComponent existing, List<AssessmentComponent> allComponents) {
        if (req == null) {
            throw new AssessmentValidationException("Component update request body cannot be null.");
        }
        double maxMarks = req.maxMarks != null ? req.maxMarks : existing.getMaxMarks();
        double passingMarks = req.passingMarks != null ? req.passingMarks : existing.getPassingMarks();
        if (maxMarks <= 0) {
            throw new AssessmentValidationException("Maximum marks must be greater than zero.");
        }
        if (passingMarks < 0) {
            throw new AssessmentValidationException("Passing marks cannot be negative.");
        }
        if (passingMarks > maxMarks) {
            throw new AssessmentValidationException("Passing marks (" + passingMarks + ") cannot exceed maximum marks (" + maxMarks + ").");
        }
        if (req.weightage != null && req.weightage <= 0) {
            throw new AssessmentValidationException("Weightage must be greater than zero.");
        }
        if (req.sequenceNo != null && req.sequenceNo <= 0) {
            throw new AssessmentValidationException("Sequence number must be positive.");
        }
        if (req.sequenceNo != null && req.sequenceNo != existing.getSequenceNo()) {
            for (AssessmentComponent c : allComponents) {
                if (!c.getId().equals(existing.getId()) && c.getSequenceNo() == req.sequenceNo) {
                    throw new AssessmentValidationException("Component sequence number '" + req.sequenceNo + "' is already assigned to component '" + c.getComponentCode() + "'.");
                }
            }
        }
    }
}
