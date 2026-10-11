package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.College;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code core.colleges} table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 * Follows the established {@link PostgresTenantRepository} and {@link UserProfileRepository} pattern.
 */
public class PostgresCollegeRepository implements CollegeRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCollegeRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresCollegeRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCollegeRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public College createCollege(UserSecurityContext context, College college) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (college == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (college.getCollegeCode() == null || college.getCollegeCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'collegeCode' is required");
        }
        if (college.getName() == null || college.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'name' is required");
        }
        if (college.getInstituteId() == null || college.getInstituteId().trim().isEmpty()) {
            throw new InstituteNotFoundException("Parent Institute", college.getInstituteId());
        }

        // Validate UUID syntax for parent institute ID (prevents PostgreSQL 22P02)
        UUID parentTenantUuid;
        try {
            parentTenantUuid = UUID.fromString(college.getInstituteId().trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("Parent Institute", college.getInstituteId());
        }

        // Enforce tenant authorization: tenant admin cannot create college under another tenant
        if (!context.isPlatformScope() && !context.getTenantId().equals(parentTenantUuid)) {
            throw new UserProfileAccessDeniedException(
                    "Caller not authorized to manage colleges for tenant: " + parentTenantUuid);
        }

        String code = college.getCollegeCode().trim().toUpperCase(Locale.ROOT);
        String name = college.getName().trim();
        String legalName = college.getLegalName() != null ? college.getLegalName().trim() : null;
        String status = (college.getStatus() != null && !college.getStatus().trim().isEmpty())
                ? college.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";

        // Check constraint ck_colleges_x1: code ~ '^[A-Z0-9_-]{2,30}$'
        if (!code.matches("^[A-Z0-9_-]{2,30}$")) {
            throw new SecurityViolationException(
                    "College code must be 2-30 characters containing uppercase alphanumeric, underscore or hyphen");
        }

        UUID collegeId;
        if (college.getId() != null && !college.getId().trim().isEmpty()) {
            try {
                collegeId = UUID.fromString(college.getId().trim());
            } catch (IllegalArgumentException e) {
                collegeId = UUID.randomUUID();
            }
        } else {
            collegeId = UUID.randomUUID();
        }

        UUID finalCollegeId = collegeId;

        // Effective context targeting the parent tenant so RLS core.current_tenant_id() matches
        UserSecurityContext effectiveContext = context.withTenant(parentTenantUuid);

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            // 1. If platform-scoped caller, verify platform admin privileges in plat.platform_admins
            if (context.isPlatformScope()) {
                context.checkPlatformAdmin(conn);
            }

            // 2. Validate parent institute existence and active state in core.tenants
            String checkParentSql = "SELECT id, status FROM core.tenants WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement psParent = conn.prepareStatement(checkParentSql)) {
                psParent.setObject(1, parentTenantUuid);
                try (ResultSet rsParent = psParent.executeQuery()) {
                    if (!rsParent.next()) {
                        throw new InstituteNotFoundException("Parent Institute", parentTenantUuid.toString());
                    }
                    String parentStatus = rsParent.getString("status");
                    if (!"ACTIVE".equalsIgnoreCase(parentStatus)) {
                        throw new InvalidTenantStateException("Institute", parentStatus, "ACTIVE");
                    }
                }
            }

            // 3. Insert college into core.colleges
            String insertSql = "INSERT INTO core.colleges ("
                    + "id, tenant_id, code, name, legal_name, status, provisioning_status, row_version"
                    + ") VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 1) "
                    + "RETURNING id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalCollegeId);
                ps.setObject(2, parentTenantUuid);
                ps.setString(3, code);
                ps.setString(4, name);
                ps.setString(5, legalName);
                ps.setString(6, status);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, code, parentTenantUuid.toString());
            }
            throw new RuntimeException("Failed to insert college record");
        });
    }

    @Override
    public Optional<College> getCollegeById(UserSecurityContext context, String id) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        // Validate UUID syntax safely (prevents PostgreSQL 22P02 error)
        UUID collegeUuid;
        try {
            collegeUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at, updated_at "
                    + "FROM core.colleges WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, collegeUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, null, id);
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<College> getCollegeByCode(UserSecurityContext context, String instituteId, String collegeCode) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (instituteId == null || instituteId.trim().isEmpty() || collegeCode == null || collegeCode.trim().isEmpty()) {
            return Optional.empty();
        }

        UUID instituteUuid;
        try {
            instituteUuid = UUID.fromString(instituteId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // Tenant scope check
        if (!context.isPlatformScope() && !context.getTenantId().equals(instituteUuid)) {
            return Optional.empty();
        }

        UserSecurityContext effectiveContext = context.withTenant(instituteUuid);

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at, updated_at "
                    + "FROM core.colleges WHERE tenant_id = ? AND lower(code) = lower(?) AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, instituteUuid);
                ps.setString(2, collegeCode.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, collegeCode, instituteId);
            }
            return Optional.empty();
        });
    }

    @Override
    public List<College> listColleges(UserSecurityContext context, String instituteId) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (instituteId == null || instituteId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        UUID instituteUuid;
        try {
            instituteUuid = UUID.fromString(instituteId.trim());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }

        // Tenant scope check
        if (!context.isPlatformScope() && !context.getTenantId().equals(instituteUuid)) {
            return new ArrayList<>();
        }

        UserSecurityContext effectiveContext = context.withTenant(instituteUuid);

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at, updated_at "
                    + "FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code ASC";

            List<College> colleges = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, instituteUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        colleges.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, null, instituteId);
            }
            return colleges;
        });
    }

    @Override
    public College updateCollege(UserSecurityContext context, String id, College update, Integer expectedVersion) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            throw new InstituteNotFoundException("College", id);
        }
        if (update == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }

        // Pre-validate UUID syntax to prevent 22P02
        UUID collegeUuid;
        try {
            collegeUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("College", id);
        }

        return context.executeInTransaction(connectionManager, conn -> {
            // 1. Fetch current row state
            String checkSql = "SELECT id, tenant_id, code, row_version, status FROM core.colleges WHERE id = ? AND deleted_at IS NULL";
            UUID currentTenantId;
            String currentCode;
            int currentRowVersion;

            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, collegeUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentTenantId = (UUID) rs.getObject("tenant_id");
                        currentCode = rs.getString("code");
                        currentRowVersion = rs.getInt("row_version");
                    } else {
                        throw new InstituteNotFoundException("College", id);
                    }
                }
            }

            // Tenant scope validation
            if (!context.isPlatformScope() && !context.getTenantId().equals(currentTenantId)) {
                throw new UserProfileAccessDeniedException("Cross-tenant college modification disallowed");
            }

            // Protect immutable identity key (collegeCode)
            if (update.getCollegeCode() != null && !update.getCollegeCode().equalsIgnoreCase(currentCode)) {
                throw new SecurityViolationException("ADM01_IMMUTABLE_KEY_MODIFICATION",
                        "Modification of immutable identity key 'collegeCode' is prohibited");
            }

            // Enforce optimistic locking
            if (expectedVersion != null && currentRowVersion != expectedVersion) {
                throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                        String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, currentRowVersion));
            }

            // 2. Perform update
            StringBuilder updateSql = new StringBuilder("UPDATE core.colleges SET ");
            updateSql.append("name = coalesce(?, name), ");
            updateSql.append("legal_name = coalesce(?, legal_name), ");
            updateSql.append("status = coalesce(?, status), ");
            updateSql.append("row_version = row_version + 1, ");
            updateSql.append("updated_at = now() ");
            updateSql.append("WHERE id = ? AND deleted_at IS NULL ");
            if (expectedVersion != null) {
                updateSql.append("AND row_version = ? ");
            }
            updateSql.append("RETURNING id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at, updated_at");

            try (PreparedStatement ps = conn.prepareStatement(updateSql.toString())) {
                ps.setString(1, update.getName() != null ? update.getName().trim() : null);
                ps.setString(2, update.getLegalName() != null ? update.getLegalName().trim() : null);
                ps.setString(3, update.getStatus() != null ? update.getStatus().trim().toUpperCase(Locale.ROOT) : null);
                ps.setObject(4, collegeUuid);
                if (expectedVersion != null) {
                    ps.setInt(5, expectedVersion);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, currentCode, id);
            }

            // Disambiguate zero rows updated
            disambiguateZeroRowsUpdated(conn, collegeUuid, expectedVersion);
            throw new InstituteNotFoundException("College", id);
        });
    }

    private void disambiguateZeroRowsUpdated(Connection conn, UUID id, Integer expectedVersion) throws SQLException {
        String query = "SELECT row_version, deleted_at FROM core.colleges WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp deletedAt = rs.getTimestamp("deleted_at");
                    if (deletedAt != null) {
                        throw new InstituteNotFoundException("College", id.toString());
                    }
                    int actualVersion = rs.getInt("row_version");
                    if (expectedVersion != null && actualVersion != expectedVersion) {
                        throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                                String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, actualVersion));
                    }
                    throw new UserProfileAccessDeniedException("Caller lacks permission to update college in this scope");
                }
            }
        }
        throw new InstituteNotFoundException("College", id.toString());
    }

    private void mapSqlException(SQLException e, String code, String contextId) {
        String sqlState = e.getSQLState();
        String message = e.getMessage() != null ? e.getMessage() : "";

        logger.error("[DatabaseError] SQLState: {}, Error: {}", sqlState, message, e);

        if ("23505".equals(sqlState)) { // Unique constraint violation -> 409 Conflict
            if (message.contains("uq_colleges_tenant_id_code") || (code != null && !code.isEmpty())) {
                throw new InstituteAlreadyExistsException("College", "collegeCode", code != null ? code : "");
            }
            throw new UserProfileConflictException("ADM01_UNIQUE_VIOLATION", "A unique college resource conflict occurred");
        }

        if ("23503".equals(sqlState)) { // Foreign key constraint violation -> 404
            throw new InstituteNotFoundException("Parent Institute", contextId != null ? contextId : "");
        }

        if ("23514".equals(sqlState)) { // Check constraint violation -> 400 Bad Request
            throw new SecurityViolationException("The provided values violate college validation constraints");
        }

        if ("42501".equals(sqlState)) { // Insufficient privilege -> 403 Forbidden
            throw new UserProfileAccessDeniedException("Access denied: insufficient database permissions to complete this operation");
        }
    }

    private College mapRow(ResultSet rs) throws SQLException {
        College college = new College();
        college.setId(rs.getObject("id").toString());
        college.setInstituteId(rs.getObject("tenant_id").toString());
        college.setCollegeCode(rs.getString("code"));
        college.setName(rs.getString("name"));
        college.setLegalName(rs.getString("legal_name"));
        college.setStatus(rs.getString("status"));
        college.setVersion(rs.getInt("row_version"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        college.setCreatedAt(createdAt != null ? createdAt.getTime() : 0);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        college.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : 0);
        return college;
    }
}
