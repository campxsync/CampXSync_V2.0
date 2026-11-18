package com.campx.academic.timetable.port;

import com.campx.academic.timetable.config.DatabaseConnectionManager;
import com.campx.academic.timetable.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves batch membership via existing {@code stu.students} + {@code acd.batch_students}
 * (and optional {@code stu.student_iam_links}) under authenticated RLS.
 */
public class PostgresStudentBatchMembershipPort implements StudentBatchMembershipPort {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentBatchMembershipPort.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresStudentBatchMembershipPort() {
        this(new DatabaseConnectionManager());
    }

    public PostgresStudentBatchMembershipPort(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager != null ? connectionManager : new DatabaseConnectionManager();
    }

    @Override
    public Optional<UUID> findActiveBatchIdForActor(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null || context.getUserId() == null) {
            return Optional.empty();
        }
        UUID tenantId;
        UUID actorId;
        try {
            tenantId = context.getTenantId();
            actorId = context.getUserId();
        } catch (Exception e) {
            return Optional.empty();
        }

        try {
            return context.executeInTransaction(connectionManager, conn -> {
                String sql =
                        "SELECT bs.batch_id "
                                + "FROM stu.students s "
                                + "JOIN acd.batch_students bs ON bs.student_id = s.id AND bs.tenant_id = s.tenant_id "
                                + " AND bs.deleted_at IS NULL AND UPPER(COALESCE(bs.status, '')) = 'ACTIVE' "
                                + "WHERE s.tenant_id = ? AND s.deleted_at IS NULL "
                                + "AND (s.person_id = ? OR EXISTS ("
                                + "  SELECT 1 FROM stu.student_iam_links l "
                                + "  WHERE l.student_id = s.id AND l.tenant_id = s.tenant_id "
                                + "    AND l.deleted_at IS NULL AND l.link_status = 'ACTIVE' "
                                + "    AND l.iam_user_id = ?"
                                + ")) "
                                + "ORDER BY bs.updated_at DESC NULLS LAST "
                                + "LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, actorId);
                    ps.setObject(3, actorId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of((UUID) rs.getObject("batch_id"));
                        }
                    }
                }
                return Optional.empty();
            });
        } catch (Exception e) {
            logger.warn("Unable to resolve student batch membership: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
