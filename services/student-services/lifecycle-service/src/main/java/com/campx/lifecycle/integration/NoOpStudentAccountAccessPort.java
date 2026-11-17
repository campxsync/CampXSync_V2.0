package com.campx.lifecycle.integration;

import java.util.UUID;

/** Default when student-service notify URL is not configured. */
public final class NoOpStudentAccountAccessPort implements StudentAccountAccessPort {
    @Override
    public void onAccessCuttingStatus(UUID tenantId, UUID studentId, String newStatus, String correlationId) {
        // intentionally empty
    }
}
