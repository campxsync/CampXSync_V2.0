package com.campx.academic.assessment.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter and reference client for ACD-02 Curriculum Management Service (US-005, US-040, US-056, US-058).
 */
public class CurriculumReferenceClient {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(CurriculumReferenceClient.class);
    private final Set<String> validCurricula = ConcurrentHashMap.newKeySet();

    public CurriculumReferenceClient() {
        validCurricula.add("CURR-2026-CS");
        validCurricula.add("CURR-2025-CS");
        validCurricula.add("CURR-2024-ENG");
    }

    public boolean validateCurriculum(String curriculumId) {
        if (curriculumId == null || curriculumId.trim().isEmpty()) {
            return false;
        }
        return validCurricula.contains(curriculumId) || curriculumId.startsWith("CURR-") || curriculumId.startsWith("CURRICULUM_");
    }

    public void registerCurriculum(String curriculumId) {
        validCurricula.add(curriculumId);
        log.info("Curriculum reference registered: " + curriculumId);
    }
}
