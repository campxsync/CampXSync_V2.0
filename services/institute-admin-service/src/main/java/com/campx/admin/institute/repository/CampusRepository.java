package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Campus;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing physical and academic campuses (Item 21: {@code core.campuses}).
 * Enforces tenant boundary isolation and college-level authorization under {@code iam.perm_all('adm02.*')}.
 */
public interface CampusRepository {

    /**
     * Registers a new campus for a college.
     *
     * @param context caller security context
     * @param campus  campus entity to persist
     * @return persisted campus entity
     */
    Campus createCampus(UserSecurityContext context, Campus campus);

    /**
     * Retrieves a campus by ID within caller's tenant.
     *
     * @param context caller security context
     * @param id      campus UUID
     * @return optional containing campus if present and active
     */
    Optional<Campus> findById(UserSecurityContext context, String id);

    /**
     * Retrieves a campus by college ID and unique code within caller's tenant.
     *
     * @param context   caller security context
     * @param collegeId college UUID
     * @param code      campus code
     * @return optional containing campus if found
     */
    Optional<Campus> findByCode(UserSecurityContext context, String collegeId, String code);

    /**
     * Lists active campuses belonging to a specific college.
     *
     * @param context   caller security context
     * @param collegeId college UUID (optional: if null, lists all in tenant)
     * @return list of campuses
     */
    List<Campus> listCampuses(UserSecurityContext context, String collegeId);

    /**
     * Updates an existing campus.
     *
     * @param context caller security context
     * @param campus  campus entity containing updates
     * @return updated campus record
     */
    Campus updateCampus(UserSecurityContext context, Campus campus);

    /**
     * Soft-deletes a campus.
     *
     * @param context caller security context
     * @param id      campus UUID
     */
    void deleteCampus(UserSecurityContext context, String id);
}
