package com.campx.guardian.persistence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianException;
import com.campx.guardian.GuardianDomainService.EvidenceRecord;
import com.campx.guardian.GuardianDomainService.EmergencyContact;
import com.campx.guardian.GuardianDomainService.AccessWindow;
import com.campx.guardian.GuardianDomainService.GuardianLink;
import com.campx.guardian.GuardianDomainService.HistoryEntry;
import com.campx.guardian.config.DatabaseConnectionManager;
import com.campx.guardian.security.GuardianSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                        + "(id, tenant_id, student_id, guardian_person_id, relationship, is_primary, "
                        + "can_view_academics, can_view_fees, can_pay_fees, status, created_at, updated_at, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false, false, false, 'ACTIVE', ?, ?, 1)";
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
    public void endGuardianship(final Actor actor, final GuardianLink link) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET status = 'ENDED', is_primary = false, "
                        + "can_view_academics = false, can_view_fees = false, can_pay_fees = false, "
                        + "valid_to = CURRENT_DATE, updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(link.linkedAt));
                    ps.setObject(2, actor.getUserId());
                    ps.setObject(3, link.id);
                    ps.setObject(4, link.tenantId);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        throw new GuardianException(409, "GUARDIAN_ALREADY_ENDED", "This guardian relationship is already ended");
                    }
                }
                logger.info("Ended guardian id={} tenantId={} deleted=false accessRevoked=true", link.id, link.tenantId);
                return null;
            }
        });
    }

    @Override
    public UUID openReverification(final Actor actor, final GuardianLink link, final String requestedState, final String storedStatus) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<UUID>() {
            @Override
            public UUID execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET status = ?, is_primary = false, "
                        + "can_view_academics = false, can_view_fees = false, can_pay_fees = false, "
                        + "valid_to = CASE WHEN ? = 'ENDED' THEN CURRENT_DATE ELSE valid_to END, "
                        + "updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, storedStatus);
                    ps.setString(2, storedStatus);
                    ps.setTimestamp(3, Timestamp.from(link.linkedAt));
                    ps.setObject(4, actor.getUserId());
                    ps.setObject(5, link.id);
                    ps.setObject(6, link.tenantId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(422, "RELATIONSHIP_NOT_ACTIVE", "Only an active relationship can be marked disputed, suspended, or expired");
                    }
                }
                UUID collegeId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT college_id FROM stu.students WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, link.tenantId);
                    ps.setObject(2, link.studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            collegeId = (UUID) rs.getObject("college_id");
                        }
                    }
                }
                if (collegeId == null) {
                    throw new GuardianException(422, "STUDENT_COLLEGE_REQUIRED", "The student record has no college for this re-verification");
                }
                UUID documentId = UUID.randomUUID();
                UUID linkId = UUID.randomUUID();
                Timestamp now = Timestamp.from(link.linkedAt);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO doc.documents (id, tenant_id, college_id, title, classification, status, owner_id, current_version_no, created_at, updated_at, row_version) "
                                + "VALUES (?, ?, ?, ?, 'INTERNAL', 'SUBMITTED', ?, 1, ?, ?, 1)")) {
                    ps.setObject(1, documentId);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, collegeId);
                    ps.setString(4, "Guardian re-verification: " + requestedState);
                    ps.setObject(5, actor.getUserId());
                    ps.setTimestamp(6, now);
                    ps.setTimestamp(7, now);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO doc.document_links (id, tenant_id, document_id, entity_type, entity_id, relation, created_at, created_by) "
                                + "VALUES (?, ?, ?, 'GUARDIANSHIP', ?, 'REVERIFICATION', ?, ?)")) {
                    ps.setObject(1, linkId);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, documentId);
                    ps.setObject(4, link.id);
                    ps.setTimestamp(5, now);
                    ps.setObject(6, actor.getUserId());
                    ps.executeUpdate();
                }
                logger.info("Opened guardian re-verification documentId={} requestedState={} storedStatus={} guardianshipId={}",
                        documentId, requestedState, storedStatus, link.id);
                return documentId;
            }
        });
    }

    @Override
    public boolean evidenceApproved(final Actor actor, final GuardianLink link) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Boolean>() {
            @Override
            public Boolean execute(Connection conn) throws SQLException {
                String sql = "SELECT d.status FROM doc.documents d "
                        + "JOIN doc.document_links l ON l.document_id = d.id AND l.tenant_id = d.tenant_id "
                        + "WHERE l.tenant_id = ? AND l.entity_type = 'GUARDIANSHIP' AND l.entity_id = ? AND l.relation = 'EVIDENCE' "
                        + "ORDER BY d.updated_at DESC LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, link.tenantId);
                    ps.setObject(2, link.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() && "APPROVED".equals(rs.getString("status"));
                    }
                }
            }
        });
    }

    @Override
    public void grantAccess(final Actor actor, final GuardianLink link, final boolean viewAcademics, final boolean viewFees,
                            final boolean payFees, final java.time.LocalDate validTo) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET can_view_academics = ?, can_view_fees = ?, can_pay_fees = ?, "
                        + "valid_to = COALESCE(?, valid_to), updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, viewAcademics);
                    ps.setBoolean(2, viewFees);
                    ps.setBoolean(3, payFees);
                    if (validTo == null) {
                        ps.setNull(4, java.sql.Types.DATE);
                    } else {
                        ps.setDate(4, java.sql.Date.valueOf(validTo));
                    }
                    ps.setTimestamp(5, Timestamp.from(link.linkedAt));
                    ps.setObject(6, actor.getUserId());
                    ps.setObject(7, link.id);
                    ps.setObject(8, link.tenantId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(422, "RELATIONSHIP_NOT_ACTIVE", "Access can be granted only on an active relationship");
                    }
                }
                logger.info("Granted guardian access id={} academics={} fees={} feePayment={} everything=false",
                        link.id, viewAcademics, viewFees, payFees);
                return null;
            }
        });
    }

    @Override
    public void saveAccessPeriod(final Actor actor, final GuardianLink link, final java.time.LocalDate validFrom, final java.time.LocalDate validTo) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET valid_from = ?, valid_to = ?, updated_at = ?, updated_by = ?, "
                        + "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, java.sql.Date.valueOf(validFrom));
                    ps.setDate(2, java.sql.Date.valueOf(validTo));
                    ps.setTimestamp(3, Timestamp.from(link.linkedAt));
                    ps.setObject(4, actor.getUserId());
                    ps.setObject(5, link.id);
                    ps.setObject(6, link.tenantId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(422, "RELATIONSHIP_NOT_ACTIVE", "An access period can be set only on an active relationship");
                    }
                }
                logger.info("Saved guardian access period id={} validFrom={} validTo={}", link.id, validFrom, validTo);
                return null;
            }
        });
    }

    @Override
    public AccessWindow findAccess(final Actor actor, final GuardianLink link) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<AccessWindow>() {
            @Override
            public AccessWindow execute(Connection conn) throws SQLException {
                String sql = "SELECT can_view_academics, can_view_fees, can_pay_fees, valid_from, valid_to "
                        + "FROM stu.guardianships WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, link.id);
                    ps.setObject(2, link.tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new GuardianException(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
                        }
                        java.sql.Date from = rs.getDate("valid_from");
                        java.sql.Date until = rs.getDate("valid_to");
                        return new AccessWindow(rs.getBoolean("can_view_academics"), rs.getBoolean("can_view_fees"),
                                rs.getBoolean("can_pay_fees"), from == null ? null : from.toLocalDate(), until == null ? null : until.toLocalDate());
                    }
                }
            }
        });
    }

    @Override
    public void expireElapsedAccess(final Actor actor, final GuardianLink link, final java.time.LocalDate today) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET can_view_academics = false, can_view_fees = false, can_pay_fees = false, "
                        + "updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL AND valid_to < ? "
                        + "AND (can_view_academics OR can_view_fees OR can_pay_fees)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(link.linkedAt));
                    ps.setObject(2, actor.getUserId());
                    ps.setObject(3, link.id);
                    ps.setObject(4, link.tenantId);
                    ps.setDate(5, java.sql.Date.valueOf(today));
                    ps.executeUpdate();
                }
                logger.info("Expired elapsed guardian access id={} today={} relationshipStatus=ACTIVE", link.id, today);
                return null;
            }
        });
    }

    @Override
    public void revokeAccess(final Actor actor, final GuardianLink link) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.guardianships SET can_view_academics = false, can_view_fees = false, can_pay_fees = false, "
                        + "updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL "
                        + "AND (can_view_academics OR can_view_fees OR can_pay_fees)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(link.linkedAt));
                    ps.setObject(2, actor.getUserId());
                    ps.setObject(3, link.id);
                    ps.setObject(4, link.tenantId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(409, "ACCESS_ALREADY_REVOKED", "This guardian access is already revoked");
                    }
                }
                logger.info("Revoked guardian access id={} relationshipStatus=ACTIVE sessionInvalidated=false", link.id);
                return null;
            }
        });
    }

    @Override
    public EvidenceRecord saveEvidence(final Actor actor, final GuardianLink link, final String documentType) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<EvidenceRecord>() {
            @Override
            public EvidenceRecord execute(Connection conn) throws SQLException {
                UUID collegeId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT college_id FROM stu.students WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, link.tenantId);
                    ps.setObject(2, link.studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            collegeId = (UUID) rs.getObject("college_id");
                        }
                    }
                }
                if (collegeId == null) {
                    throw new GuardianException(422, "STUDENT_COLLEGE_REQUIRED", "The student record has no college for this evidence");
                }
                UUID documentId = UUID.randomUUID();
                UUID linkId = UUID.randomUUID();
                Timestamp now = Timestamp.from(java.time.Instant.now());
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO doc.documents (id, tenant_id, college_id, title, classification, status, owner_id, current_version_no, created_at, updated_at, row_version) "
                                + "VALUES (?, ?, ?, ?, 'INTERNAL', 'SUBMITTED', ?, 1, ?, ?, 1)")) {
                    ps.setObject(1, documentId);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, collegeId);
                    ps.setString(4, "Guardian evidence: " + documentType);
                    ps.setObject(5, actor.getUserId());
                    ps.setTimestamp(6, now);
                    ps.setTimestamp(7, now);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO doc.document_links (id, tenant_id, document_id, entity_type, entity_id, relation, created_at, created_by) "
                                + "VALUES (?, ?, ?, 'GUARDIANSHIP', ?, 'EVIDENCE', ?, ?)")) {
                    ps.setObject(1, linkId);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, documentId);
                    ps.setObject(4, link.id);
                    ps.setTimestamp(5, now);
                    ps.setObject(6, actor.getUserId());
                    ps.executeUpdate();
                }
                logger.info("Queued guardian evidence documentId={} guardianshipId={} type={}", documentId, link.id, documentType);
                return new EvidenceRecord(documentId, linkId, link.id, documentType, "SUBMITTED");
            }
        });
    }

    @Override
    public UUID reviewEvidence(final Actor actor, final GuardianLink link, final String storedStatus) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<UUID>() {
            @Override
            public UUID execute(Connection conn) throws SQLException {
                String sql = "UPDATE doc.documents SET status = ?, updated_by = ?, updated_at = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND status = 'SUBMITTED' AND id = ("
                        + "SELECT l.document_id FROM doc.document_links l "
                        + "WHERE l.tenant_id = ? AND l.entity_type = 'GUARDIANSHIP' AND l.entity_id = ? AND l.relation = 'EVIDENCE' "
                        + "ORDER BY l.created_at DESC LIMIT 1) RETURNING id";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, storedStatus);
                    ps.setObject(2, actor.getUserId());
                    ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
                    ps.setObject(4, link.tenantId);
                    ps.setObject(5, link.tenantId);
                    ps.setObject(6, link.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new GuardianException(404, "EVIDENCE_NOT_PENDING", "No submitted guardian evidence is waiting for review");
                        }
                        UUID documentId = (UUID) rs.getObject("id");
                        logger.info("Reviewed guardian evidence documentId={} status={} guardianshipId={}", documentId, storedStatus, link.id);
                        return documentId;
                    }
                }
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

    @Override
    public EmergencyContact saveEmergencyContact(final Actor actor, final EmergencyContact contact) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<EmergencyContact>() {
            @Override
            public EmergencyContact execute(Connection conn) throws SQLException {
                UUID personId = studentPersonId(conn, actor, contact.studentId);
                Timestamp now = Timestamp.from(java.time.Instant.now());
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.person_emergency_contacts "
                                + "(id, tenant_id, person_id, name, relationship, phone, priority, created_at, updated_at, created_by, row_version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 1)")) {
                    ps.setObject(1, contact.id);
                    ps.setObject(2, actor.getTenantId());
                    ps.setObject(3, personId);
                    ps.setString(4, contact.name);
                    ps.setString(5, contact.relationship);
                    ps.setString(6, contact.phone);
                    ps.setTimestamp(7, now);
                    ps.setTimestamp(8, now);
                    ps.setObject(9, actor.getUserId());
                    ps.executeUpdate();
                }
                logger.info("Saved emergency contact id={} studentId={} status=ACTIVE accessGranted=false", contact.id, contact.studentId);
                return contact;
            }
        });
    }

    @Override
    public EmergencyContact updateEmergencyContact(final Actor actor, final EmergencyContact contact) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<EmergencyContact>() {
            @Override
            public EmergencyContact execute(Connection conn) throws SQLException {
                UUID personId = studentPersonId(conn, actor, contact.studentId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.person_emergency_contacts SET name = ?, relationship = ?, phone = ?, updated_at = ?, updated_by = ?, "
                                + "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND person_id = ? AND deleted_at IS NULL")) {
                    ps.setString(1, contact.name);
                    ps.setString(2, contact.relationship);
                    ps.setString(3, contact.phone);
                    ps.setTimestamp(4, Timestamp.from(java.time.Instant.now()));
                    ps.setObject(5, actor.getUserId());
                    ps.setObject(6, contact.id);
                    ps.setObject(7, actor.getTenantId());
                    ps.setObject(8, personId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(409, "CONTACT_INACTIVE", "An inactive emergency contact cannot be changed");
                    }
                }
                logger.info("Updated emergency contact id={} studentId={} accessGranted=false", contact.id, contact.studentId);
                return contact;
            }
        });
    }

    @Override
    public EmergencyContact removeEmergencyContact(final Actor actor, final EmergencyContact contact) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<EmergencyContact>() {
            @Override
            public EmergencyContact execute(Connection conn) throws SQLException {
                UUID personId = studentPersonId(conn, actor, contact.studentId);
                Timestamp now = Timestamp.from(java.time.Instant.now());
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.person_emergency_contacts SET deleted_at = ?, updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                                + "WHERE id = ? AND tenant_id = ? AND person_id = ? AND deleted_at IS NULL")) {
                    ps.setTimestamp(1, now);
                    ps.setTimestamp(2, now);
                    ps.setObject(3, actor.getUserId());
                    ps.setObject(4, contact.id);
                    ps.setObject(5, actor.getTenantId());
                    ps.setObject(6, personId);
                    if (ps.executeUpdate() != 1) {
                        throw new GuardianException(409, "CONTACT_INACTIVE", "This emergency contact is already inactive");
                    }
                }
                logger.info("Inactivated emergency contact id={} studentId={} hardDeleted=false", contact.id, contact.studentId);
                return contact;
            }
        });
    }

    @Override
    public EmergencyContact findEmergencyContact(final Actor actor, final UUID studentId, final UUID contactId) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<EmergencyContact>() {
            @Override
            public EmergencyContact execute(Connection conn) throws SQLException {
                UUID personId = studentPersonId(conn, actor, studentId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name, relationship, phone, deleted_at FROM core.person_emergency_contacts "
                                + "WHERE tenant_id = ? AND person_id = ? AND id = ?")) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, personId);
                    ps.setObject(3, contactId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return mapContact(rs, studentId);
                    }
                }
            }
        });
    }

    @Override
    public List<EmergencyContact> findEmergencyContacts(final Actor actor, final UUID studentId) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<List<EmergencyContact>>() {
            @Override
            public List<EmergencyContact> execute(Connection conn) throws SQLException {
                UUID personId = studentPersonId(conn, actor, studentId);
                List<EmergencyContact> rows = new ArrayList<EmergencyContact>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name, relationship, phone, deleted_at FROM core.person_emergency_contacts "
                                + "WHERE tenant_id = ? AND person_id = ? ORDER BY created_at")) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, personId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(mapContact(rs, studentId));
                        }
                    }
                }
                return rows;
            }
        });
    }

    private static UUID studentPersonId(Connection conn, Actor actor, UUID studentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT person_id FROM stu.students WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, actor.getTenantId());
            ps.setObject(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new GuardianException(404, "STUDENT_NOT_FOUND", "Student record was not found");
                }
                return (UUID) rs.getObject("person_id");
            }
        }
    }

    @Override
    public void appendHistory(final Actor actor, final HistoryEntry entry) {
        GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                UUID collegeId = null;
                try (PreparedStatement college = conn.prepareStatement(
                        "SELECT college_id FROM stu.students WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                    college.setObject(1, entry.studentId);
                    college.setObject(2, actor.getTenantId());
                    try (ResultSet rs = college.executeQuery()) {
                        if (rs.next()) {
                            collegeId = (UUID) rs.getObject("college_id");
                        }
                    }
                }
                ObjectNode detail = objectMapper.createObjectNode();
                detail.put("kind", entry.kind);
                detail.put("oldValue", entry.oldValue);
                detail.put("newValue", entry.newValue);
                detail.put("readOnly", true);
                String sql = "INSERT INTO audit.events (id, tenant_id, college_id, event_time, action, principal_id, principal_type, "
                        + "resource_type, resource_id, status, severity, correlation_id, detail, created_at, created_by) "
                        + "VALUES (?, ?, ?, now(), ?, ?, 'USER', 'GUARDIANSHIP', ?, 'SUCCESS', 'INFO', ?, ?::jsonb, now(), ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, entry.id);
                    ps.setObject(2, actor.getTenantId());
                    ps.setObject(3, collegeId);
                    ps.setString(4, "guardian.history." + entry.kind);
                    ps.setObject(5, entry.actorId);
                    ps.setObject(6, entry.guardianshipId);
                    ps.setString(7, actor.getCorrelationId());
                    ps.setString(8, detail.toString());
                    ps.setObject(9, entry.actorId);
                    ps.executeUpdate();
                }
                logger.info("Recorded guardian history id={} kind={} guardianshipId={}", entry.id, entry.kind, entry.guardianshipId);
                return null;
            }
        });
    }

    @Override
    public List<HistoryEntry> findHistory(final Actor actor, final UUID studentId) {
        return GuardianSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new GuardianSecurityContext.TransactionCallback<List<HistoryEntry>>() {
            @Override
            public List<HistoryEntry> execute(Connection conn) throws SQLException {
                List<HistoryEntry> rows = new ArrayList<HistoryEntry>();
                String documents = "SELECT d.id, d.status, d.title, d.updated_at, d.updated_by, d.owner_id, l.entity_id, l.relation "
                        + "FROM doc.document_links l "
                        + "JOIN doc.documents d ON d.id = l.document_id AND d.tenant_id = l.tenant_id "
                        + "JOIN stu.guardianships g ON g.id = l.entity_id AND g.tenant_id = l.tenant_id "
                        + "WHERE l.tenant_id = ? AND l.entity_type = 'GUARDIANSHIP' AND g.student_id = ? "
                        + "AND g.deleted_at IS NULL AND d.deleted_at IS NULL ORDER BY d.updated_at";
                try (PreparedStatement ps = conn.prepareStatement(documents)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(documentHistory(rs, studentId));
                        }
                    }
                }
                String events = "SELECT e.id, e.event_time, e.principal_id, e.resource_id, e.detail "
                        + "FROM audit.events e WHERE e.tenant_id = ? AND e.action LIKE 'guardian.history.%' "
                        + "AND e.resource_id IN (SELECT g.id FROM stu.guardianships g WHERE g.tenant_id = ? AND g.student_id = ? AND g.deleted_at IS NULL) "
                        + "ORDER BY e.event_time";
                try (PreparedStatement ps = conn.prepareStatement(events)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, actor.getTenantId());
                    ps.setObject(3, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(eventHistory(rs, studentId));
                        }
                    }
                }
                return rows;
            }
        });
    }

    private HistoryEntry documentHistory(ResultSet rs, UUID studentId) throws SQLException {
        String relation = rs.getString("relation");
        String status = rs.getString("status");
        String kind = "VERIFICATION";
        String oldValue = "SUBMITTED";
        String newValue = status;
        if ("REVERIFICATION".equals(relation) || "SUBMITTED".equals(status)) {
            kind = "CLAIM";
            oldValue = "";
            newValue = rs.getString("title");
        }
        UUID actorId = (UUID) rs.getObject("updated_by");
        if (actorId == null) {
            actorId = (UUID) rs.getObject("owner_id");
        }
        Timestamp when = rs.getTimestamp("updated_at");
        return new HistoryEntry((UUID) rs.getObject("id"), studentId, (UUID) rs.getObject("entity_id"), kind,
                actorId, when == null ? "" : when.toInstant().toString(), oldValue, newValue, true);
    }

    private HistoryEntry eventHistory(ResultSet rs, UUID studentId) throws SQLException {
        String kind = "";
        String oldValue = "";
        String newValue = "";
        String detail = rs.getString("detail");
        if (detail != null && !detail.isEmpty()) {
            try {
                JsonNode node = objectMapper.readTree(detail);
                kind = node.path("kind").asText("");
                oldValue = node.path("oldValue").asText("");
                newValue = node.path("newValue").asText("");
            } catch (RuntimeException ex) {
                kind = "";
            } catch (java.io.IOException ex) {
                kind = "";
            }
        }
        Timestamp when = rs.getTimestamp("event_time");
        return new HistoryEntry((UUID) rs.getObject("id"), studentId, (UUID) rs.getObject("resource_id"), kind,
                (UUID) rs.getObject("principal_id"), when == null ? "" : when.toInstant().toString(), oldValue, newValue, true);
    }

    private static EmergencyContact mapContact(ResultSet rs, UUID studentId) throws SQLException {
        boolean inactive = rs.getTimestamp("deleted_at") != null;
        return new EmergencyContact(
                (UUID) rs.getObject("id"),
                studentId,
                rs.getString("name"),
                rs.getString("relationship"),
                rs.getString("phone"),
                inactive ? "INACTIVE" : "ACTIVE");
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
