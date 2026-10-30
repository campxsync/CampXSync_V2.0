package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Campus;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link CampusRepository} backed by {@code core.campuses}.
 * Executes under PostgreSQL RLS kernel enforcement with user session context.
 */
public class PostgresCampusRepository implements CampusRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCampusRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresCampusRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCampusRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public Campus createCampus(UserSecurityContext context, Campus campus) {
        checkContext(context);
        if (campus == null) {
            throw new MalformedPayloadException("Campus payload cannot be null");
        }
        if (campus.getCollegeId() == null || campus.getCollegeId().trim().isEmpty()) {
            throw new MalformedPayloadException("College ID is required for campus");
        }
        if (campus.getCode() == null || campus.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus code is required");
        }
        if (campus.getName() == null || campus.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus name is required");
        }

        UUID campusId = (campus.getId() != null && !campus.getId().trim().isEmpty())
                ? UUID.fromString(campus.getId().trim())
                : UUID.randomUUID();
        UUID collegeUuid = UUID.fromString(campus.getCollegeId().trim());
        String code = campus.getCode().trim().toUpperCase(Locale.ROOT);
        String name = campus.getName().trim();
        UUID addressUuid = (campus.getAddressId() != null && !campus.getAddressId().trim().isEmpty())
                ? UUID.fromString(campus.getAddressId().trim()) : null;
        String status = (campus.getStatus() != null && !campus.getStatus().trim().isEmpty())
                ? campus.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.campuses " +
                    "(id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, campusId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, collegeUuid);
                ps.setString(4, code);
                ps.setString(5, name);
                ps.setObject(6, addressUuid);
                ps.setBoolean(7, campus.isPrimary());
                ps.setString(8, status);
                ps.setObject(9, context.getUserId());
                ps.setObject(10, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Campus persisted = mapRow(rs);
                        logger.info("Persisted campus [{}] code [{}] college [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getCollegeId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("Campus code already exists in college: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist campus record");
        });
    }

    @Override
    public Optional<Campus> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID campusUuid;
        try {
            campusUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.campuses WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, campusUuid);
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
    public Optional<Campus> findByCode(UserSecurityContext context, String collegeId, String code) {
        checkContext(context);
        if (collegeId == null || code == null) return Optional.empty();

        UUID collegeUuid;
        try {
            collegeUuid = UUID.fromString(collegeId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.campuses WHERE college_id = ? AND code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, collegeUuid);
                ps.setString(2, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(3, context.getTenantId());
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
    public List<Campus> listCampuses(UserSecurityContext context, String collegeId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql;
            UUID collegeUuid = null;
            if (collegeId != null && !collegeId.trim().isEmpty()) {
                collegeUuid = UUID.fromString(collegeId.trim());
                sql = "SELECT id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                        "FROM core.campuses WHERE college_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY code ASC";
            } else {
                sql = "SELECT id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                        "FROM core.campuses WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code ASC";
            }

            List<Campus> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (collegeUuid != null) {
                    ps.setObject(1, collegeUuid);
                    ps.setObject(2, context.getTenantId());
                } else {
                    ps.setObject(1, context.getTenantId());
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    @Override
    public Campus updateCampus(UserSecurityContext context, Campus campus) {
        checkContext(context);
        if (campus == null || campus.getId() == null || campus.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus ID is required for update");
        }

        UUID campusUuid;
        try {
            campusUuid = UUID.fromString(campus.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Campus", campus.getId());
        }

        String name = campus.getName() != null && !campus.getName().trim().isEmpty() ? campus.getName().trim() : null;
        String status = campus.getStatus() != null && !campus.getStatus().trim().isEmpty()
                ? campus.getStatus().trim().toUpperCase(Locale.ROOT) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.campuses SET " +
                    "name = COALESCE(?, name), " +
                    "is_primary = ?, " +
                    "status = COALESCE(?, status), " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, college_id, code, name, address_id, is_primary, status, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setBoolean(2, campus.isPrimary());
                ps.setString(3, status);
                ps.setObject(4, context.getUserId());
                ps.setObject(5, campusUuid);
                ps.setObject(6, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("Campus", campusUuid.toString());
        });
    }

    @Override
    public void deleteCampus(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Campus ID is required for deletion");
        }

        UUID campusUuid;
        try {
            campusUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Campus", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.campuses SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, campusUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Deleted campus [{}]", campusUuid);
                }
            }
            return null;
        });
    }

    private Campus mapRow(ResultSet rs) throws SQLException {
        Campus c = new Campus();
        c.setId(rs.getString("id"));
        c.setTenantId(rs.getString("tenant_id"));
        c.setCollegeId(rs.getString("college_id"));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));

        Object addr = rs.getObject("address_id");
        if (addr != null) c.setAddressId(addr.toString());

        c.setPrimary(rs.getBoolean("is_primary"));
        c.setStatus(rs.getString("status"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) c.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) c.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) c.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) c.setUpdatedBy(ub.toString());

        c.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) c.setDeletedAt(da.getTime());

        return c;
    }
}
