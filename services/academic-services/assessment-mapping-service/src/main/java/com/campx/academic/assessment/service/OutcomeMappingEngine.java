package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.OutcomeMappingException;
import com.campx.academic.assessment.model.AssessmentModels.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates outcome mappings between assessment components and CO/PO/LO outcomes (US-020, US-021, US-022, US-023, US-024, US-068).
 */
public class OutcomeMappingEngine {

    public void validateMapping(CreateOutcomeMappingRequest req, AssessmentStructure structure,
                                List<AssessmentComponent> components, List<OutcomeMapping> existingMappings) {
        if (req == null) {
            throw new OutcomeMappingException("Outcome mapping request cannot be null.");
        }
        if (req.outcomeType == null || req.outcomeType.trim().isEmpty()) {
            throw new OutcomeMappingException("Outcome type is required (CO, PO, LO, PSO).");
        }
        OutcomeType type;
        try {
            type = OutcomeType.valueOf(req.outcomeType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new OutcomeMappingException("Invalid outcome type: " + req.outcomeType);
        }

        if (req.outcomeCode == null || req.outcomeCode.trim().isEmpty()) {
            throw new OutcomeMappingException("Outcome code is required.");
        }

        if (req.mappingLevel != null && !req.mappingLevel.trim().isEmpty()) {
            try {
                MappingLevel.valueOf(req.mappingLevel.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                throw new OutcomeMappingException("Invalid mapping level: " + req.mappingLevel + ". Supported: LOW, MEDIUM, HIGH, DIRECT, INDIRECT.");
            }
        }

        if (req.weight != null) {
            if (req.weight <= 0.0 || req.weight > 100.0) {
                throw new OutcomeMappingException("Outcome mapping weight must be between 0.0 and 100.0.");
            }
        }

        // Verify component exists if specified
        String compId = req.componentId != null ? req.componentId.trim() : "";
        if (!compId.isEmpty()) {
            boolean found = false;
            for (AssessmentComponent c : components) {
                if (c.getId().equals(compId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new OutcomeMappingException("Referenced componentId '" + compId + "' does not exist in assessment " + structure.getId());
            }
        }

        // Check duplicate mapping: assessmentId + componentId + outcomeType + outcomeCode (US-024)
        for (OutcomeMapping m : existingMappings) {
            String existingCompId = m.getComponentId() != null ? m.getComponentId().trim() : "";
            if (existingCompId.equals(compId)
                    && m.getOutcomeType() == type
                    && m.getOutcomeCode().equalsIgnoreCase(req.outcomeCode.trim())) {
                throw new OutcomeMappingException("Outcome mapping for " + type + " '" + req.outcomeCode + "' already exists on "
                        + (compId.isEmpty() ? "assessment level" : "component " + compId) + ".");
            }
        }
    }

    public List<String> detectUnmappedComponents(List<AssessmentComponent> components, List<OutcomeMapping> mappings) {
        List<String> unmapped = new ArrayList<>();
        for (AssessmentComponent c : components) {
            boolean mapped = false;
            for (OutcomeMapping m : mappings) {
                if (c.getId().equals(m.getComponentId())) {
                    mapped = true;
                    break;
                }
            }
            if (!mapped) {
                unmapped.add(c.getComponentCode());
            }
        }
        return unmapped;
    }
}
