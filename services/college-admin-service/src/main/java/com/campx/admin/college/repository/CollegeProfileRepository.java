package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.CollegeProfile;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing College Profile and Accreditations persistence
 * in {@code core.colleges} and {@code core.college_accreditations}.
 */
public interface CollegeProfileRepository {

    /**
     * Retrieves the operational profile for a college by code.
     *
     * @param context     caller security context
     * @param collegeCode unique alphanumeric college code
     * @return optional containing the college profile if found
     */
    Optional<CollegeProfile> getProfile(UserSecurityContext context, String collegeCode);

    /**
     * Persists or updates the operational profile for a college.
     *
     * @param context caller security context
     * @param profile profile entity to save
     * @return persisted college profile
     */
    CollegeProfile saveOrUpdateProfile(UserSecurityContext context, CollegeProfile profile);

    /**
     * Lists accreditation reference codes for a college.
     *
     * @param context   caller security context
     * @param collegeId college primary key
     * @return list of accreditation references
     */
    List<String> listAccreditationRefs(UserSecurityContext context, String collegeId);

    /**
     * Synchronizes the accreditation references for a college.
     *
     * @param context          caller security context
     * @param collegeId        college primary key
     * @param accreditationRefs list of accreditation references
     */
    void syncAccreditations(UserSecurityContext context, String collegeId, List<String> accreditationRefs);
}
