package com.campx.admin.institute.security;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code iam.roles.code} under the caller's RLS session context.
 */
public final class PostgresRoleCodeLookup implements RoleCodeLookup {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresRoleCodeLookup.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresRoleCodeLookup() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresRoleCodeLookup(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public Optional<String> findCodeByRoleId(UserSecurityContext context, String roleId) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        if (roleId == null || roleId.trim().isEmpty()) {
            return Optional.empty();
        }
        final UUID roleUuid;
        try {
            roleUuid = UUID.fromString(roleId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        try {
            return context.executeInTransaction(connectionManager, conn -> {
                String sql = "SELECT code FROM iam.roles WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, roleUuid);
                    ps.setObject(2, context.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String code = rs.getString("code");
                            if (code != null && !code.trim().isEmpty()) {
                                return Optional.of(code.trim().toUpperCase(Locale.ROOT));
                            }
                        }
                    }
                }
                return Optional.empty();
            });
        } catch (RuntimeException e) {
            logger.warn("Role code lookup failed for roleId={} tenantId={}: {}",
                    roleId, context.getTenantId(), e.getMessage());
            throw e;
        }
    }
}
