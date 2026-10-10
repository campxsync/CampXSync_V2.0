package com.campx.academic.course.repository;

import com.campx.academic.course.exception.CourseCodeConflictException;
import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback implementation of {@link CourseRepository}.
 */
public class InMemoryCourseRepository implements CourseRepository {

    private final Map<String, Course> store = new ConcurrentHashMap<>();

    @Override
    public Course createCourse(UserSecurityContext context, Course course) {
        if (course.getCourseCode() == null || course.getCourseCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Course code is required");
        }
        String code = course.getCourseCode().trim().toUpperCase(Locale.ROOT);
        for (Course existing : store.values()) {
            if (existing.getCourseCode().equalsIgnoreCase(code)) {
                if (context == null || context.getTenantId() == null
                        || Objects.equals(existing.getTenantId(), context.getTenantId().toString())) {
                    throw new CourseCodeConflictException(code);
                }
            }
        }
        if (course.getId() == null || course.getId().trim().isEmpty()) {
            course.setId(UUID.randomUUID().toString());
        }
        if (course.getStatus() == null) {
            course.setStatus("DRAFT");
        }
        if (context != null && context.getTenantId() != null) {
            course.setTenantId(context.getTenantId().toString());
        }
        store.put(course.getId(), course);
        return course;
    }

    @Override
    public Optional<Course> findById(UserSecurityContext context, String id) {
        Course c = store.get(id);
        if (c != null && context != null && context.getTenantId() != null
                && c.getTenantId() != null && !context.getTenantId().toString().equals(c.getTenantId())) {
            return Optional.empty(); // tenant isolation
        }
        return Optional.ofNullable(c);
    }

    @Override
    public Optional<Course> findByCode(UserSecurityContext context, String code) {
        for (Course c : store.values()) {
            if (c.getCourseCode().equalsIgnoreCase(code)) {
                if (context != null && context.getTenantId() != null
                        && c.getTenantId() != null && !context.getTenantId().toString().equals(c.getTenantId())) {
                    continue; // tenant isolation
                }
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Course> listCourses(UserSecurityContext context, String departmentId) {
        List<Course> result = new ArrayList<>();
        for (Course c : store.values()) {
            if ("RETIRED".equals(c.getStatus())) continue;
            if (context != null && context.getTenantId() != null
                    && c.getTenantId() != null && !context.getTenantId().toString().equals(c.getTenantId())) {
                continue; // tenant isolation
            }
            if (departmentId != null && !departmentId.trim().isEmpty() && !departmentId.equals(c.getDepartmentId())) {
                continue;
            }
            result.add(c);
        }
        return result;
    }

    @Override
    public void updateCourse(UserSecurityContext context, Course course) {
        if (course != null && course.getId() != null) {
            store.put(course.getId(), course);
        }
    }

    @Override
    public void deleteCourse(UserSecurityContext context, String id) {
        store.remove(id);
    }
}
