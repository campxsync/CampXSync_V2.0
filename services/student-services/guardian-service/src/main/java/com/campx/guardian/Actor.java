package com.campx.guardian;

import com.campx.guardian.security.LegacyRegistrarStandin;

import java.util.UUID;

/**
 * Trusted identity forwarded by the API Gateway. The service does not accept a client-supplied tenant.
 */
public final class Actor {
    private final UUID tenantId;
    private final UUID userId;
    private final UUID studentRecordId;
    private final UUID collegeId;
    private final String role;
    private final String correlationId;

    public Actor(UUID tenantId, UUID userId, String role, String correlationId) {
        this(tenantId, userId, null, null, role, correlationId);
    }

    public Actor(UUID tenantId, UUID userId, UUID studentRecordId, String role, String correlationId) {
        this(tenantId, userId, studentRecordId, null, role, correlationId);
    }

    public Actor(UUID tenantId, UUID userId, UUID studentRecordId, UUID collegeId, String role, String correlationId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.studentRecordId = studentRecordId;
        this.collegeId = collegeId;
        this.role = role == null ? "" : role.trim().toUpperCase();
        this.correlationId = correlationId == null ? "" : correlationId;
    }

    public UUID subjectStudentId() {
        return studentRecordId != null ? studentRecordId : userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCollegeId() {
        return collegeId;
    }

    public String getRole() {
        return role;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isRegistrar() {
        if ("R31".equals(role) || "REGISTRAR".equals(role)) {
            return true;
        }
        if (LegacyRegistrarStandin.isEnabled()) {
            return "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
        }
        return false;
    }
}