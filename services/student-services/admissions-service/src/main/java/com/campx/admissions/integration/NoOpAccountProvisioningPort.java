package com.campx.admissions.integration;

import java.util.UUID;

/** Default when {@code STM01_SERVICE_URL} / {@code STUDENT_SERVICE_URL} is unset. */
public final class NoOpAccountProvisioningPort implements AccountProvisioningPort {
    @Override
    public void requestLoginAfterHandover(UUID tenantId, UUID studentId, String correlationId) {
        // no-op
    }
}
