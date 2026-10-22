package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.Gender;
import com.campx.student.model.PersonEntity;
import com.campx.student.security.UserSecurityContext;

import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code core.persons}.
 * Enforces transaction-scoped tenant claims and least-privilege role switching.
 */
public class PostgresPersonRepository implements PersonRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresPersonRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresPersonRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresPersonRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) throw new IllegalArgumentException("connectionManager cannot be null");
        this.connectionManager = connectionManager;
    }

    @Override
    public PersonEntity save(UserSecurityContext context, PersonEntity person) {
        assertContext(context);
        if (person == null) throw new IllegalArgumentException("person cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (person.getId() == null) {
                return insertPerson(conn, context, person);
            } else {
                return updatePerson(conn, context, person);
            }
        });
    }

    @Override
    public Optional<PersonEntity> findById(UserSecurityContext context, UUID id) {
        assertContext(context);
        if (id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, first_name, middle_name, last_name, display_name, " +
                    "date_of_birth, gender, primary_email, primary_phone, status, created_at, updated_at, " +
                    "created_by, updated_by, row_version, deleted_at " +
                    "FROM core.persons WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<PersonEntity> findByEmail(UserSecurityContext context, String email) {
        assertContext(context);
        if (email == null || email.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, first_name, middle_name, last_name, display_name, " +
                    "date_of_birth, gender, primary_email, primary_phone, status, created_at, updated_at, " +
                    "created_by, updated_by, row_version, deleted_at " +
                    "FROM core.persons WHERE tenant_id = ? AND LOWER(primary_email) = LOWER(?) AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean existsByEmail(UserSecurityContext context, String email) {
        assertContext(context);
        if (email == null || email.trim().isEmpty()) return false;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT 1 FROM core.persons WHERE tenant_id = ? AND LOWER(primary_email) = LOWER(?) AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private PersonEntity insertPerson(Connection conn, UserSecurityContext context, PersonEntity person) throws SQLException {
        UUID id = UUID.randomUUID();
        person.setId(id);
        person.setTenantId(context.getTenantId());

        String sql = "INSERT INTO core.persons (id, tenant_id, first_name, middle_name, last_name, display_name, " +
                "date_of_birth, gender, primary_email, primary_phone, status, created_by, updated_by, row_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) " +
                "RETURNING created_at, updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, context.getTenantId());
            ps.setString(3, person.getFirstName());
            ps.setString(4, person.getMiddleName());
            ps.setString(5, person.getLastName());
            ps.setString(6, person.getDisplayName());
            if (person.getDateOfBirth() != null) {
                ps.setDate(7, java.sql.Date.valueOf(person.getDateOfBirth()));
            } else {
                ps.setNull(7, Types.DATE);
            }
            ps.setString(8, person.getGender() != null ? person.getGender().name() : "U");
            ps.setString(9, person.getPrimaryEmail());
            ps.setString(10, person.getPrimaryPhone());
            ps.setString(11, person.getStatus() != null ? person.getStatus() : "ACTIVE");
            ps.setObject(12, context.getUserId());
            ps.setObject(13, context.getUserId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp cat = rs.getTimestamp("created_at");
                    Timestamp uat = rs.getTimestamp("updated_at");
                    person.setCreatedAt(cat != null ? cat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    person.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    person.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return person;
    }

    private PersonEntity updatePerson(Connection conn, UserSecurityContext context, PersonEntity person) throws SQLException {
        String sql = "UPDATE core.persons SET first_name = ?, middle_name = ?, last_name = ?, display_name = ?, " +
                "date_of_birth = ?, gender = ?, primary_email = ?, primary_phone = ?, updated_by = ?, updated_at = now(), " +
                "row_version = row_version + 1 " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                "RETURNING updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, person.getFirstName());
            ps.setString(2, person.getMiddleName());
            ps.setString(3, person.getLastName());
            ps.setString(4, person.getDisplayName());
            if (person.getDateOfBirth() != null) {
                ps.setDate(5, java.sql.Date.valueOf(person.getDateOfBirth()));
            } else {
                ps.setNull(5, Types.DATE);
            }
            ps.setString(6, person.getGender() != null ? person.getGender().name() : "U");
            ps.setString(7, person.getPrimaryEmail());
            ps.setString(8, person.getPrimaryPhone());
            ps.setObject(9, context.getUserId());
            ps.setObject(10, person.getId());
            ps.setObject(11, context.getTenantId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp uat = rs.getTimestamp("updated_at");
                    person.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    person.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return person;
    }

    private PersonEntity mapResultSet(ResultSet rs) throws SQLException {
        PersonEntity p = new PersonEntity();
        p.setId((UUID) rs.getObject("id"));
        p.setTenantId((UUID) rs.getObject("tenant_id"));
        p.setFirstName(rs.getString("first_name"));
        p.setMiddleName(rs.getString("middle_name"));
        p.setLastName(rs.getString("last_name"));
        p.setDisplayName(rs.getString("display_name"));

        java.sql.Date dob = rs.getDate("date_of_birth");
        if (dob != null) {
            p.setDateOfBirth(dob.toLocalDate());
        }

        String genderStr = rs.getString("gender");
        if (genderStr != null) {
            try { p.setGender(Gender.valueOf(genderStr)); } catch (IllegalArgumentException ignored) {}
        }

        p.setPrimaryEmail(rs.getString("primary_email"));
        p.setPrimaryPhone(rs.getString("primary_phone"));
        p.setStatus(rs.getString("status"));

        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) p.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) p.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));

        p.setRowVersion(rs.getInt("row_version"));
        return p;
    }

    private void assertContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new StudentSecurityViolationException("Authenticated tenant context is strictly required");
        }
    }
}
