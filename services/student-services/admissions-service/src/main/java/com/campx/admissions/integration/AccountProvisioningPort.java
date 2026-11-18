package com.campx.admissions.integration;

import java.util.UUID;

/**
 * STM-07-EN-005 — best-effort "request a new login" after admission handover.
 * Copied from the lifecycle {@code StudentAccountAccessPort} pattern (HTTP to student-service).
 */
public interface AccountProvisioningPort {

    /**
     * Ask student-service to open an IAM provisioning request for the handed-over student.
     * Implementations must be fail-soft (log and continue) so handover success is not rolled back.
     */
    void requestLoginAfterHandover(UUID tenantId, UUID studentId, String correlationId);
}
