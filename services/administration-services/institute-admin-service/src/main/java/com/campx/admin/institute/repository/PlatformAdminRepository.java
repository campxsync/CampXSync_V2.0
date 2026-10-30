package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.PlatformAdmin;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing platform administrator identities (Item 16: {@code plat.platform_admins}).
 * Enforces cryptographic trust boundaries and RLS validation under {@code iam.is_platform_admin()}.
 */
public interface PlatformAdminRepository {

    /**
     * Registers a new platform administrator under the caller's authorized context.
     *
     * @param context caller security context
     * @param admin   administrator model to persist
     * @return persisted platform administrator with generated UUID and metadata
     */
    PlatformAdmin createPlatformAdmin(UserSecurityContext context, PlatformAdmin admin);

    /**
     * Looks up an administrator by internal UUID.
     *
     * @param context caller security context
     * @param id      platform admin primary key
     * @return optional containing the administrator record if found and active
     */
    Optional<PlatformAdmin> findById(UserSecurityContext context, String id);

    /**
     * Looks up an administrator by Auth user identifier.
     *
     * @param context caller security context
     * @param userId  auth user UUID
     * @return optional containing the administrator record if found and active
     */
    Optional<PlatformAdmin> findByUserId(UserSecurityContext context, String userId);

    /**
     * Lists all active platform administrators.
     *
     * @param context caller security context
     * @return list of platform administrators
     */
    List<PlatformAdmin> listPlatformAdmins(UserSecurityContext context);

    /**
     * Updates editable platform administrator fields (roleCode, status, fullName).
     *
     * @param context caller security context
     * @param admin   administrator model containing updated values
     * @return updated administrator entity
     */
    PlatformAdmin updatePlatformAdmin(UserSecurityContext context, PlatformAdmin admin);

    /**
     * Soft-deletes a platform administrator.
     *
     * @param context caller security context
     * @param id      platform admin primary key
     */
    void deletePlatformAdmin(UserSecurityContext context, String id);
}
