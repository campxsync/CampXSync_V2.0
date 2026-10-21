package com.campx.gateway.identity;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Authoritative CampXSync user identity resolved from trusted database records.
 * <p>
 * Enforces strict multi-tenant boundary parameters, verified business role assignment,
 * and account status eligibility.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class ResolvedIdentity {

    private final String userId;
    private final String email;
    private final String fullName;
    private final String displayName;
    private final String businessRole;
    private final String normalizedRole;
    private final List<String> roles;
    private final String tenantId;
    private final String tenantName;
    private final String tenantStatus;
    private final String collegeId;
    private final String departmentId;
    private final String status;
    private final boolean isPlatformAdmin;

    public ResolvedIdentity(String userId,
                            String email,
                            String fullName,
                            String displayName,
                            String businessRole,
                            String normalizedRole,
                            String tenantId,
                            String tenantName,
                            String tenantStatus,
                            String collegeId,
                            String departmentId,
                            String status,
                            boolean isPlatformAdmin) {
        this(userId, email, fullName, displayName, businessRole, normalizedRole,
                businessRole != null ? Collections.singletonList(businessRole) : Collections.emptyList(),
                tenantId, tenantName, tenantStatus, collegeId, departmentId, status, isPlatformAdmin);
    }

    public ResolvedIdentity(String userId,
                            String email,
                            String fullName,
                            String displayName,
                            String businessRole,
                            String normalizedRole,
                            List<String> roles,
                            String tenantId,
                            String tenantName,
                            String tenantStatus,
                            String collegeId,
                            String departmentId,
                            String status,
                            boolean isPlatformAdmin) {
        this.userId = userId;
        this.email = email != null ? email : "";
        this.fullName = fullName != null ? fullName : "";
        this.displayName = displayName != null && !displayName.trim().isEmpty()
                ? displayName : (fullName != null && !fullName.trim().isEmpty() ? fullName : (email != null && email.contains("@") ? email.split("@")[0] : userId));
        this.businessRole = businessRole != null ? businessRole.toUpperCase(Locale.ROOT) : "AUTHENTICATED";
        this.normalizedRole = normalizedRole != null ? normalizedRole.toLowerCase(Locale.ROOT) : normalizeRole(this.businessRole);
        this.roles = roles != null ? Collections.unmodifiableList(new java.util.ArrayList<>(roles)) : Collections.emptyList();
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.tenantStatus = tenantStatus != null ? tenantStatus : "ACTIVE";
        this.collegeId = collegeId;
        this.departmentId = departmentId;
        this.status = status != null ? status.toUpperCase(Locale.ROOT) : "ACTIVE";
        this.isPlatformAdmin = isPlatformAdmin;
    }

    public static String normalizeRole(String businessRole) {
        if (businessRole == null) return "authenticated";
        String upper = businessRole.toUpperCase(Locale.ROOT);
        if (upper.equals("SUPER_ADMIN") || upper.equals("PLATFORM_ADMIN")) {
            return "platform_admin";
        }
        if (upper.equals("COLLEGE_ADMIN") || upper.equals("INSTITUTION_ADMIN") || upper.equals("TENANT_ADMIN")
                || upper.equals("PRINCIPAL") || upper.equals("ACADEMIC_ADMIN")) {
            return "college_admin";
        }
        if (upper.equals("FACULTY") || upper.equals("HOD") || upper.equals("DEAN")
                || upper.equals("TEACHER") || upper.equals("SECTION_COORDINATOR")) {
            return "faculty";
        }
        if (upper.equals("STUDENT")) {
            return "student";
        }
        return upper.toLowerCase(Locale.ROOT);
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getDisplayName() { return displayName; }
    public String getBusinessRole() { return businessRole; }
    public String getNormalizedRole() { return normalizedRole; }
    public List<String> getRoles() { return roles; }
    public String getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public String getTenantStatus() { return tenantStatus; }
    public String getCollegeId() { return collegeId; }
    public String getDepartmentId() { return departmentId; }
    public String getStatus() { return status; }
    public boolean isPlatformAdmin() { return isPlatformAdmin; }

    /**
     * Serializes this resolved identity to a stable, documented JSON contract for GET /api/v1/auth/me.
     *
     * @return Formatted JSON string representation
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"userId\":\"").append(escape(userId)).append("\",");
        sb.append("\"email\":\"").append(escape(email)).append("\",");
        sb.append("\"fullName\":\"").append(escape(fullName)).append("\",");
        sb.append("\"displayName\":\"").append(escape(displayName)).append("\",");
        sb.append("\"role\":\"").append(escape(businessRole)).append("\",");
        sb.append("\"normalizedRole\":\"").append(escape(normalizedRole)).append("\",");
        sb.append("\"roles\":[");
        if (roles != null && !roles.isEmpty()) {
            for (int i = 0; i < roles.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(roles.get(i))).append("\"");
            }
        }
        sb.append("],");

        if (tenantId != null) {
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
        } else {
            sb.append("\"tenantId\":null,");
        }

        if (tenantName != null) {
            sb.append("\"tenantName\":\"").append(escape(tenantName)).append("\",");
        } else {
            sb.append("\"tenantName\":null,");
        }

        if (tenantStatus != null) {
            sb.append("\"tenantStatus\":\"").append(escape(tenantStatus)).append("\",");
        } else {
            sb.append("\"tenantStatus\":null,");
        }

        if (collegeId != null) {
            sb.append("\"collegeId\":\"").append(escape(collegeId)).append("\",");
        } else {
            sb.append("\"collegeId\":null,");
        }

        if (departmentId != null) {
            sb.append("\"departmentId\":\"").append(escape(departmentId)).append("\",");
        } else {
            sb.append("\"departmentId\":null,");
        }

        sb.append("\"status\":\"").append(escape(status)).append("\",");
        sb.append("\"isPlatformAdmin\":").append(isPlatformAdmin);
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
