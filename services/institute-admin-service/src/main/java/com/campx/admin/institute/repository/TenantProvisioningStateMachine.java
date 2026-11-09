package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.InvalidStatusTransitionException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single authoritative lifecycle transition table for tenant provisioning orchestration (ADM-01 Item 3 / MVP-ADM01-04).
 * <p>
 * Allowed standard transitions:
 * <ul>
 *   <li>REQUESTED &rarr; IN_PROGRESS</li>
 *   <li>IN_PROGRESS &rarr; COMPLETED</li>
 *   <li>IN_PROGRESS &rarr; FAILED</li>
 * </ul>
 * <p>
 * Authorized retry transitions (Platform Admin only):
 * <ul>
 *   <li>FAILED &rarr; IN_PROGRESS</li>
 * </ul>
 * <p>
 * Terminal immutability:
 * <ul>
 *   <li>COMPLETED &rarr; any other state is strictly rejected</li>
 * </ul>
 * <p>
 * All other pairs are rejected with {@link InvalidStatusTransitionException}.
 */
public final class TenantProvisioningStateMachine {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private static final Map<String, Set<String>> STANDARD_TRANSITIONS;
    private static final Map<String, Set<String>> RETRY_TRANSITIONS;

    static {
        Map<String, Set<String>> std = new HashMap<>();
        std.put(STATUS_REQUESTED, Collections.singleton(STATUS_IN_PROGRESS));

        Set<String> inProgressTargets = new HashSet<>();
        inProgressTargets.add(STATUS_COMPLETED);
        inProgressTargets.add(STATUS_FAILED);
        std.put(STATUS_IN_PROGRESS, Collections.unmodifiableSet(inProgressTargets));

        std.put(STATUS_COMPLETED, Collections.emptySet());
        std.put(STATUS_FAILED, Collections.emptySet());
        STANDARD_TRANSITIONS = Collections.unmodifiableMap(std);

        Map<String, Set<String>> retry = new HashMap<>();
        retry.put(STATUS_FAILED, Collections.singleton(STATUS_IN_PROGRESS));
        RETRY_TRANSITIONS = Collections.unmodifiableMap(retry);
    }

    private TenantProvisioningStateMachine() {
    }

    /**
     * Normalizes a provisioning status string to uppercase and trimmed format.
     *
     * @param status input status string
     * @return uppercase normalized status
     * @throws InvalidStatusTransitionException if status is null or blank
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new InvalidStatusTransitionException("Status cannot be null or empty");
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Checks if a transition between two states is permitted.
     *
     * @param fromStatus initial status
     * @param toStatus   target status
     * @param isRetry    true if the transition is requested via the authorized retry operation
     * @return true if permitted, false otherwise
     */
    public static boolean isLegalTransition(String fromStatus, String toStatus, boolean isRetry) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        String fromNorm = fromStatus.trim().toUpperCase(Locale.ROOT);
        String toNorm = toStatus.trim().toUpperCase(Locale.ROOT);

        if (isRetry) {
            Set<String> allowed = RETRY_TRANSITIONS.get(fromNorm);
            return allowed != null && allowed.contains(toNorm);
        } else {
            Set<String> allowed = STANDARD_TRANSITIONS.get(fromNorm);
            return allowed != null && allowed.contains(toNorm);
        }
    }

    /**
     * Validates a standard status transition.
     *
     * @param fromStatus current lifecycle status
     * @param toStatus   target lifecycle status
     * @throws InvalidStatusTransitionException if the transition is illegal or prohibited
     */
    public static void validateTransition(String fromStatus, String toStatus) {
        validateTransition(fromStatus, toStatus, false);
    }

    /**
     * Validates a status transition, distinguishing between standard updates and authorized retry operations.
     *
     * @param fromStatus current lifecycle status
     * @param toStatus   target lifecycle status
     * @param isRetry    true if invoked via authorized platform-admin retry operation
     * @throws InvalidStatusTransitionException if the transition is illegal, violates COMPLETED immutability,
     *                                           or attempts FAILED -> IN_PROGRESS outside of retry
     */
    public static void validateTransition(String fromStatus, String toStatus, boolean isRetry) {
        String fromNorm = normalizeStatus(fromStatus);
        String toNorm = normalizeStatus(toStatus);

        if (STATUS_COMPLETED.equals(fromNorm)) {
            throw new InvalidStatusTransitionException(
                    "State 'COMPLETED' is terminal and immutable. No further transitions are permitted");
        }

        if (!isLegalTransition(fromNorm, toNorm, isRetry)) {
            if (STATUS_FAILED.equals(fromNorm) && STATUS_IN_PROGRESS.equals(toNorm) && !isRetry) {
                throw new InvalidStatusTransitionException(
                        "Transition from 'FAILED' to 'IN_PROGRESS' is rejected via plain update; only permitted via authorized retry operation");
            }
            throw new InvalidStatusTransitionException(fromNorm, toNorm);
        }
    }
}
