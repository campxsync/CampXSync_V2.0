package com.campx.gateway.identity;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test and local implementation of {@link IdentityResolver}.
 * <p>
 * Simulates authoritative database records across {@code plat.platform_admins},
 * {@code iam.user_profiles}, {@code core.tenants}, and {@code iam.role_assignments}.
 * Guarantees zero live database connection requirements during testing while strictly
 * verifying fail-closed multi-tenant and role eligibility semantics.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class InMemoryIdentityResolver implements IdentityResolver {

    public static class PlatformAdminRecord {
        public final UUID userId;
        public final String fullName;
        public final String roleCode;
        public final String status;

        public PlatformAdminRecord(UUID userId, String fullName, String roleCode, String status) {
            this.userId = userId;
            this.fullName = fullName;
            this.roleCode = roleCode;
            this.status = status;
        }
    }

    public static class UserProfileRecord {
        public final UUID userId;
        public final UUID tenantId;
        public final String email;
        public final String fullName;
        public final String status;
        public final UUID collegeId;
        public final UUID departmentId;

        public UserProfileRecord(UUID userId, UUID tenantId, String email, String fullName, String status, UUID collegeId, UUID departmentId) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.email = email;
            this.fullName = fullName;
            this.status = status;
            this.collegeId = collegeId;
            this.departmentId = departmentId;
        }
    }

    public static class TenantRecord {
        public final UUID tenantId;
        public final String name;
        public final String code;
        public final String status;

        public TenantRecord(UUID tenantId, String name, String code, String status) {
            this.tenantId = tenantId;
            this.name = name;
            this.code = code;
            this.status = status;
        }
    }

    public static class RoleAssignmentRecord {
        public final UUID userId;
        public final UUID tenantId;
        public final String roleCode;
        public final UUID collegeId;
        public final UUID departmentId;
        public final LocalDate validFrom;
        public final LocalDate validTo;

        public RoleAssignmentRecord(UUID userId, UUID tenantId, String roleCode, UUID collegeId, UUID departmentId, LocalDate validFrom, LocalDate validTo) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.roleCode = roleCode;
            this.collegeId = collegeId;
            this.departmentId = departmentId;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }
    }

    private final Map<UUID, PlatformAdminRecord> platformAdmins = new ConcurrentHashMap<>();
    private final Map<UUID, UserProfileRecord> userProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, TenantRecord> tenants = new ConcurrentHashMap<>();
    private final List<RoleAssignmentRecord> roleAssignments = Collections.synchronizedList(new ArrayList<>());

    public InMemoryIdentityResolver() {}

    public void registerPlatformAdmin(UUID userId, String fullName, String roleCode, String status) {
        platformAdmins.put(userId, new PlatformAdminRecord(userId, fullName, roleCode, status));
    }

    public void registerUserProfile(UUID userId, UUID tenantId, String email, String fullName, String status, UUID collegeId, UUID departmentId) {
        userProfiles.put(userId, new UserProfileRecord(userId, tenantId, email, fullName, status, collegeId, departmentId));
    }

    public void registerTenant(UUID tenantId, String name, String code, String status) {
        tenants.put(tenantId, new TenantRecord(tenantId, name, code, status));
    }

    public void registerRoleAssignment(UUID userId, UUID tenantId, String roleCode, UUID collegeId, UUID departmentId, LocalDate validFrom, LocalDate validTo) {
        roleAssignments.add(new RoleAssignmentRecord(userId, tenantId, roleCode, collegeId, departmentId, validFrom, validTo));
    }

    public void clear() {
        platformAdmins.clear();
        userProfiles.clear();
        tenants.clear();
        roleAssignments.clear();
    }

    @Override
    public IdentityResolutionResult resolveIdentity(UUID userId) {
        if (userId == null) {
            return IdentityResolutionResult.notFound("User identity key (sub) cannot be null");
        }

        // 1. Authoritative check against plat.platform_admins
        PlatformAdminRecord platAdmin = platformAdmins.get(userId);
        if (platAdmin != null) {
            if (!"ACTIVE".equalsIgnoreCase(platAdmin.status)) {
                return IdentityResolutionResult.ineligible("inactive_membership", "Platform administrator account is " + platAdmin.status);
            }
            String adminRole = platAdmin.roleCode != null ? platAdmin.roleCode : "SUPER_ADMIN";
            ResolvedIdentity identity = new ResolvedIdentity(
                    userId.toString(),
                    userId.toString() + "@platform.campx",
                    platAdmin.fullName,
                    platAdmin.fullName,
                    adminRole,
                    "platform_admin",
                    Collections.singletonList(adminRole),
                    null,
                    "CampXSync Platform",
                    "ACTIVE",
                    null,
                    null,
                    "ACTIVE",
                    true
            );
            return IdentityResolutionResult.success(identity);
        }

        // 2. Query iam.user_profiles
        UserProfileRecord profile = userProfiles.get(userId);
        if (profile == null) {
            return IdentityResolutionResult.notFound("No active user profile found for identity: " + userId);
        }

        if (!"ACTIVE".equalsIgnoreCase(profile.status)) {
            return IdentityResolutionResult.ineligible("inactive_membership", "User account is " + profile.status);
        }

        if (profile.tenantId == null) {
            return IdentityResolutionResult.ineligible("no_profile", "User profile is not associated with any tenant");
        }

        // 3. Query core.tenants
        TenantRecord tenant = tenants.get(profile.tenantId);
        if (tenant == null) {
            return IdentityResolutionResult.ineligible("tenant_not_found", "Tenant not found for ID: " + profile.tenantId);
        }

        if (!"ACTIVE".equalsIgnoreCase(tenant.status)) {
            return IdentityResolutionResult.ineligible("tenant_suspended", "Tenant organization is " + tenant.status);
        }

        // 4. Query iam.role_assignments
        LocalDate today = LocalDate.now();
        List<RoleAssignmentRecord> matchingAssignments = new ArrayList<>();
        synchronized (roleAssignments) {
            for (RoleAssignmentRecord ra : roleAssignments) {
                if (ra.userId.equals(userId) && ra.tenantId.equals(profile.tenantId)) {
                    if (ra.validFrom != null && ra.validFrom.isAfter(today)) {
                        continue;
                    }
                    if (ra.validTo != null && ra.validTo.isBefore(today)) {
                        continue;
                    }
                    matchingAssignments.add(ra);
                }
            }
        }

        if (matchingAssignments.isEmpty()) {
            return IdentityResolutionResult.ineligible("no_role_assigned", "No active business role assignment found for user in tenant");
        }

        // Preserve all assigned roles and derive effective role strictly according to canonical rules
        List<String> assignedRoles = new ArrayList<>();
        for (RoleAssignmentRecord ra : matchingAssignments) {
            if (ra.roleCode != null && !ra.roleCode.trim().isEmpty()) {
                String c = ra.roleCode.trim();
                if (!assignedRoles.contains(c)) {
                    assignedRoles.add(c);
                }
            }
        }

        String resolvedRole = null;
        UUID matchedCollegeId = profile.collegeId;
        UUID matchedDepartmentId = profile.departmentId;

        for (RoleAssignmentRecord ra : matchingAssignments) {
            String code = ra.roleCode != null ? ra.roleCode.toUpperCase(Locale.ROOT) : "";
            if (isCollegeAdminRole(code)) {
                resolvedRole = ra.roleCode != null ? ra.roleCode : "COLLEGE_ADMIN";
                if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                if (ra.departmentId != null) matchedDepartmentId = ra.departmentId;
                break;
            } else if (isFacultyRole(code)) {
                if (resolvedRole == null || isStudentRole(resolvedRole)) {
                    resolvedRole = ra.roleCode != null ? ra.roleCode : "FACULTY";
                    if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                    if (ra.departmentId != null) matchedDepartmentId = ra.departmentId;
                }
            } else if (isStudentRole(code)) {
                if (resolvedRole == null) {
                    resolvedRole = ra.roleCode != null ? ra.roleCode : "STUDENT";
                    if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                    if (ra.departmentId != null) matchedDepartmentId = ra.departmentId;
                }
            }
        }

        if (resolvedRole == null) {
            return IdentityResolutionResult.ineligible("no_role_assigned", "No active recognized business role assignment found for user in tenant");
        }

        ResolvedIdentity identity = new ResolvedIdentity(
                userId.toString(),
                profile.email,
                profile.fullName,
                profile.fullName,
                resolvedRole,
                ResolvedIdentity.normalizeRole(resolvedRole),
                assignedRoles,
                tenant.tenantId.toString(),
                tenant.name,
                tenant.status,
                matchedCollegeId != null ? matchedCollegeId.toString() : null,
                matchedDepartmentId != null ? matchedDepartmentId.toString() : null,
                profile.status,
                false
        );

        return IdentityResolutionResult.success(identity);
    }

    private static boolean isCollegeAdminRole(String code) {
        if (code == null) return false;
        String upper = code.toUpperCase(Locale.ROOT);
        return upper.equals("COLLEGE_ADMIN") || upper.equals("INSTITUTION_ADMIN")
                || upper.equals("TENANT_ADMIN") || upper.equals("PRINCIPAL")
                || upper.equals("ACADEMIC_ADMIN");
    }

    private static boolean isFacultyRole(String code) {
        if (code == null) return false;
        String upper = code.toUpperCase(Locale.ROOT);
        return upper.equals("FACULTY") || upper.equals("HOD") || upper.equals("DEAN")
                || upper.equals("TEACHER") || upper.equals("SECTION_COORDINATOR");
    }

    private static boolean isStudentRole(String code) {
        if (code == null) return false;
        String upper = code.toUpperCase(Locale.ROOT);
        return upper.equals("STUDENT");
    }
}
