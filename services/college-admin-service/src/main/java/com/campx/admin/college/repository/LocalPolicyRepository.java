package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.LocalPolicy;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing college local policies in {@code cfg.policies}.
 */
public interface LocalPolicyRepository {

    /**
     * Creates a new draft local policy.
     *
     * @param context caller security context
     * @param policy  local policy entity
     * @return persisted policy entity
     */
    LocalPolicy createLocalPolicy(UserSecurityContext context, LocalPolicy policy);

    /**
     * Updates an existing local policy (approval or publication).
     *
     * @param context caller security context
     * @param policy  local policy entity
     * @return updated policy entity
     */
    LocalPolicy updateLocalPolicy(UserSecurityContext context, LocalPolicy policy);

    /**
     * Finds a policy by its unique policy code.
     *
     * @param context    caller security context
     * @param policyCode policy code string
     * @return optional containing policy if found
     */
    Optional<LocalPolicy> findByCode(UserSecurityContext context, String policyCode);

    /**
     * Lists all local policies for the caller's scope.
     *
     * @param context caller security context
     * @return list of policies
     */
    List<LocalPolicy> listPolicies(UserSecurityContext context);
}
