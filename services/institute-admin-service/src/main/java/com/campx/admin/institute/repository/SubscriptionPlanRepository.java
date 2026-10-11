package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;

/**
 * Repository interface for managing {@code plat.subscription_plans} persistence.
 */
public interface SubscriptionPlanRepository {

    /**
     * Persists a new commercial subscription plan.
     *
     * @param context caller security context (platform admin)
     * @param plan    plan definition
     * @return persisted plan
     */
    SubscriptionPlan createPlan(UserSecurityContext context, SubscriptionPlan plan);

    /**
     * Finds a plan by ID.
     *
     * @param context caller security context
     * @param id      plan ID
     * @return plan if found, null otherwise
     */
    SubscriptionPlan getPlanById(UserSecurityContext context, String id);

    /**
     * Finds a plan by its unique plan code.
     *
     * @param context  caller security context
     * @param planCode alphanumeric code (e.g. "ENTERPRISE_ANNUAL")
     * @return plan if found, null otherwise
     */
    SubscriptionPlan getPlanByCode(UserSecurityContext context, String planCode);

    /**
     * Lists all published subscription plans.
     *
     * @param context caller security context
     * @return list of published plans
     */
    List<SubscriptionPlan> listPlans(UserSecurityContext context);

    /**
     * Updates an existing subscription plan under optimistic locking.
     *
     * @param context caller security context (platform admin)
     * @param plan    updated plan definition
     * @return updated plan
     */
    SubscriptionPlan updatePlan(UserSecurityContext context, SubscriptionPlan plan);

    /**
     * Soft-deletes a plan.
     *
     * @param context caller security context (platform admin)
     * @param id      plan ID
     */
    void deletePlan(UserSecurityContext context, String id);
}
