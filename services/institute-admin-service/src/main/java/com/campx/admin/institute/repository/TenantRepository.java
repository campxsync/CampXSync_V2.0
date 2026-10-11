package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Institute;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface abstraction for tenant persistence in {@code core.tenants}.
 * Follows the established {@link UserProfileRepository} pattern with strict {@link UserSecurityContext}
 * enforcement, transactional RLS isolation, and optimistic locking.
 */
public interface TenantRepository {

    /**
     * Creates a new tenant / institute record in {@code core.tenants} under the caller's platform security context.
     *
     * @param context   caller's security context (must be authenticated platform administrator)
     * @param institute institute details to persist
     * @return persisted {@link Institute} entity with server-generated ID, timestamps, and row_version
     */
    Institute createInstitute(UserSecurityContext context, Institute institute);

    /**
     * Updates an existing institute record with optimistic locking.
     *
     * @param context         caller's security context
     * @param id              institute / tenant ID
     * @param update          fields to update
     * @param expectedVersion expected {@code row_version} for optimistic concurrency, or null if unconstrained
     * @return updated {@link Institute} entity
     */
    Institute updateInstitute(UserSecurityContext context, String id, Institute update, Integer expectedVersion);

    /**
     * Retrieves an institute by its ID within the caller's tenant / platform scope.
     *
     * @param context caller's security context
     * @param id      institute ID
     * @return Optional containing the institute if found and visible under RLS, or empty
     */
    Optional<Institute> getInstituteById(UserSecurityContext context, String id);

    /**
     * Retrieves an institute by its unique institute code.
     *
     * @param context caller's security context
     * @param code    institute code
     * @return Optional containing the institute if found and visible under RLS, or empty
     */
    Optional<Institute> getInstituteByCode(UserSecurityContext context, String code);

    /**
     * Lists all institutes visible to the caller under RLS.
     *
     * @param context caller's security context
     * @return list of visible institutes
     */
    List<Institute> listInstitutes(UserSecurityContext context);

    /**
     * Lists institutes matching status and pagination filters visible under RLS.
     *
     * @param context caller's security context
     * @param status  filter status (optional)
     * @param page    page number (1-indexed)
     * @param size    page size
     * @return list of visible institutes
     */
    List<Institute> listInstitutes(UserSecurityContext context, String status, int page, int size);
}
