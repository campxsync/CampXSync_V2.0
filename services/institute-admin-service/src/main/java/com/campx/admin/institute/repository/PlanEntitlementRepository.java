package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.PlanEntitlement;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;

/**
 * Repository interface for managing {@code plat.plan_entitlements} persistence.
 */
public interface PlanEntitlementRepository {

    /**
     * Persists a new plan entitlement.
     *
     * @param context     caller security context (platform admin)
     * @param entitlement entitlement definition
     * @return persisted entitlement
     */
    PlanEntitlement createEntitlement(UserSecurityContext context, PlanEntitlement entitlement);

    /**
     * Finds an entitlement by its unique ID.
     *
     * @param context caller security context
     * @param id      entitlement UUID
     * @return entitlement if found, null otherwise
     */
    PlanEntitlement getEntitlementById(UserSecurityContext context, String id);

    /**
     * Lists all active entitlements for a given subscription plan.
     *
     * @param context caller security context
     * @param planId  subscription plan UUID
     * @return list of active entitlements for the plan
     */
    List<PlanEntitlement> listEntitlementsByPlan(UserSecurityContext context, String planId);

    /**
     * Updates an entitlement under optimistic concurrency control.
     *
     * @param context     caller security context (platform admin)
     * @param entitlement updated entitlement definition
     * @return updated entitlement
     */
    PlanEntitlement updateEntitlement(UserSecurityContext context, PlanEntitlement entitlement);

    /**
     * Soft-deletes a plan entitlement.
     *
     * @param context caller security context (platform admin)
     * @param id      entitlement UUID
     * @return true if deleted, false if not found
     */
    boolean deleteEntitlement(UserSecurityContext context, String id);
}
