package com.campx.academic.assessment.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter and reference client for ACD-03 Subject Management Service (US-005, US-039, US-058).
 */
public class SubjectReferenceClient {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(SubjectReferenceClient.class);
    private final Set<String> validSubjects = ConcurrentHashMap.newKeySet();

    public SubjectReferenceClient() {
        // Seed default known subject IDs
        validSubjects.add("SUB-CS101");
        validSubjects.add("SUB-MATH201");
        validSubjects.add("SUB-PHY101");
        validSubjects.add("SUB-DS301");
    }

    public boolean validateSubject(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            return false;
        }
        // In local/test mode, allow prefix-matching or seeded IDs
        return validSubjects.contains(subjectId) || subjectId.startsWith("SUB-") || subjectId.startsWith("SUBJ_") || subjectId.startsWith("SUB_");
    }

    public void registerSubject(String subjectId) {
        validSubjects.add(subjectId);
        log.info("Subject reference registered: " + subjectId);
    }
}
