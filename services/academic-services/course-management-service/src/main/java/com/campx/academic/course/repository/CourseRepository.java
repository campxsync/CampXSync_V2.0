package com.campx.academic.course.repository;

import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@code acd.courses} persistence.
 */
public interface CourseRepository {

    /**
     * Persists a new course under an active department and tenant.
     *
     * @param context authenticated caller security context
     * @param course  course entity to persist
     * @return persisted course with generated ID and timestamps
     */
    Course createCourse(UserSecurityContext context, Course course);

    /**
     * Finds a course by primary identifier within the caller's tenant.
     *
     * @param context authenticated caller security context
     * @param id      course UUID
     * @return optional containing course if found and accessible
     */
    Optional<Course> findById(UserSecurityContext context, String id);

    /**
     * Finds a course by unique alphanumeric code within the caller's tenant.
     *
     * @param context authenticated caller security context
     * @param code    course code (e.g. "CS101")
     * @return optional containing course if found
     */
    Optional<Course> findByCode(UserSecurityContext context, String code);

    /**
     * Lists active courses for caller's tenant, optionally filtered by department.
     *
     * @param context      authenticated caller security context
     * @param departmentId optional department UUID filter
     * @return list of matching courses
     */
    List<Course> listCourses(UserSecurityContext context, String departmentId);

    /**
     * Updates an existing course.
     *
     * @param context authenticated caller security context
     * @param course  course entity with updated fields
     */
    void updateCourse(UserSecurityContext context, Course course);

    /**
     * Updates only the lifecycle status of an existing course.
     *
     * @param context  authenticated caller security context
     * @param courseId course UUID
     * @param status   new lifecycle status (e.g. "ACTIVE", "APPROVED")
     */
    void updateStatus(UserSecurityContext context, String courseId, String status);

    /**
     * Deletes a course (soft-deletes in PostgreSQL).
     *
     * @param context authenticated caller security context
     * @param id      course UUID
     */
    void deleteCourse(UserSecurityContext context, String id);
}
