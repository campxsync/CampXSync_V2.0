package com.campx.lifecycle.persistence;

import com.campx.lifecycle.Actor;
import com.campx.lifecycle.LifecycleDomainService.GraduationRecord;
import com.campx.lifecycle.LifecycleDomainService.HistoryEntry;
import com.campx.lifecycle.LifecycleDomainService.LifecycleRequest;
import com.campx.lifecycle.LifecycleDomainService.LifecycleStatus;
import com.campx.lifecycle.LifecycleDomainService.TransitionRule;
import com.campx.lifecycle.LifecycleException;
import com.campx.lifecycle.config.DatabaseConnectionManager;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.lifecycle.security.LifecycleSecurityContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link LifecyclePersistence}.
 * Each call runs inside the caller's tenant row-level security context.
 * Missing rows raise {@link LifecycleException}. SQL failures are logged by
 * {@link LifecycleSecurityContext} and rolled back.
 */
public class PostgresLifecyclePersistence implements LifecyclePersistence {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresLifecyclePersistence.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresLifecyclePersistence() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresLifecyclePersistence(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void saveStatus(Actor actor, final LifecycleStatus status) {
        LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, student_id) DO UPDATE SET "
                        + "status = EXCLUDED.status, effective_from = EXCLUDED.effective_from, updated_at = EXCLUDED.updated_at";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, status.tenantId);
                    ps.setObject(2, status.studentId);
                    ps.setString(3, status.status);
                    ps.setTimestamp(4, Timestamp.from(status.effectiveFrom));
                    ps.setTimestamp(5, Timestamp.from(status.updatedAt));
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void appendHistory(Actor actor, final HistoryEntry entry) {
        LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.lifecycle_history "
                        + "(tenant_id, id, student_id, status, action, actor_role, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, entry.tenantId);
                    ps.setObject(2, entry.id);
                    ps.setObject(3, entry.studentId);
                    ps.setString(4, entry.status);
                    ps.setString(5, entry.action);
                    ps.setString(6, entry.actorRole);
                    ps.setTimestamp(7, Timestamp.from(entry.occurredAt));
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveRequest(Actor actor, final LifecycleRequest request) {
        LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.lifecycle_requests "
                        + "(tenant_id, id, student_id, request_type, status, reason, effective_date, decided_by, decided_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "status = EXCLUDED.status, decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at, "
                        + "reason = EXCLUDED.reason, effective_date = EXCLUDED.effective_date";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, request.tenantId);
                    ps.setObject(2, request.id);
                    ps.setObject(3, request.studentId);
                    ps.setString(4, request.requestType);
                    ps.setString(5, request.status);
                    ps.setString(6, request.reason);
                    if (request.effectiveDate == null) {
                        ps.setDate(7, null);
                    } else {
                        ps.setDate(7, Date.valueOf(request.effectiveDate));
                    }
                    if (request.decidedBy == null) {
                        ps.setObject(8, null);
                    } else {
                        ps.setObject(8, request.decidedBy);
                    }
                    if (request.decidedAt == null) {
                        ps.setTimestamp(9, null);
                    } else {
                        ps.setTimestamp(9, Timestamp.from(request.decidedAt));
                    }
                    ps.setTimestamp(10, Timestamp.from(request.createdAt));
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveRule(Actor actor, final TransitionRule rule) {
        LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.lifecycle_transition_rules "
                        + "(tenant_id, id, from_status, to_status, requires_approval, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, from_status, to_status) DO UPDATE SET "
                        + "requires_approval = EXCLUDED.requires_approval, active = EXCLUDED.active";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, rule.tenantId);
                    ps.setObject(2, rule.id);
                    ps.setString(3, rule.fromStatus);
                    ps.setString(4, rule.toStatus);
                    ps.setBoolean(5, rule.requiresApproval);
                    ps.setBoolean(6, rule.active);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveGraduation(Actor actor, final GraduationRecord record) {
        LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.graduation_records "
                        + "(tenant_id, id, student_id, program_id, graduated_on, notes, recorded_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET graduated_on = EXCLUDED.graduated_on, notes = EXCLUDED.notes";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, record.tenantId);
                    ps.setObject(2, record.id);
                    ps.setObject(3, record.studentId);
                    ps.setObject(4, record.programId);
                    ps.setDate(5, Date.valueOf(record.graduatedOn));
                    ps.setString(6, record.notes);
                    ps.setTimestamp(7, Timestamp.from(record.recordedAt));
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public LifecycleStatus findStatus(Actor actor, final UUID studentId) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<LifecycleStatus>() {
            @Override
            public LifecycleStatus execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, student_id, status, effective_from, updated_at "
                        + "FROM stu.lifecycle_status WHERE tenant_id = ? AND student_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("Lifecycle status was not found for student {}", studentId);
                            throw new LifecycleException(404, "STATUS_NOT_FOUND", "Lifecycle status was not found");
                        }
                        return new LifecycleStatus(
                                (UUID) rs.getObject("tenant_id"),
                                (UUID) rs.getObject("student_id"),
                                rs.getString("status"),
                                rs.getTimestamp("effective_from").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                }
            }
        });
    }

    @Override
    public List<HistoryEntry> findHistory(Actor actor, final UUID studentId) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<List<HistoryEntry>>() {
            @Override
            public List<HistoryEntry> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, student_id, status, action, actor_role, occurred_at "
                        + "FROM stu.lifecycle_history WHERE tenant_id = ? AND student_id = ? ORDER BY occurred_at";
                List<HistoryEntry> rows = new ArrayList<HistoryEntry>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new HistoryEntry(
                                    (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("student_id"),
                                    rs.getString("status"),
                                    rs.getString("action"),
                                    rs.getString("actor_role"),
                                    rs.getTimestamp("occurred_at").toInstant()));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public LifecycleRequest findRequest(Actor actor, final UUID requestId) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<LifecycleRequest>() {
            @Override
            public LifecycleRequest execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, student_id, request_type, status, reason, effective_date, decided_by, decided_at, created_at "
                        + "FROM stu.lifecycle_requests WHERE tenant_id = ? AND id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("Lifecycle request was not found id={}", requestId);
                            throw new LifecycleException(404, "REQUEST_NOT_FOUND", "Lifecycle request was not found");
                        }
                        return mapRequest(rs);
                    }
                }
            }
        });
    }

    @Override
    public List<LifecycleStatus> findAllStatuses(Actor actor) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<List<LifecycleStatus>>() {
            @Override
            public List<LifecycleStatus> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, student_id, status, effective_from, updated_at FROM stu.lifecycle_status WHERE tenant_id = ?";
                List<LifecycleStatus> rows = new ArrayList<LifecycleStatus>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new LifecycleStatus(
                                    (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("student_id"),
                                    rs.getString("status"),
                                    rs.getTimestamp("effective_from").toInstant(),
                                    rs.getTimestamp("updated_at").toInstant()));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<LifecycleRequest> findPendingRequests(Actor actor) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<List<LifecycleRequest>>() {
            @Override
            public List<LifecycleRequest> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, student_id, request_type, status, reason, effective_date, decided_by, decided_at, created_at "
                        + "FROM stu.lifecycle_requests WHERE tenant_id = ? AND status = 'PENDING' ORDER BY created_at";
                List<LifecycleRequest> rows = new ArrayList<LifecycleRequest>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(mapRequest(rs));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<LifecycleRequest> findRequestsByStudent(Actor actor, final UUID studentId) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<List<LifecycleRequest>>() {
            @Override
            public List<LifecycleRequest> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, student_id, request_type, status, reason, effective_date, decided_by, decided_at, created_at "
                        + "FROM stu.lifecycle_requests WHERE tenant_id = ? AND student_id = ? ORDER BY created_at DESC";
                List<LifecycleRequest> rows = new ArrayList<LifecycleRequest>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(mapRequest(rs));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<UUID> findAdviseesByAdvisor(Actor actor, final UUID advisorUserId) {
        return LifecycleSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new LifecycleSecurityContext.TransactionCallback<List<UUID>>() {
            @Override
            public List<UUID> execute(Connection conn) throws SQLException {
                String sql = "SELECT DISTINCT student_id FROM stu.lifecycle_history "
                        + "WHERE tenant_id = ? AND action = ? ORDER BY student_id";
                List<UUID> rows = new ArrayList<UUID>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setString(2, "advisor:" + advisorUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add((UUID) rs.getObject("student_id"));
                        }
                    }
                }
                return rows;
            }
        });
    }

    private LifecycleRequest mapRequest(ResultSet rs) throws SQLException {
        Instant decidedAt = rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant();
        Date effective = rs.getDate("effective_date");
        return new LifecycleRequest(
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("student_id"),
                rs.getString("request_type"),
                rs.getString("status"),
                rs.getString("reason"),
                effective == null ? null : effective.toLocalDate(),
                (UUID) rs.getObject("decided_by"),
                decidedAt,
                rs.getTimestamp("created_at").toInstant());
    }
}
