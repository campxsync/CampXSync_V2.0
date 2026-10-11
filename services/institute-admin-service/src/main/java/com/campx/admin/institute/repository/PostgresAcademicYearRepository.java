package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AcademicYear;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link AcademicYearRepository} backed by {@code core.academic_years}.
 * Executes under PostgreSQL RLS kernel enforcement with user session context.
 */
public class PostgresAcademicYearRepository implements AcademicYearRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAcademicYearRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresAcademicYearRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAcademicYearRepository(DatabaseConnectionManager connectionManager) {
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
    public AcademicYear createAcademicYear(UserSecurityContext context, AcademicYear year) {
        checkContext(context);
        if (year == null) {
            throw new MalformedPayloadException("AcademicYear payload cannot be null");
        }
        if (year.getCode() == null || year.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear code is required");
        }
        if (year.getEndDate() <= year.getStartDate()) {
            throw new MalformedPayloadException("endDate must be strictly greater than startDate");
        }

        UUID yearId = (year.getId() != null && !year.getId().trim().isEmpty())
                ? UUID.fromString(year.getId().trim())
                : UUID.randomUUID();
        String code = year.getCode().trim().toUpperCase(Locale.ROOT);

        return context.executeInTransaction(connectionManager, conn -> {
            if (year.isCurrent()) {
                String unsetSql = "UPDATE core.academic_years SET is_current = false, updated_at = now(), updated_by = ? " +
                        "WHERE tenant_id = ? AND is_current = true AND deleted_at IS NULL";
                try (PreparedStatement unset = conn.prepareStatement(unsetSql)) {
                    unset.setObject(1, context.getUserId());
                    unset.setObject(2, context.getTenantId());
                    unset.executeUpdate();
                }
            }

            String sql = "INSERT INTO core.academic_years " +
                    "(id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, yearId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, code);
                ps.setDate(4, new java.sql.Date(year.getStartDate()));
                ps.setDate(5, new java.sql.Date(year.getEndDate()));
                ps.setBoolean(6, year.isCurrent());
                ps.setObject(7, context.getUserId());
                ps.setObject(8, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        AcademicYear persisted = mapRow(rs);
                        logger.info("Persisted academic year [{}] code [{}] in tenant [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getTenantId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("AcademicYear code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist academic year record");
        });
    }

    @Override
    public Optional<AcademicYear> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UUID yearUuid;
        try {
            yearUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.academic_years WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, yearUuid);
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
    public Optional<AcademicYear> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.academic_years WHERE code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
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
    public Optional<AcademicYear> findCurrent(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.academic_years WHERE tenant_id = ? AND is_current = true AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
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
    public List<AcademicYear> listAcademicYears(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.academic_years WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY start_date DESC";
            List<AcademicYear> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
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
    public AcademicYear updateAcademicYear(UserSecurityContext context, AcademicYear year) {
        checkContext(context);
        if (year == null || year.getId() == null || year.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required for update");
        }

        UUID yearUuid;
        try {
            yearUuid = UUID.fromString(year.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("AcademicYear", year.getId());
        }

        String code = year.getCode() != null && !year.getCode().trim().isEmpty()
                ? year.getCode().trim().toUpperCase(Locale.ROOT) : null;
        boolean hasStartDate = year.getStartDate() > 0;
        boolean hasEndDate = year.getEndDate() > 0;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.academic_years SET " +
                    "code = COALESCE(?, code), " +
                    "start_date = CASE WHEN ? THEN ? ELSE start_date END, " +
                    "end_date = CASE WHEN ? THEN ? ELSE end_date END, " +
                    "updated_by = ?, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, code, start_date, end_date, is_current, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setBoolean(2, hasStartDate);
                if (hasStartDate) {
                    ps.setDate(3, new java.sql.Date(year.getStartDate()));
                } else {
                    ps.setNull(3, Types.DATE);
                }
                ps.setBoolean(4, hasEndDate);
                if (hasEndDate) {
                    ps.setDate(5, new java.sql.Date(year.getEndDate()));
                } else {
                    ps.setNull(5, Types.DATE);
                }
                ps.setObject(6, context.getUserId());
                ps.setObject(7, yearUuid);
                ps.setObject(8, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("AcademicYear", yearUuid.toString());
        });
    }

    @Override
    public void setCurrent(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required");
        }

        UUID yearUuid;
        try {
            yearUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("AcademicYear", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String unsetSql = "UPDATE core.academic_years SET is_current = false, updated_at = now(), updated_by = ? " +
                    "WHERE tenant_id = ? AND is_current = true AND deleted_at IS NULL";
            try (PreparedStatement unset = conn.prepareStatement(unsetSql)) {
                unset.setObject(1, context.getUserId());
                unset.setObject(2, context.getTenantId());
                unset.executeUpdate();
            }

            String setSql = "UPDATE core.academic_years SET is_current = true, updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement set = conn.prepareStatement(setSql)) {
                set.setObject(1, context.getUserId());
                set.setObject(2, yearUuid);
                set.setObject(3, context.getTenantId());
                int rows = set.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("AcademicYear", id);
                }
            }
            return null;
        });
    }

    @Override
    public void deleteAcademicYear(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required for deletion");
        }

        UUID yearUuid;
        try {
            yearUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("AcademicYear", id);
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.academic_years SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, yearUuid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    logger.info("Deleted academic year [{}]", yearUuid);
                }
            }
            return null;
        });
    }

    private AcademicYear mapRow(ResultSet rs) throws SQLException {
        AcademicYear y = new AcademicYear();
        y.setId(rs.getString("id"));
        y.setTenantId(rs.getString("tenant_id"));
        y.setCode(rs.getString("code"));

        java.sql.Date sd = rs.getDate("start_date");
        if (sd != null) y.setStartDate(sd.getTime());

        java.sql.Date ed = rs.getDate("end_date");
        if (ed != null) y.setEndDate(ed.getTime());

        y.setCurrent(rs.getBoolean("is_current"));

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) y.setCreatedAt(ca.getTime());

        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) y.setUpdatedAt(ua.getTime());

        Object cb = rs.getObject("created_by");
        if (cb != null) y.setCreatedBy(cb.toString());

        Object ub = rs.getObject("updated_by");
        if (ub != null) y.setUpdatedBy(ub.toString());

        y.setRowVersion(rs.getInt("row_version"));

        Timestamp da = rs.getTimestamp("deleted_at");
        if (da != null) y.setDeletedAt(da.getTime());

        return y;
    }
}
