package com.campx.document;

import com.campx.document.security.LegacyRegistrarStandin;

import java.util.Objects;
import java.util.UUID;

/**
 * Caller identity forwarded by the API Gateway or tests.
 * D12b: optional {@code collegeId} is the assigned campus (IAM college_id).
 */
public class Actor {

    private final UUID tenantId;
    private final UUID userId;
    private final UUID collegeId;
    private final String role;
    private final String email;
    private final String correlationId;

    public Actor(UUID tenantId, UUID userId, String role, String email) {
        this(tenantId, userId, null, role, email, null);
    }

    public Actor(UUID tenantId, UUID userId, String role, String email, String correlationId) {
        this(tenantId, userId, null, role, email, correlationId);
    }

    public Actor(UUID tenantId, UUID userId, UUID collegeId, String role, String email, String correlationId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.collegeId = collegeId;
        this.role = Objects.requireNonNull(role, "role").toUpperCase();
        this.email = email == null ? "" : email;
        this.correlationId = correlationId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    /** Assigned campus (IAM college_id); may be null for non-campus-scoped callers. */
    public UUID getCollegeId() {
        return collegeId;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isCollegeAdmin() {
        return "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role) || "ADMIN".equals(role);
    }

    /**
     * Canonical Registrar: R31 or REGISTRAR.
     * D12d: COLLEGE_ADMIN / INSTITUTION_ADMIN only when {@link LegacyRegistrarStandin} is ON.
     */
    public boolean isRegistrar() {
        if ("R31".equals(role) || "REGISTRAR".equals(role)) {
            return true;
        }
        if (LegacyRegistrarStandin.isEnabled()) {
            return "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
        }
        return false;
    }

    public boolean isStudent() {
        return "STUDENT".equals(role) || "R25".equals(role);
    }

    public Actor asTenantAdmin() {
        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        return new Actor(this.tenantId, adminUserId, this.collegeId, "COLLEGE_ADMIN", "dev.admin@campxsync.test", this.correlationId);
    }
}
