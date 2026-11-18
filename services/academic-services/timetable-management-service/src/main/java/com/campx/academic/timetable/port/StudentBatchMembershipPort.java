package com.campx.academic.timetable.port;

import com.campx.academic.timetable.security.UserSecurityContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the authenticated student's active batch from existing membership data.
 * Used by STM-10-US-004 "my timetable" — never trusts a client-supplied batch id.
 */
public interface StudentBatchMembershipPort {

    /**
     * @return active batch id for the actor in context, or empty if none / not a student membership
     */
    Optional<UUID> findActiveBatchIdForActor(UserSecurityContext context);
}
