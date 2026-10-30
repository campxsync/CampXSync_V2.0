package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.CollegeProfile;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code core.colleges} and {@code core.college_accreditations}.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresCollegeProfileRepository implements CollegeProfileRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCollegeProfileRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresCollegeProfileRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCollegeProfileRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Optional<CollegeProfile> getProfile(UserSecurityContext context, String collegeCode) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        boolean hasCode = collegeCode != null && !collegeCode.trim().isEmpty();
        String code = hasCode ? collegeCode.trim().toUpperCase(Locale.ROOT) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = hasCode
                    ? "SELECT id, code, name, legal_name, status, row_version, updated_at FROM core.colleges WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL"
                    : "SELECT id, code, name, legal_name, status, row_version, updated_at FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                if (hasCode) {
                    ps.setString(2, code);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID collegeId = (UUID) rs.getObject("id");
                        CollegeProfile profile = new CollegeProfile();
                        profile.setCollegeCode(rs.getString("code"));
                        profile.setDisplayName(rs.getString("name"));
                        profile.setLegalName(rs.getString("legal_name") != null ? rs.getString("legal_name") : rs.getString("name"));
                        profile.setStatus(rs.getString("status"));
                        profile.setCurrentVersion(rs.getInt("row_version"));
                        Timestamp updatedAt = rs.getTimestamp("updated_at");
                        profile.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : System.currentTimeMillis());

                        // Fetch accreditations
                        List<String> accreditations = listAccreditationsForCollege(conn, context.getTenantId(), collegeId);
                        profile.setAccreditationRefs(accreditations);

                        return Optional.of(profile);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CollegeProfile saveOrUpdateProfile(UserSecurityContext context, CollegeProfile profile) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (profile == null) {
            throw new CollegeMalformedPayloadException("College profile payload cannot be null");
        }
        if (profile.getCollegeCode() == null || profile.getCollegeCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'collegeCode' is required");
        }

        String code = profile.getCollegeCode().trim().toUpperCase(Locale.ROOT);
        String displayName = profile.getDisplayName() != null ? profile.getDisplayName().trim() : code;
        String legalName = profile.getLegalName() != null ? profile.getLegalName().trim() : displayName;
        String status = (profile.getStatus() != null && !profile.getStatus().trim().isEmpty())
                ? profile.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";

        return context.executeInTransaction(connectionManager, conn -> {
            UUID collegeId = null;
            int currentVersion = 1;

            // Check if college exists
            String selectSql = "SELECT id, row_version FROM core.colleges WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        collegeId = (UUID) rs.getObject("id");
                        currentVersion = rs.getInt("row_version");
                    }
                }
            }

            if (collegeId != null) {
                // Update
                int nextVersion = currentVersion + 1;
                String updateSql = "UPDATE core.colleges SET name = ?, legal_name = ?, status = ?, " +
                        "row_version = ?, updated_at = now() WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, displayName);
                    ps.setString(2, legalName);
                    ps.setString(3, status);
                    ps.setInt(4, nextVersion);
                    ps.setObject(5, collegeId);
                    ps.setObject(6, context.getTenantId());
                    ps.executeUpdate();
                }
                profile.setCurrentVersion(nextVersion);
            } else {
                // Insert
                collegeId = UUID.randomUUID();
                String insertSql = "INSERT INTO core.colleges (id, tenant_id, code, name, legal_name, status, " +
                        "provisioning_status, created_at, updated_at, row_version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now(), 1)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, collegeId);
                    ps.setObject(2, context.getTenantId());
                    ps.setString(3, code);
                    ps.setString(4, displayName);
                    ps.setString(5, legalName);
                    ps.setString(6, status);
                    ps.executeUpdate();
                }
                profile.setCurrentVersion(1);
            }

            profile.setUpdatedAt(System.currentTimeMillis());

            // Sync accreditations if provided
            if (profile.getAccreditationRefs() != null) {
                syncAccreditationsInternal(conn, context.getTenantId(), collegeId, profile.getAccreditationRefs());
            }

            logger.info("Persisted college profile [{}] in core.colleges (version={})", code, profile.getCurrentVersion());
            return profile;
        });
    }

    @Override
    public List<String> listAccreditationRefs(UserSecurityContext context, String collegeId) {
        if (context == null || context.getTenantId() == null || collegeId == null) {
            return Collections.emptyList();
        }
        UUID cUuid;
        try {
            cUuid = UUID.fromString(collegeId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn ->
                listAccreditationsForCollege(conn, context.getTenantId(), cUuid));
    }

    @Override
    public void syncAccreditations(UserSecurityContext context, String collegeId, List<String> accreditationRefs) {
        if (context == null || context.getTenantId() == null || collegeId == null || accreditationRefs == null) {
            return;
        }
        UUID cUuid;
        try {
            cUuid = UUID.fromString(collegeId.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            syncAccreditationsInternal(conn, context.getTenantId(), cUuid, accreditationRefs);
            return null;
        });
    }

    private List<String> listAccreditationsForCollege(Connection conn, UUID tenantId, UUID collegeId) throws SQLException {
        List<String> refs = new ArrayList<>();
        String sql = "SELECT body, reference_no FROM core.college_accreditations " +
                "WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String refNo = rs.getString("reference_no");
                    String body = rs.getString("body");
                    refs.add(refNo != null && !refNo.isEmpty() ? refNo : body);
                }
            }
        }
        return refs;
    }

    private void syncAccreditationsInternal(Connection conn, UUID tenantId, UUID collegeId, List<String> accreditationRefs) throws SQLException {
        // Clear old accreditations
        String delSql = "DELETE FROM core.college_accreditations WHERE tenant_id = ? AND college_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(delSql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
            ps.executeUpdate();
        }

        // Insert new ones
        String insSql = "INSERT INTO core.college_accreditations (id, tenant_id, college_id, body, reference_no, created_at, updated_at, row_version) " +
                "VALUES (?, ?, ?, ?, ?, now(), now(), 1)";
        try (PreparedStatement ps = conn.prepareStatement(insSql)) {
            for (String ref : accreditationRefs) {
                if (ref == null || ref.trim().isEmpty()) continue;
                String body = normalizeAccreditationBody(ref.trim());
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, collegeId);
                ps.setString(4, body);
                ps.setString(5, ref.trim());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String normalizeAccreditationBody(String ref) {
        String upper = ref.toUpperCase(Locale.ROOT);
        if (upper.contains("NAAC")) return "NAAC";
        if (upper.contains("NBA")) return "NBA";
        if (upper.contains("UGC")) return "UGC";
        if (upper.contains("AICTE")) return "AICTE";
        if (upper.contains("NIRF")) return "NIRF";
        if (upper.contains("ISO")) return "ISO";
        return "OTHER";
    }
}
