package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.GlobalPolicy;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing platform policies in {@code cfg.policies}.
 */
public interface GlobalPolicyRepository {

    /**
     * Persists or updates a global policy definition.
     *
     * @param context caller security context
     * @param policy  global policy definition
     * @return saved global policy
     */
    GlobalPolicy savePolicy(UserSecurityContext context, GlobalPolicy policy);

    /**
     * Resolves a policy by unique code.
     *
     * @param context    caller security context
     * @param policyCode policy code (e.g. "SEC_PWD_EXPIRY")
     * @return optional containing policy if found
     */
    Optional<GlobalPolicy> getPolicyByCode(UserSecurityContext context, String policyCode);

    /**
     * Lists all active global policies.
     *
     * @param context caller security context
     * @return list of global policies
     */
    List<GlobalPolicy> listPolicies(UserSecurityContext context);

    /**
     * Deletes a policy (for test teardown).
     *
     * @param context    caller security context
     * @param policyCode policy code
     */
    void deletePolicy(UserSecurityContext context, String policyCode);
}
