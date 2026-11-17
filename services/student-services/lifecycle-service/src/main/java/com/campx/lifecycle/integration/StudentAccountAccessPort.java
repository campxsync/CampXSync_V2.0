package com.campx.lifecycle.integration;

import java.util.UUID;

/**
 * STM-07-EN-005 — notify student-account provisioning when lifecycle cuts portal access.
 * Implementations must be fail-soft: lifecycle transition still commits if the notify fails.
 */
public interface StudentAccountAccessPort {

    /**
     * Called after a successful lifecycle status change that should revoke login access
     * (SUSPENDED, DETAINED, WITHDRAWN). No-op for other statuses.
     */
    void onAccessCuttingStatus(UUID tenantId, UUID studentId, String newStatus, String correlationId);
}
