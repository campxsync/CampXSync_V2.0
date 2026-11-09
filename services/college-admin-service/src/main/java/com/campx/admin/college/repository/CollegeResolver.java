package com.campx.admin.college.repository;

import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Authoritative, tenant-scoped college identity resolver.
 * Replaces duplicated college resolution logic across repositories and guarantees
 * that unprovisioned tenants do not silently create phantom 'COL_AUTO' records.
 */
public final class CollegeResolver {

    private CollegeResolver() {}

    /**
     * Resolves the primary authorized college ID for the given tenant context.
     *
     * @param conn     active database connection executing in caller transaction
     * @param tenantId tenant identifier from caller security context
     * @return authorized college UUID
     * @throws CollegeSecurityViolationException if tenantId is null
     * @throws CollegeResourceNotFoundException  if no college is registered for the tenant
     * @throws SQLException                      on database access failure
     */
    public static UUID resolveCollegeId(Connection conn, UUID tenantId) throws SQLException {
        if (tenantId == null) {
            throw new CollegeSecurityViolationException("Tenant context required to resolve college identity");
        }
        String sql = "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
        }
        throw new CollegeResourceNotFoundException("College", "tenant:" + tenantId);
    }
}
