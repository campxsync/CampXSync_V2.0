package com.campx.enrollment;

import com.campx.enrollment.security.LegacyRegistrarStandin;

import java.util.UUID;

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

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getStudentRecordId() {
        return studentRecordId;
    }

    /**
     * Prefer institutional student record id when the BFF resolved /students/me.
     */
    public UUID subjectStudentId() {
        return studentRecordId != null ? studentRecordId : userId;
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