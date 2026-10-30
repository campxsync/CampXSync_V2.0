package com.campx.academic.assessment.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter and reference client for ACD-01 Course Management Service (US-005, US-041, US-058).
 */
public class CourseReferenceClient {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(CourseReferenceClient.class);
    private final Set<String> validCourses = ConcurrentHashMap.newKeySet();

    public CourseReferenceClient() {
        validCourses.add("COURSE-CS-BS");
        validCourses.add("COURSE-IT-BS");
        validCourses.add("COURSE-EE-BE");
    }

    public boolean validateCourse(String courseId) {
        if (courseId == null || courseId.trim().isEmpty()) {
            return false;
        }
        return validCourses.contains(courseId) || courseId.startsWith("COURSE-") || courseId.startsWith("CRS_");
    }

    public void registerCourse(String courseId) {
        validCourses.add(courseId);
        log.info("Course reference registered: " + courseId);
    }
}
