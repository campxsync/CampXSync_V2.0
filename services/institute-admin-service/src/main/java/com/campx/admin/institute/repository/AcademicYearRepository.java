package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.AcademicYear;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing academic years (Item 22: {@code core.academic_years}).
 * Enforces tenant boundary isolation and RLS security under {@code iam.has_permission('acd01.*')}.
 */
public interface AcademicYearRepository {

    /**
     * Registers a new academic year within caller's tenant.
     *
     * @param context caller security context
     * @param year    academic year entity
     * @return persisted academic year
     */
    AcademicYear createAcademicYear(UserSecurityContext context, AcademicYear year);

    /**
     * Retrieves an academic year by ID within caller's tenant.
     *
     * @param context caller security context
     * @param id      academic year UUID
     * @return optional containing academic year if found and active
     */
    Optional<AcademicYear> findById(UserSecurityContext context, String id);

    /**
     * Retrieves an academic year by code within caller's tenant.
     *
     * @param context caller security context
     * @param code    academic year code (e.g. 'AY-2026-2027')
     * @return optional containing academic year if found
     */
    Optional<AcademicYear> findByCode(UserSecurityContext context, String code);

    /**
     * Finds the currently active academic year for caller's tenant.
     *
     * @param context caller security context
     * @return optional containing current academic year
     */
    Optional<AcademicYear> findCurrent(UserSecurityContext context);

    /**
     * Lists active academic years for caller's tenant.
     *
     * @param context caller security context
     * @return list of academic years
     */
    List<AcademicYear> listAcademicYears(UserSecurityContext context);

    /**
     * Updates an existing academic year.
     *
     * @param context caller security context
     * @param year    academic year entity containing updates
     * @return updated academic year
     */
    AcademicYear updateAcademicYear(UserSecurityContext context, AcademicYear year);

    /**
     * Sets the specified academic year as the current academic year, unsetting any previous.
     *
     * @param context caller security context
     * @param id      academic year UUID
     */
    void setCurrent(UserSecurityContext context, String id);

    /**
     * Soft-deletes an academic year.
     *
     * @param context caller security context
     * @param id      academic year UUID
     */
    void deleteAcademicYear(UserSecurityContext context, String id);
}
