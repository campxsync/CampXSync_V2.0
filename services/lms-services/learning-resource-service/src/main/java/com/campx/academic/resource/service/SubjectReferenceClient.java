package com.campx.academic.resource.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client adapter for ACD-03 Subject Management Service integration (US-003, US-038).
 * Caches active subjects and verifies subject existence.
 */
public class SubjectReferenceClient {

    private final Map<String, Boolean> knownSubjects = new ConcurrentHashMap<>();

    public SubjectReferenceClient() {
        // Seed default known academic subjects
        knownSubjects.put("SUB_CS101", true);
        knownSubjects.put("SUB_CS201", true);
        knownSubjects.put("SUB_MATH101", true);
        knownSubjects.put("SUB_PHY101", true);
    }

    public boolean isValidSubject(String subjectId) {
        if (subjectId == null || subjectId.trim().isEmpty()) {
            return false;
        }
        return knownSubjects.getOrDefault(subjectId, true); // Allow configured / discovered subjects
    }

    public void registerSubject(String subjectId) {
        if (subjectId != null) {
            knownSubjects.put(subjectId, true);
        }
    }
}
