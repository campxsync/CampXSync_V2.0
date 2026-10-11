package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code acd.programs} table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresProgramRepository implements ProgramRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresProgramRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresProgramRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresProgramRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Program createProgram(UserSecurityContext context, Program prog) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (prog == null) {
            throw new CollegeMalformedPayloadException("Program payload cannot be null");
        }
        if (prog.getProgramCode() == null || prog.getProgramCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'programCode' is required");
        }
        if (prog.getName() == null || prog.getName().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'name' is required");
        }
        if (prog.getDepartmentId() == null || prog.getDepartmentId().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'departmentId' is required");
        }
        if (prog.getDurationYears() <= 0) {
            throw new CollegeMalformedPayloadException("Field 'durationYears' must be positive");
        }

        String code = prog.getProgramCode().trim().toUpperCase(Locale.ROOT);
        String name = prog.getName().trim();
        String level = (prog.getLevel() != null && !prog.getLevel().trim().isEmpty())
                ? prog.getLevel().trim().toUpperCase(Locale.ROOT) : "UG";
        String status = (prog.getStatus() != null && !prog.getStatus().trim().isEmpty())
                ? prog.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        int durationYears = prog.getDurationYears();
        int totalTerms = prog.getTotalTerms() != null ? prog.getTotalTerms() : (durationYears * 2);

        UUID tenantUuid = context.getTenantId();
        UUID userUuid = context.getUserId();

        UUID depUuid;
        try {
            depUuid = UUID.fromString(prog.getDepartmentId().trim());
        } catch (IllegalArgumentException e) {
            throw new CollegeMalformedPayloadException("Invalid departmentId UUID format: " + prog.getDepartmentId());
        }

        UUID progUuid;
        if (prog.getId() != null && !prog.getId().trim().isEmpty()) {
            try {
                progUuid = UUID.fromString(prog.getId().trim());
            } catch (IllegalArgumentException e) {
                progUuid = UUID.randomUUID();
            }
        } else {
            progUuid = UUID.randomUUID();
        }

        UUID finalProgId = progUuid;

        return context.executeInTransaction(connectionManager, conn -> {
            // 1. Resolve college_id from parent department in core.departments
            UUID collegeUuid = null;
            if (prog.getCollegeId() != null && !prog.getCollegeId().trim().isEmpty()) {
                try {
                    collegeUuid = UUID.fromString(prog.getCollegeId().trim());
                } catch (IllegalArgumentException ignored) {}
            }

            if (collegeUuid == null) {
                try (PreparedStatement depPs = conn.prepareStatement(
                        "SELECT college_id, status FROM core.departments WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                    depPs.setObject(1, tenantUuid);
                    depPs.setObject(2, depUuid);
                    try (ResultSet rs = depPs.executeQuery()) {
                        if (rs.next()) {
                            collegeUuid = (UUID) rs.getObject("college_id");
                        } else {
                            throw new CollegeResourceNotFoundException("Department", depUuid.toString());
                        }
                    }
                }
            }

            // 2. Check duplicate code under tenant
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM acd.programs WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, tenantUuid);
                checkPs.setString(2, code);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new CollegeResourceConflictException("Program", "programCode", code);
                    }
                }
            }

            // 3. Insert under RLS
            String insertSql = "INSERT INTO acd.programs ("
                    + "id, tenant_id, college_id, department_id, code, name, level, "
                    + "duration_years, total_terms, published, status, created_by, row_version"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1) "
                    + "RETURNING id, tenant_id, college_id, department_id, code, name, level, "
                    + "duration_years, total_terms, published, status, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalProgId);
                ps.setObject(2, tenantUuid);
                ps.setObject(3, collegeUuid);
                ps.setObject(4, depUuid);
                ps.setString(5, code);
                ps.setString(6, name);
                ps.setString(7, level);
                ps.setBigDecimal(8, BigDecimal.valueOf(durationYears));
                ps.setInt(9, totalTerms);
                ps.setBoolean(10, prog.isPublished());
                ps.setString(11, status);
                ps.setObject(12, userUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToProgram(rs);
                    }
                }
            }
            throw new RuntimeException("Failed to persist academic program: no row returned from RETURNING clause");
        });
    }

    @Override
    public Program getProgramById(UserSecurityContext context, String programId) {
        if (context == null || context.getTenantId() == null || programId == null) {
            return null;
        }

        UUID progUuid;
        try {
            progUuid = UUID.fromString(programId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, department_id, code, name, level, "
                    + "duration_years, total_terms, published, status, created_at, updated_at, row_version "
                    + "FROM acd.programs WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantUuid);
                ps.setObject(2, progUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToProgram(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Program> listPrograms(UserSecurityContext context, String collegeId, String departmentId) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            StringBuilder sb = new StringBuilder(
                    "SELECT id, tenant_id, college_id, department_id, code, name, level, "
                    + "duration_years, total_terms, published, status, created_at, updated_at, row_version "
                    + "FROM acd.programs WHERE tenant_id = ? AND deleted_at IS NULL"
            );

            List<Object> params = new ArrayList<>();
            params.add(tenantUuid);

            if (collegeId != null && !collegeId.trim().isEmpty()) {
                try {
                    params.add(UUID.fromString(collegeId.trim()));
                    sb.append(" AND college_id = ?");
                } catch (IllegalArgumentException ignored) {}
            }

            if (departmentId != null && !departmentId.trim().isEmpty()) {
                try {
                    params.add(UUID.fromString(departmentId.trim()));
                    sb.append(" AND department_id = ?");
                } catch (IllegalArgumentException ignored) {}
            }

            sb.append(" ORDER BY code ASC");

            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }

                List<Program> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRowToProgram(rs));
                    }
                }
                return results;
            }
        });
    }

    @Override
    public Program updateProgram(UserSecurityContext context, Program prog) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (prog == null || prog.getId() == null) {
            throw new CollegeMalformedPayloadException("Program ID is required for update");
        }

        UUID progUuid;
        try {
            progUuid = UUID.fromString(prog.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new CollegeMalformedPayloadException("Invalid program ID format: " + prog.getId());
        }

        UUID tenantUuid = context.getTenantId();
        UUID userUuid = context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            String updateSql = "UPDATE acd.programs SET "
                    + "name = ?, duration_years = ?, published = ?, updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                    + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, college_id, department_id, code, name, level, "
                    + "duration_years, total_terms, published, status, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, prog.getName());
                ps.setBigDecimal(2, BigDecimal.valueOf(prog.getDurationYears()));
                ps.setBoolean(3, prog.isPublished());
                ps.setObject(4, userUuid);
                ps.setObject(5, tenantUuid);
                ps.setObject(6, progUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToProgram(rs);
                    }
                }
            }
            throw new CollegeResourceNotFoundException("Program", prog.getId());
        });
    }

    @Override
    public void deleteProgram(UserSecurityContext context, String programId) {
        if (context == null || context.getTenantId() == null || programId == null) {
            return;
        }

        UUID progUuid;
        try {
            progUuid = UUID.fromString(programId.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        UUID tenantUuid = context.getTenantId();

        context.executeInTransaction(connectionManager, conn -> {
            String deleteSql = "UPDATE acd.programs SET deleted_at = now(), status = 'DISCONTINUED' "
                    + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setObject(1, tenantUuid);
                ps.setObject(2, progUuid);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Program mapRowToProgram(ResultSet rs) throws SQLException {
        Program p = new Program();
        p.setId(rs.getString("id"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setCollegeId(rs.getString("college_id"));
        p.setDepartmentId(rs.getString("department_id"));
        p.setProgramCode(rs.getString("code"));
        p.setName(rs.getString("name"));
        p.setLevel(rs.getString("level"));
        BigDecimal dur = rs.getBigDecimal("duration_years");
        p.setDurationYears(dur != null ? dur.intValue() : 4);
        int terms = rs.getInt("total_terms");
        p.setTotalTerms(rs.wasNull() ? null : terms);
        p.setPublished(rs.getBoolean("published"));
        p.setStatus(rs.getString("status"));
        Timestamp ct = rs.getTimestamp("created_at");
        p.setCreatedAt(ct != null ? ct.getTime() : System.currentTimeMillis());
        Timestamp ut = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ut != null ? ut.getTime() : p.getCreatedAt());
        p.setRowVersion(rs.getInt("row_version"));
        return p;
    }
}
