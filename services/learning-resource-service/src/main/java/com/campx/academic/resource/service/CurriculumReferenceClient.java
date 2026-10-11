package com.campx.academic.resource.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client adapter for ACD-01 Course & ACD-02 Curriculum Management Service integrations (US-003, US-038).
 */
public class CurriculumReferenceClient {

    private final Map<String, Boolean> knownCurricula = new ConcurrentHashMap<>();
    private final Map<String, Boolean> knownCourses = new ConcurrentHashMap<>();

    public CurriculumReferenceClient() {
        knownCurricula.put("CUR_CSE_2026", true);
        knownCurricula.put("CUR_ECE_2026", true);

        knownCourses.put("CRS_CS101", true);
        knownCourses.put("CRS_CS201", true);
        knownCourses.put("CRS_ENG_001", true);
    }

    public boolean isValidCurriculum(String curriculumId) {
        if (curriculumId == null || curriculumId.trim().isEmpty()) {
            return false;
        }
        return knownCurricula.getOrDefault(curriculumId, true);
    }

    public boolean isValidCourse(String courseId) {
        if (courseId == null || courseId.trim().isEmpty()) {
            return false;
        }
        return knownCourses.getOrDefault(courseId, true);
    }

    public void registerCurriculum(String curriculumId) {
        if (curriculumId != null) {
            knownCurricula.put(curriculumId, true);
        }
    }

    public void registerCourse(String courseId) {
        if (courseId != null) {
            knownCourses.put(courseId, true);
        }
    }
}
