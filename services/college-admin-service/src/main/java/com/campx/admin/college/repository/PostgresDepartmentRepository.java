package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code core.departments} table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresDepartmentRepository implements DepartmentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresDepartmentRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresDepartmentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresDepartmentRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Department createDepartment(UserSecurityContext context, Department dep) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (dep == null) {
            throw new CollegeMalformedPayloadException("Department payload cannot be null");
        }
        if (dep.getDepartmentCode() == null || dep.getDepartmentCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'departmentCode' is required");
        }
        if (dep.getName() == null || dep.getName().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'name' is required");
        }

        String code = dep.getDepartmentCode().trim().toUpperCase(Locale.ROOT);
        String name = dep.getName().trim();
        String status = (dep.getStatus() != null && !dep.getStatus().trim().isEmpty())
                ? dep.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";

        UUID tenantUuid = context.getTenantId();

        UUID depUuid;
        if (dep.getId() != null && !dep.getId().trim().isEmpty()) {
            try {
                depUuid = UUID.fromString(dep.getId().trim());
            } catch (IllegalArgumentException e) {
                depUuid = UUID.randomUUID();
            }
        } else {
            depUuid = UUID.randomUUID();
        }

        UUID finalDepId = depUuid;

        return context.executeInTransaction(connectionManager, conn -> {
            // Resolve college_id: if not explicitly specified, find the first active college for this tenant
            UUID collegeUuid = null;
            if (dep.getCollegeId() != null && !dep.getCollegeId().trim().isEmpty()) {
                try {
                    collegeUuid = UUID.fromString(dep.getCollegeId().trim());
                } catch (IllegalArgumentException e) {
                    throw new CollegeMalformedPayloadException("Invalid collegeId UUID format: " + dep.getCollegeId());
                }
            } else {
                try (PreparedStatement psCollege = conn.prepareStatement(
                        "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                    psCollege.setObject(1, tenantUuid);
                    try (ResultSet rs = psCollege.executeQuery()) {
                        if (rs.next()) {
                            collegeUuid = (UUID) rs.getObject("id");
                        }
                    }
                }
                if (collegeUuid == null) {
                    throw new CollegeMalformedPayloadException("No parent college exists for tenant: " + tenantUuid + ". A college must exist before creating departments.");
                }
            }

            // Check duplicate code under college
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM core.departments WHERE tenant_id = ? AND college_id = ? AND code = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, tenantUuid);
                checkPs.setObject(2, collegeUuid);
                checkPs.setString(3, code);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new CollegeResourceConflictException("Department", "departmentCode", code);
                    }
                }
            }

            // Parse head_employee_id if valid UUID and exists in hrm.employees; else null
            UUID headEmpUuid = null;
            if (dep.getHeadUserId() != null && !dep.getHeadUserId().trim().isEmpty()) {
                try {
                    UUID candidate = UUID.fromString(dep.getHeadUserId().trim());
                    // Verify candidate exists in hrm.employees
                    try (PreparedStatement empPs = conn.prepareStatement(
                            "SELECT id FROM hrm.employees WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                        empPs.setObject(1, tenantUuid);
                        empPs.setObject(2, candidate);
                        try (ResultSet rs = empPs.executeQuery()) {
                            if (rs.next()) {
                                headEmpUuid = candidate;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID placeholder (e.g. "FACULTY_HOD") - keep null in DB
                }
            }

            String insertSql = "INSERT INTO core.departments " +
                    "(id, tenant_id, college_id, code, name, head_employee_id, status, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, now(), now(), 1) " +
                    "RETURNING id, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalDepId);
                ps.setObject(2, tenantUuid);
                ps.setObject(3, collegeUuid);
                ps.setString(4, code);
                ps.setString(5, name);
                ps.setObject(6, headEmpUuid);
                ps.setString(7, status);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dep.setId(rs.getString("id"));
                        dep.setTenantId(tenantUuid.toString());
                        dep.setCollegeId(collegeUuid.toString());
                        dep.setDepartmentCode(code);
                        dep.setName(name);
                        dep.setStatus(status);
                        dep.setRowVersion(rs.getInt("row_version"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        dep.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
                        Timestamp uat = rs.getTimestamp("updated_at");
                        dep.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());
                        logger.info("Persisted department [{}] id={} in core.departments", code, dep.getId());
                        return dep;
                    }
                }
            }
            throw new RuntimeException("Failed to persist department: insert returned no rows");
        });
    }

    @Override
    public Optional<Department> findById(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        UUID depUuid;
        try {
            depUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, code, name, head_employee_id, status, created_at, updated_at, row_version " +
                    "FROM core.departments WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, depUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Department> findByCode(UserSecurityContext context, String collegeId, String code) {
        if (context == null || context.getTenantId() == null || code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }

        UUID collegeUuid = null;
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            try {
                collegeUuid = UUID.fromString(collegeId.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        UUID finalCollegeUuid = collegeUuid;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, code, name, head_employee_id, status, created_at, updated_at, row_version " +
                    "FROM core.departments WHERE code = ? AND tenant_id = ? " +
                    (finalCollegeUuid != null ? "AND college_id = ? " : "") +
                    "AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                if (finalCollegeUuid != null) {
                    ps.setObject(3, finalCollegeUuid);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<Department> listDepartments(UserSecurityContext context, String collegeId) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        UUID collegeUuid = null;
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            try {
                collegeUuid = UUID.fromString(collegeId.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        UUID finalCollegeUuid = collegeUuid;

        return context.executeInTransaction(connectionManager, conn -> {
            List<Department> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, college_id, code, name, head_employee_id, status, created_at, updated_at, row_version " +
                    "FROM core.departments WHERE tenant_id = ? AND deleted_at IS NULL " +
                    (finalCollegeUuid != null ? "AND college_id = ? " : "") +
                    "ORDER BY name ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                if (finalCollegeUuid != null) {
                    ps.setObject(2, finalCollegeUuid);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public void retireDepartment(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return;
        }

        UUID depUuid;
        try {
            depUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new CollegeResourceNotFoundException("Department", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.departments SET status = 'RETIRED', updated_at = now(), row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, depUuid);
                ps.setObject(2, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new CollegeResourceNotFoundException("Department", id);
                }
            }
            return null;
        });
    }

    @Override
    public void deleteDepartment(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return;
        }

        UUID depUuid;
        try {
            depUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM core.departments WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, depUuid);
                ps.setObject(2, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        Department dep = new Department();
        dep.setId(rs.getString("id"));
        dep.setTenantId(rs.getString("tenant_id"));
        dep.setCollegeId(rs.getString("college_id"));
        dep.setDepartmentCode(rs.getString("code"));
        dep.setName(rs.getString("name"));
        UUID head = (UUID) rs.getObject("head_employee_id");
        dep.setHeadUserId(head != null ? head.toString() : null);
        dep.setStatus(rs.getString("status"));
        dep.setRowVersion(rs.getInt("row_version"));
        Timestamp cat = rs.getTimestamp("created_at");
        dep.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
        Timestamp uat = rs.getTimestamp("updated_at");
        dep.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());
        return dep;
    }
}
