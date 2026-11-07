package com.campx.guardian.persistence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianException;
import com.campx.guardian.GuardianDomainService.GuardianLink;
import com.campx.guardian.config.DatabaseConnectionManager;
import com.campx.guardian.security.GuardianSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes the guardian person and the {@code stu.guardianships} row in one transaction.
 */
public class PostgresGuardianPersistence implements GuardianPersistence {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresGuardianPersistence.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresGuardianPersistence(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void saveGuardian(final Actor actor, final GuardianLink link) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String personSql = "INSERT INTO core.persons "
                        + "(id, tenant_id, first_name, last_name, display_name, primary_email, primary_phone, status, created_at, updated_at, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(personSql)) {
                    String display = (link.firstName + " " + link.lastName).trim();
                    ps.setObject(1, link.guardianPersonId);
                    ps.setObject(2, link.tenantId);
                    ps.setString(3, link.firstName);
                    ps.setString(4, link.lastName.isEmpty() ? null : link.lastName);
                    ps.setString(5, display);
                    ps.setString(6, link.email.isEmpty() ? null : link.email);
                    ps.setString(7, link.phone.isEmpty() ? null : link.phone);
                    ps.setTimestamp(8, Timestamp.from(link.linkedAt));
                    ps.setTimestamp(9, Timestamp.from(link.linkedAt));
                    ps.executeUpdate();
                }
                String linkSql = "INSERT INTO stu.guardianships "
                        + "(id, tenant_id, student_id, guardian_person_id, relationship, is_primary, status, created_at, updated_at, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                    ps.setObject(1, link.id);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, link.studentId);
                    ps.setObject(4, link.guardianPersonId);
                    ps.setString(5, link.relationship);
                    ps.setBoolean(6, link.primary);
                    ps.setTimestamp(7, Timestamp.from(link.linkedAt));
                    ps.setTimestamp(8, Timestamp.from(link.linkedAt));
                    ps.executeUpdate();
                }
                logger.info("Persisted guardian id={} studentId={} tenantId={}", link.id, link.studentId, link.tenantId);
                return null;
            }
        });
    }

    @Override
    public void updateGuardian(final Actor actor, final GuardianLink link) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET relationship = ?, is_primary = ?, updated_at = ?, "
                        + "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, link.relationship);
                    ps.setBoolean(2, link.primary);
                    ps.setTimestamp(3, Timestamp.from(link.linkedAt));
                    ps.setObject(4, link.id);
                    ps.setObject(5, link.tenantId);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        throw new GuardianException(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
                    }
                }
                logger.info("Updated guardian id={} relationship={} tenantId={}", link.id, link.relationship, link.tenantId);
                return null;
            }
        });
    }

    @Override
    public GuardianLink findGuardian(final Actor actor, final UUID guardianshipId) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<GuardianLink>() {
            @Override
            public GuardianLink execute(Connection conn) throws SQLException {
                String sql = "SELECT g.id, g.tenant_id, g.student_id, g.guardian_person_id, g.relationship, g.is_primary, g.status, g.created_at, "
                        + "p.first_name, p.last_name, p.primary_email, p.primary_phone "
                        + "FROM stu.guardianships g JOIN core.persons p ON p.id = g.guardian_person_id AND p.tenant_id = g.tenant_id "
                        + "WHERE g.tenant_id = ? AND g.id = ? AND g.deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, guardianshipId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return mapLink(rs);
                    }
                }
            }
        });
    }

    @Override
    public List<GuardianLink> findGuardians(final Actor actor, final UUID studentId) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<List<GuardianLink>>() {
            @Override
            public List<GuardianLink> execute(Connection conn) throws SQLException {
                String sql = "SELECT g.id, g.tenant_id, g.student_id, g.guardian_person_id, g.relationship, g.is_primary, g.status, g.created_at, "
                        + "p.first_name, p.last_name, p.primary_email, p.primary_phone "
                        + "FROM stu.guardianships g JOIN core.persons p ON p.id = g.guardian_person_id AND p.tenant_id = g.tenant_id "
                        + "WHERE g.tenant_id = ? AND g.student_id = ? AND g.deleted_at IS NULL ORDER BY g.created_at";
                List<GuardianLink> rows = new ArrayList<GuardianLink>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(mapLink(rs));
                        }
                    }
                }
                return rows;
            }
        });
    }

    private static GuardianLink mapLink(ResultSet rs) throws SQLException {
        return new GuardianLink(
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("student_id"),
                (UUID) rs.getObject("guardian_person_id"),
                rs.getString("first_name"),
                rs.getString("last_name") == null ? "" : rs.getString("last_name"),
                rs.getString("relationship"),
                rs.getString("primary_email") == null ? "" : rs.getString("primary_email"),
                rs.getString("primary_phone") == null ? "" : rs.getString("primary_phone"),
                rs.getBoolean("is_primary"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
