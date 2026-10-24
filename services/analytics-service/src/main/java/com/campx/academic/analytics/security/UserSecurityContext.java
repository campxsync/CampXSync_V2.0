package com.campx.academic.analytics.security;

import java.util.UUID;

/**
 * Thread-safe security context holding validated authentication, tenant,
 * and authorization claims for ACD-10: Reporting & Analytics Service.
 */
public class UserSecurityContext {

    private final UUID userId;
    private final UUID tenantId;
    private final String role;
    private final String departmentId;

    public UserSecurityContext(UUID userId, UUID tenantId, String role, String departmentId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role != null ? role.toUpperCase() : "ANONYMOUS";
        this.departmentId = departmentId;
    }

    public UserSecurityContext(UUID userId, UUID tenantId, String role) {
        this(userId, tenantId, role, null);
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getRole() { return role; }
    public String getDepartmentId() { return departmentId; }

    public boolean isCollegeAdmin() {
        return "COLLEGE_ADMIN".equalsIgnoreCase(role) || "ACADEMIC_ADMIN".equalsIgnoreCase(role);
    }

    public boolean isHod() {
        return "HOD".equalsIgnoreCase(role) || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(role);
    }

    public boolean isFaculty() {
        return "FACULTY".equalsIgnoreCase(role);
    }

    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equalsIgnoreCase(role);
    }
}
