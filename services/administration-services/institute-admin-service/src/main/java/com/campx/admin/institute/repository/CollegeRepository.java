package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.College;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Data access abstraction for {@link College} entities.
 * Decouples the domain layer from the underlying storage mechanism.
 */
public interface CollegeRepository {

    /**
     * Persists a new college entity under an existing institute tenant.
     *
     * @param context caller security context
     * @param college college model to create
     * @return persisted college
     */
    College createCollege(UserSecurityContext context, College college);

    /**
     * Retrieves a college by its primary key ID.
     *
     * @param context caller security context
     * @param id      college ID (UUID format)
     * @return Optional containing the college, or empty if not found or filtered by RLS
     */
    Optional<College> getCollegeById(UserSecurityContext context, String id);

    /**
     * Retrieves a college by its unique college code within an institute tenant.
     *
     * @param context     caller security context
     * @param instituteId parent institute ID (UUID format)
     * @param collegeCode college code
     * @return Optional containing the college, or empty if not found
     */
    Optional<College> getCollegeByCode(UserSecurityContext context, String instituteId, String collegeCode);

    /**
     * Lists colleges operating under a specific parent institute tenant.
     *
     * @param context     caller security context
     * @param instituteId parent institute ID (UUID format)
     * @return list of colleges
     */
    List<College> listColleges(UserSecurityContext context, String instituteId);

    /**
     * Updates an existing college record.
     *
     * @param context         caller security context
     * @param id              college ID (UUID format)
     * @param update          fields to update
     * @param expectedVersion expected row_version for optimistic concurrency control (nullable)
     * @return updated college
     */
    College updateCollege(UserSecurityContext context, String id, College update, Integer expectedVersion);
}
