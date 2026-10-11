package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Subscription;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;

/**
 * Repository interface for managing {@code plat.subscriptions} persistence (ADM-01 Item 9).
 */
public interface SubscriptionRepository {

    /**
     * Persists a tenant subscription.
     *
     * @param context caller security context
     * @param sub     subscription definition
     * @return persisted subscription
     */
    Subscription createSubscription(UserSecurityContext context, Subscription sub);

    /**
     * Retrieves subscription by identifier.
     *
     * @param context caller security context
     * @param id      subscription ID
     * @return subscription if found, null otherwise
     */
    Subscription getSubscriptionById(UserSecurityContext context, String id);

    /**
     * Retrieves active subscription for the tenant.
     *
     * @param context caller security context
     * @return current active subscription, or null
     */
    Subscription getActiveSubscription(UserSecurityContext context);

    /**
     * Lists all subscriptions for the tenant.
     *
     * @param context caller security context
     * @return list of subscriptions
     */
    List<Subscription> listSubscriptions(UserSecurityContext context);

    /**
     * Updates an existing subscription.
     *
     * @param context caller security context
     * @param sub     updated subscription
     * @return updated subscription
     */
    Subscription updateSubscription(UserSecurityContext context, Subscription sub);

    /**
     * Cancels / terminates a subscription.
     *
     * @param context caller security context
     * @param id      subscription ID
     */
    void cancelSubscription(UserSecurityContext context, String id);
}
