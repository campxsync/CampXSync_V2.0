package com.campx.gateway.identity;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Authoritative PostgreSQL implementation of {@link IdentityResolver}.
 * <p>
 * Directly queries trusted relational database records across:
 * <ul>
 *   <li>{@code plat.platform_admins} for super-administrative identities</li>
 *   <li>{@code iam.user_profiles} for user account and tenant association</li>
 *   <li>{@code core.tenants} for tenant status verification</li>
 *   <li>{@code iam.role_assignments} and {@code iam.roles} for business roles and permissions</li>
 * </ul>
 * Guarantees zero trust in unverified JWT metadata or client-supplied headers.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class JdbcIdentityResolver implements IdentityResolver {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(JdbcIdentityResolver.class);

    private final DataSource dataSource;

    public JdbcIdentityResolver() {
        this(GatewayDatabaseManager.getInstance().getDataSource());
    }

    public JdbcIdentityResolver(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource cannot be null for JdbcIdentityResolver");
        }
        this.dataSource = dataSource;
    }

    @Override
    public IdentityResolutionResult resolveIdentity(UUID userId) {
        if (userId == null) {
            return IdentityResolutionResult.notFound("User identity key (sub) cannot be null");
        }

        try (Connection conn = dataSource.getConnection()) {

            // 1. Authoritative check against plat.platform_admins
            String platSql = "SELECT id, user_id, full_name, role_code, status "
                    + "FROM plat.platform_admins "
                    + "WHERE user_id = ? AND deleted_at IS NULL "
                    + "LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(platSql)) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        if (!"ACTIVE".equalsIgnoreCase(status)) {
                            return IdentityResolutionResult.ineligible("inactive_membership", "Platform administrator account is " + status);
                        }
                        String fullName = rs.getString("full_name");
                        String roleCode = rs.getString("role_code");
                        String adminRole = roleCode != null ? roleCode : "SUPER_ADMIN";
                        ResolvedIdentity identity = new ResolvedIdentity(
                                userId.toString(),
                                userId.toString() + "@platform.campx",
                                fullName,
                                fullName,
                                adminRole,
                                "platform_admin",
                                java.util.Collections.singletonList(adminRole),
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
                }
            } catch (SQLException e) {
                logger.debug("plat.platform_admins query bypassed or table not present: {}", e.getMessage());
            }

            // 2. Query iam.user_profiles
            String profileSql = "SELECT id, tenant_id, email, full_name, status, college_id, department_id "
                    + "FROM iam.user_profiles "
                    + "WHERE id = ? AND deleted_at IS NULL "
                    + "LIMIT 1";

            UUID tenantId = null;
            String email = null;
            String fullName = null;
            String userStatus = null;
            UUID collegeId = null;
            UUID departmentId = null;

            try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("tenant_id");
                        email = rs.getString("email");
                        fullName = rs.getString("full_name");
                        userStatus = rs.getString("status");
                        collegeId = (UUID) rs.getObject("college_id");
                        departmentId = (UUID) rs.getObject("department_id");
                    } else {
                        return IdentityResolutionResult.notFound("No active user profile found for identity: " + userId);
                    }
                }
            }

            if (!"ACTIVE".equalsIgnoreCase(userStatus)) {
                return IdentityResolutionResult.ineligible("inactive_membership", "User account is " + userStatus);
            }

            if (tenantId == null) {
                return IdentityResolutionResult.ineligible("no_profile", "User profile is not associated with any tenant");
            }

            // 3. Query core.tenants
            String tenantSql = "SELECT id, name, code, status "
                    + "FROM core.tenants "
                    + "WHERE id = ? AND deleted_at IS NULL "
                    + "LIMIT 1";

            String tenantName = null;
            String tenantStatus = null;

            try (PreparedStatement ps = conn.prepareStatement(tenantSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantName = rs.getString("name");
                        tenantStatus = rs.getString("status");
                    } else {
                        return IdentityResolutionResult.ineligible("tenant_not_found", "Tenant not found for ID: " + tenantId);
                    }
                }
            }

            if (!"ACTIVE".equalsIgnoreCase(tenantStatus)) {
                return IdentityResolutionResult.ineligible("tenant_suspended", "Tenant organization is " + tenantStatus);
            }

            // 4. Query iam.role_assignments
            List<RoleAssignmentDto> assignments = new ArrayList<>();
            String rolesJoinSql = "SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to, "
                    + "r.code AS role_code, r.name AS role_name "
                    + "FROM iam.role_assignments ra "
                    + "JOIN iam.roles r ON ra.role_id = r.id "
                    + "WHERE ra.user_id = ? AND ra.tenant_id = ? "
                    + "AND ra.deleted_at IS NULL AND r.deleted_at IS NULL";

            boolean queriedJoin = false;
            try (PreparedStatement ps = conn.prepareStatement(rolesJoinSql)) {
                ps.setObject(1, userId);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assignments.add(mapRoleAssignment(rs));
                    }
                    queriedJoin = true;
                }
            } catch (SQLException e) {
                logger.debug("iam.roles join query failed, falling back to direct role_code: {}", e.getMessage());
            }

            if (!queriedJoin) {
                String directSql = "SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to, "
                        + "ra.role_code "
                        + "FROM iam.role_assignments ra "
                        + "WHERE ra.user_id = ? AND ra.tenant_id = ? "
                        + "AND ra.deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(directSql)) {
                    ps.setObject(1, userId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            assignments.add(mapRoleAssignmentDirect(rs));
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Both role assignment query variants failed: {}", e.getMessage());
                }
            }

            // Filter active by date
            LocalDate today = LocalDate.now();
            List<RoleAssignmentDto> activeAssignments = new ArrayList<>();
            for (RoleAssignmentDto a : assignments) {
                if (a.validFrom != null && a.validFrom.isAfter(today)) continue;
                if (a.validTo != null && a.validTo.isBefore(today)) continue;
                activeAssignments.add(a);
            }

            if (activeAssignments.isEmpty()) {
                return IdentityResolutionResult.ineligible("no_role_assigned", "No active business role assignment found for user in tenant");
            }

            // Preserve all assigned roles and derive effective role strictly according to canonical rules
            List<String> assignedRoles = new ArrayList<>();
            for (RoleAssignmentDto ra : activeAssignments) {
                if (ra.roleCode != null && !ra.roleCode.trim().isEmpty()) {
                    String c = ra.roleCode.trim();
                    if (!assignedRoles.contains(c)) {
                        assignedRoles.add(c);
                    }
                }
            }

            String resolvedRole = null;
            UUID matchedCollegeId = collegeId;
            UUID matchedDeptId = departmentId;

            for (RoleAssignmentDto ra : activeAssignments) {
                String code = ra.roleCode != null ? ra.roleCode.toUpperCase(Locale.ROOT) : "";
                if (isCollegeAdminRole(code)) {
                    resolvedRole = ra.roleCode != null ? ra.roleCode : "COLLEGE_ADMIN";
                    if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                    if (ra.departmentId != null) matchedDeptId = ra.departmentId;
                    break;
                } else if (isFacultyRole(code)) {
                    if (resolvedRole == null || isStudentRole(resolvedRole)) {
                        resolvedRole = ra.roleCode != null ? ra.roleCode : "FACULTY";
                        if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                        if (ra.departmentId != null) matchedDeptId = ra.departmentId;
                    }
                } else if (isStudentRole(code)) {
                    if (resolvedRole == null) {
                        resolvedRole = ra.roleCode != null ? ra.roleCode : "STUDENT";
                        if (ra.collegeId != null) matchedCollegeId = ra.collegeId;
                        if (ra.departmentId != null) matchedDeptId = ra.departmentId;
                    }
                }
            }

            if (resolvedRole == null) {
                return IdentityResolutionResult.ineligible("no_role_assigned", "No active recognized business role assignment found for user in tenant");
            }

            // Fallback college lookup by tenant if not assigned
            if (matchedCollegeId == null) {
                String collegeSql = "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(collegeSql)) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            matchedCollegeId = (UUID) rs.getObject("id");
                        }
                    }
                } catch (SQLException ignored) {}
            }

            ResolvedIdentity identity = new ResolvedIdentity(
                    userId.toString(),
                    email,
                    fullName,
                    fullName,
                    resolvedRole,
                    ResolvedIdentity.normalizeRole(resolvedRole),
                    assignedRoles,
                    tenantId.toString(),
                    tenantName,
                    tenantStatus,
                    matchedCollegeId != null ? matchedCollegeId.toString() : null,
                    matchedDeptId != null ? matchedDeptId.toString() : null,
                    userStatus,
                    false
            );

            return IdentityResolutionResult.success(identity);

        } catch (SQLException e) {
            logger.error("Authoritative identity resolution database error for user {}: {}", userId, e.getMessage(), e);
            return IdentityResolutionResult.ineligible("database_error", "Failed to resolve identity from authoritative storage");
        }
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

    private static class RoleAssignmentDto {
        UUID collegeId;
        UUID departmentId;
        LocalDate validFrom;
        LocalDate validTo;
        String roleCode;
    }

    private RoleAssignmentDto mapRoleAssignment(ResultSet rs) throws SQLException {
        RoleAssignmentDto dto = new RoleAssignmentDto();
        dto.collegeId = (UUID) rs.getObject("college_id");
        dto.departmentId = (UUID) rs.getObject("department_id");
        Date vf = rs.getDate("valid_from");
        if (vf != null) dto.validFrom = vf.toLocalDate();
        Date vt = rs.getDate("valid_to");
        if (vt != null) dto.validTo = vt.toLocalDate();
        dto.roleCode = rs.getString("role_code");
        return dto;
    }

    private RoleAssignmentDto mapRoleAssignmentDirect(ResultSet rs) throws SQLException {
        RoleAssignmentDto dto = new RoleAssignmentDto();
        dto.collegeId = (UUID) rs.getObject("college_id");
        dto.departmentId = (UUID) rs.getObject("department_id");
        Date vf = rs.getDate("valid_from");
        if (vf != null) dto.validFrom = vf.toLocalDate();
        Date vt = rs.getDate("valid_to");
        if (vt != null) dto.validTo = vt.toLocalDate();
        dto.roleCode = rs.getString("role_code");
        return dto;
    }
}
