package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing temporary authority delegations (Item 18: {@code iam.delegations}).
 * Enforces tenant boundary validation and RLS checks under {@code iam.perm_all('adm09.*')}.
 */
public interface DelegationRepository {

    /**
     * Creates a new delegation assignment between two user profiles.
     *
     * @param context    caller security context
     * @param delegation delegation entity to persist
     * @return persisted delegation entity
     */
    Delegation createDelegation(UserSecurityContext context, Delegation delegation);

    /**
     * Retrieves a delegation by ID within the caller's tenant.
     *
     * @param context caller security context
     * @param id      delegation UUID
     * @return optional containing delegation if found and authorized
     */
    Optional<Delegation> findById(UserSecurityContext context, String id);

    /**
     * Lists all active delegations for the caller's tenant.
     *
     * @param context caller security context
     * @return list of delegations
     */
    List<Delegation> listDelegations(UserSecurityContext context);

    /**
     * Lists delegations where the specified user is either delegator (from) or delegatee (to).
     *
     * @param context caller security context
     * @param userId  user profile UUID
     * @return list of matching delegations
     */
    List<Delegation> listDelegationsForUser(UserSecurityContext context, String userId);

    /**
     * Updates an existing delegation (reason, validTo, status).
     *
     * @param context    caller security context
     * @param delegation entity containing modifications
     * @return updated delegation entity
     */
    Delegation updateDelegation(UserSecurityContext context, Delegation delegation);

    /**
     * Revokes or cancels a delegation assignment.
     *
     * @param context caller security context
     * @param id      delegation UUID
     */
    void revokeDelegation(UserSecurityContext context, String id);
}
