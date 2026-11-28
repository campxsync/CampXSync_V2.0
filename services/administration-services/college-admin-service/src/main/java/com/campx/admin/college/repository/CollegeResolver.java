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

    /**
     * Trusted college for writes (close-out).
     * Plain language: optional client collegeId must belong to the caller's institute;
     * otherwise resolve the primary college under the trusted institute from claims.
     */
    public static UUID resolveTrustedCollegeId(Connection conn, UUID tenantId, UUID clientCollegeId)
            throws SQLException {
        if (tenantId == null) {
            throw new CollegeSecurityViolationException("Tenant context required to resolve college identity");
        }
        if (clientCollegeId == null) {
            return resolveCollegeId(conn, tenantId);
        }
        String sql = "SELECT id FROM core.colleges WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, clientCollegeId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
        }
        throw new CollegeSecurityViolationException(
                "collegeId is not authorized for the caller's institute");
    }

    /**
     * Offline unit helper: foreign college vs trusted institute is refused.
     */
    public static void assertCollegeBelongsToTenant(UUID trustedTenantId, UUID collegeTenantId) {
        if (trustedTenantId == null) {
            throw new CollegeSecurityViolationException("Tenant context required to resolve college identity");
        }
        if (collegeTenantId != null && !trustedTenantId.equals(collegeTenantId)) {
            throw new CollegeSecurityViolationException(
                    "collegeId is not authorized for the caller's institute");
        }
    }
}
