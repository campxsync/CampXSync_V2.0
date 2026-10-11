package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.Institute;
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
 * PostgreSQL JDBC repository for {@code core.tenants} table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 * Follows the established {@link UserProfileRepository} pattern.
 */
public class PostgresTenantRepository implements TenantRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresTenantRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresTenantRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresTenantRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Institute createInstitute(UserSecurityContext context, Institute institute) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (institute == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (institute.getInstituteCode() == null || institute.getInstituteCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'instituteCode' is required");
        }

        String code = institute.getInstituteCode().trim();
        String legalName = institute.getLegalName() != null ? institute.getLegalName().trim() : null;
        String displayName = institute.getDisplayName() != null ? institute.getDisplayName().trim()
                : (legalName != null ? legalName : code);
        String timezone = (institute.getTimezone() != null && !institute.getTimezone().trim().isEmpty())
                ? institute.getTimezone().trim() : "Asia/Kolkata";
        String locale = (institute.getLocale() != null && !institute.getLocale().trim().isEmpty())
                ? institute.getLocale().trim() : "en-IN";
        String currency = (institute.getDefaultCurrency() != null && !institute.getDefaultCurrency().trim().isEmpty())
                ? institute.getDefaultCurrency().trim() : "INR";
        String status = (institute.getStatus() != null && !institute.getStatus().trim().isEmpty())
                ? institute.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";

        UUID tenantId;
        if (institute.getId() != null && !institute.getId().trim().isEmpty()) {
            try {
                tenantId = UUID.fromString(institute.getId().trim());
            } catch (IllegalArgumentException e) {
                tenantId = UUID.randomUUID();
            }
        } else {
            tenantId = UUID.randomUUID();
        }

        UUID finalTenantId = tenantId;

        return context.executeInTransaction(connectionManager, conn -> {
            // Verify platform administrator privilege
            context.checkPlatformAdmin(conn);

            String sql = "INSERT INTO core.tenants ("
                    + "id, code, name, legal_name, timezone, locale, currency_code, status"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "RETURNING id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalTenantId);
                ps.setString(2, code);
                ps.setString(3, displayName);
                ps.setString(4, legalName);
                ps.setString(5, timezone);
                ps.setString(6, locale);
                ps.setString(7, currency);
                ps.setString(8, status);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, code, finalTenantId.toString());
            }
            throw new RuntimeException("Failed to insert tenant record");
        });
    }

    @Override
    public Institute updateInstitute(UserSecurityContext context, String id, Institute update, Integer expectedVersion) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            throw new InstituteNotFoundException("Institute", id);
        }
        if (update == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }

        // Validate UUID format before sending to PostgreSQL (protects against PostgreSQL 22P02)
        UUID uuid;
        try {
            uuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("Institute", id);
        }

        return context.executeInTransaction(connectionManager, conn -> {
            // 1. Fetch current tenant state to verify existence, check immutable keys, and validate version
            String checkSql = "SELECT id, code, row_version, status FROM core.tenants WHERE id = ? AND deleted_at IS NULL";
            String currentCode;
            int currentRowVersion;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentCode = rs.getString("code");
                        currentRowVersion = rs.getInt("row_version");
                    } else {
                        throw new InstituteNotFoundException("Institute", id);
                    }
                }
            }

            // Protect immutable identity key
            if (update.getInstituteCode() != null && !update.getInstituteCode().equals(currentCode)) {
                throw new SecurityViolationException("ADM01_IMMUTABLE_KEY_MODIFICATION",
                        "Modification of immutable identity key 'instituteCode' is prohibited");
            }

            // Enforce optimistic locking if an expected version is specified
            if (expectedVersion != null && currentRowVersion != expectedVersion) {
                throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                        String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, currentRowVersion));
            }

            // 2. Perform update
            StringBuilder updateSql = new StringBuilder("UPDATE core.tenants SET ");
            updateSql.append("name = coalesce(?, name), ");
            updateSql.append("status = coalesce(?, status), ");
            updateSql.append("timezone = coalesce(?, timezone), ");
            updateSql.append("locale = coalesce(?, locale), ");
            updateSql.append("currency_code = coalesce(?, currency_code), ");
            updateSql.append("legal_name = coalesce(?, legal_name), ");
            updateSql.append("row_version = row_version + 1, ");
            updateSql.append("updated_at = now() ");
            updateSql.append("WHERE id = ? AND deleted_at IS NULL ");
            if (expectedVersion != null) {
                updateSql.append("AND row_version = ? ");
            }
            updateSql.append("RETURNING id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at, updated_at");

            try (PreparedStatement ps = conn.prepareStatement(updateSql.toString())) {
                ps.setString(1, update.getDisplayName());
                ps.setString(2, update.getStatus());
                ps.setString(3, update.getTimezone());
                ps.setString(4, update.getLocale());
                ps.setString(5, update.getDefaultCurrency());
                ps.setString(6, update.getLegalName());
                ps.setObject(7, uuid);
                if (expectedVersion != null) {
                    ps.setInt(8, expectedVersion);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, currentCode, id);
            }

            // If 0 rows updated, disambiguate
            disambiguateZeroRowsUpdated(conn, uuid, expectedVersion);
            throw new InstituteNotFoundException("Institute", id);
        });
    }

    @Override
    public Optional<Institute> getInstituteById(UserSecurityContext context, String id) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        // Validate UUID format to prevent 22P02
        UUID uuid;
        try {
            uuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at, updated_at "
                    + "FROM core.tenants WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, uuid);
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
    public Optional<Institute> getInstituteByCode(UserSecurityContext context, String code) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at, updated_at "
                    + "FROM core.tenants WHERE lower(code) = lower(?) AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, code, null);
            }
            return Optional.empty();
        });
    }

    @Override
    public List<Institute> listInstitutes(UserSecurityContext context) {
        return listInstitutes(context, null, 1, 100);
    }

    @Override
    public List<Institute> listInstitutes(UserSecurityContext context, String status, int page, int size) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at, updated_at "
                            + "FROM core.tenants WHERE deleted_at IS NULL"
            );
            List<Object> params = new ArrayList<>();
            if (status != null && !status.trim().isEmpty()) {
                sql.append(" AND status = ?");
                params.add(status.trim().toUpperCase(Locale.ROOT));
            }
            sql.append(" ORDER BY created_at ASC");
            int limit = (size > 0) ? size : 50;
            int p = (page > 0) ? page : 1;
            int offset = (p - 1) * limit;
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Institute> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                    return list;
                }
            } catch (SQLException e) {
                mapSqlException(e, null, null);
            }
            return new ArrayList<>();
        });
    }

    private void disambiguateZeroRowsUpdated(Connection conn, UUID id, Integer expectedVersion) throws SQLException {
        String query = "SELECT row_version, deleted_at FROM core.tenants WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp deletedAt = rs.getTimestamp("deleted_at");
                    if (deletedAt != null) {
                        throw new InstituteNotFoundException("Institute", id.toString());
                    }
                    int actualVersion = rs.getInt("row_version");
                    if (expectedVersion != null && actualVersion != expectedVersion) {
                        throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                                String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, actualVersion));
                    }
                    throw new UserProfileAccessDeniedException("Caller lacks permission to update institute in this scope");
                }
            }
        }
        throw new InstituteNotFoundException("Institute", id.toString());
    }

    private void mapSqlException(SQLException e, String code, String id) {
        String sqlState = e.getSQLState();
        String message = e.getMessage() != null ? e.getMessage() : "";

        logger.error("[DatabaseError] SQLState: {}, Error: {}", sqlState, message, e);

        if ("23505".equals(sqlState)) { // Unique constraint violation -> 409 Conflict
            if (message.contains("uq_tenants_code") || (code != null && !code.isEmpty())) {
                throw new InstituteAlreadyExistsException("Institute", "instituteCode", code != null ? code : "");
            }
            throw new UserProfileConflictException("ADM01_UNIQUE_VIOLATION", "A unique tenant resource conflict occurred");
        }

        if ("23514".equals(sqlState)) { // Check constraint violation -> 400 Bad Request
            throw new SecurityViolationException("The provided values violate tenant validation constraints");
        }

        if ("42501".equals(sqlState)) { // Insufficient privilege -> 403 Forbidden
            throw new UserProfileAccessDeniedException("Access denied: insufficient database permissions to complete this operation");
        }
    }

    private Institute mapRow(ResultSet rs) throws SQLException {
        Institute inst = new Institute();
        inst.setId(rs.getObject("id").toString());
        inst.setInstituteCode(rs.getString("code"));
        inst.setDisplayName(rs.getString("name"));
        inst.setLegalName(rs.getString("legal_name"));
        inst.setTimezone(rs.getString("timezone"));
        inst.setLocale(rs.getString("locale"));
        inst.setDefaultCurrency(rs.getString("currency_code"));
        inst.setStatus(rs.getString("status"));
        inst.setVersion(rs.getInt("row_version"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        inst.setCreatedAt(createdAt != null ? createdAt.getTime() : 0);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        inst.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : 0);
        return inst;
    }
}
