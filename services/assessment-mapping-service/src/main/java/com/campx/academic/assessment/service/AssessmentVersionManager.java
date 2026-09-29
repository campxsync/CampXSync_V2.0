package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.AssessmentPublishForbiddenException;
import com.campx.academic.assessment.exception.AssessmentValidationException;
import com.campx.academic.assessment.exception.AssessmentVersionConflictException;
import com.campx.academic.assessment.model.AssessmentModels.*;

import java.util.Collection;

/**
 * Manages assessment structure versioning, optimistic locking, and effective version enforcement (US-031, US-032, US-054).
 */
public class AssessmentVersionManager {

    public void checkOptimisticLock(AssessmentStructure structure, Integer expectedVersion) {
        if (expectedVersion != null && structure.getCurrentVersion() != expectedVersion) {
            throw new AssessmentVersionConflictException(
                    "Assessment version conflict: expected version " + expectedVersion
                            + " but current version is " + structure.getCurrentVersion() + ".");
        }
    }

    public void checkDraftEditable(AssessmentStructure structure) {
        if (structure.getStatus() == AssessmentStatus.PUBLISHED || structure.getStatus() == AssessmentStatus.RETIRED) {
            throw new AssessmentValidationException(
                    "Assessment " + structure.getId() + " is in " + structure.getStatus()
                            + " status and cannot be modified directly. Published definitions are immutable.");
        }
    }

    public void enforceSingleEffectiveVersion(AssessmentStructure publishingStructure, Collection<AssessmentStructure> allStructures) {
        for (AssessmentStructure existing : allStructures) {
            if (!existing.getId().equals(publishingStructure.getId())
                    && existing.getTenantId().equals(publishingStructure.getTenantId())
                    && existing.getSubjectId().equals(publishingStructure.getSubjectId())
                    && existing.getAcademicYear().equals(publishingStructure.getAcademicYear())
                    && existing.getTermId().equals(publishingStructure.getTermId())
                    && existing.getStatus() == AssessmentStatus.PUBLISHED) {
                // Supersede previous published version to maintain single effective version
                existing.setStatus(AssessmentStatus.RETIRED);
                existing.setEffectiveVersion(0);
            }
        }
    }
}
