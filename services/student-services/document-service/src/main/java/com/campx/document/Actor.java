package com.campx.document;

import java.util.Objects;
import java.util.UUID;

/**
 * Caller identity forwarded by the API Gateway or tests.
 */
public class Actor {

    private final UUID tenantId;
    private final UUID userId;
    private final String role;
    private final String email;
    private final String correlationId;

    public Actor(UUID tenantId, UUID userId, String role, String email) {
        this(tenantId, userId, role, email, null);
    }

    public Actor(UUID tenantId, UUID userId, String role, String email, String correlationId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
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

    public boolean isRegistrar() {
        return isCollegeAdmin() || "REGISTRAR".equals(role);
    }

    public boolean isStudent() {
        return "STUDENT".equals(role);
    }

    public Actor asTenantAdmin() {
        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        return new Actor(this.tenantId, adminUserId, "COLLEGE_ADMIN", "dev.admin@campxsync.test", this.correlationId);
    }
}
