package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.DocumentVersion;
import com.campx.admin.college.model.CollegeModels.GovernanceDocument;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code doc.documents} and {@code doc.document_versions}.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresGovernanceDocumentRepository implements GovernanceDocumentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresGovernanceDocumentRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresGovernanceDocumentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresGovernanceDocumentRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public GovernanceDocument createDocument(UserSecurityContext context, GovernanceDocument doc) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (doc == null) {
            throw new CollegeMalformedPayloadException("Document payload cannot be null");
        }
        if (doc.getTitle() == null || doc.getTitle().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'title' is required");
        }

        String title = doc.getTitle().trim();
        String classification = (doc.getClassification() != null && !doc.getClassification().trim().isEmpty())
                ? normalizeClassification(doc.getClassification().trim()) : "INTERNAL";
        String status = (doc.getStatus() != null && !doc.getStatus().trim().isEmpty())
                ? normalizeStatus(doc.getStatus().trim()) : "DRAFT";

        return context.executeInTransaction(connectionManager, conn -> {
            UUID docId = (doc.getId() != null && !doc.getId().trim().isEmpty())
                    ? UUID.fromString(doc.getId().trim()) : UUID.randomUUID();
            UUID collegeId = CollegeResolver.resolveCollegeId(conn, context.getTenantId());

            String sql = "INSERT INTO doc.documents (id, tenant_id, college_id, title, classification, status, " +
                    "current_version_no, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 1, now(), now(), 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, docId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, collegeId);
                ps.setString(4, title);
                ps.setString(5, classification);
                ps.setString(6, status);
                ps.executeUpdate();
            }

            doc.setId(docId.toString());
            doc.setCurrentVersion(1);
            doc.setStatus(status);
            doc.setClassification(classification);

            logger.info("Persisted governance document [{}] in doc.documents", docId);
            return doc;
        });
    }

    @Override
    public Optional<GovernanceDocument> findById(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null) {
            return Optional.empty();
        }
        UUID docUuid;
        try {
            docUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, title, classification, status, current_version_no, row_version " +
                    "FROM doc.documents WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, docUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        GovernanceDocument doc = new GovernanceDocument();
                        doc.setId(rs.getString("id"));
                        doc.setTitle(rs.getString("title"));
                        doc.setClassification(rs.getString("classification"));
                        doc.setStatus(rs.getString("status"));
                        doc.setCurrentVersion(rs.getInt("current_version_no"));
                        return Optional.of(doc);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<GovernanceDocument> listDocuments(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<GovernanceDocument> list = new ArrayList<>();
            String sql = "SELECT id, title, classification, status, current_version_no, row_version " +
                    "FROM doc.documents WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        GovernanceDocument doc = new GovernanceDocument();
                        doc.setId(rs.getString("id"));
                        doc.setTitle(rs.getString("title"));
                        doc.setClassification(rs.getString("classification"));
                        doc.setStatus(rs.getString("status"));
                        doc.setCurrentVersion(rs.getInt("current_version_no"));
                        list.add(doc);
                    }
                }
            }
            return list;
        });
    }

    @Override
    public GovernanceDocument updateDocument(UserSecurityContext context, GovernanceDocument doc) {
        if (context == null || context.getTenantId() == null || doc == null || doc.getId() == null) {
            throw new CollegeMalformedPayloadException("Document and id cannot be null");
        }
        UUID docUuid;
        try {
            docUuid = UUID.fromString(doc.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new CollegeMalformedPayloadException("Invalid document UUID format: " + doc.getId());
        }

        String status = normalizeStatus(doc.getStatus());

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE doc.documents SET status = ?, current_version_no = ?, updated_at = now(), " +
                    "row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setInt(2, doc.getCurrentVersion());
                ps.setObject(3, docUuid);
                ps.setObject(4, context.getTenantId());
                ps.executeUpdate();
            }
            logger.info("Updated document [{}] status to {}", docUuid, status);
            return doc;
        });
    }

    @Override
    public DocumentVersion createVersion(UserSecurityContext context, DocumentVersion version) {
        if (context == null || context.getTenantId() == null || version == null || version.getDocumentId() == null) {
            throw new CollegeMalformedPayloadException("Document version and documentId cannot be null");
        }
        UUID docUuid;
        try {
            docUuid = UUID.fromString(version.getDocumentId().trim());
        } catch (IllegalArgumentException e) {
            throw new CollegeMalformedPayloadException("Invalid documentId UUID format");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            UUID verId = (version.getId() != null && !version.getId().trim().isEmpty())
                    ? UUID.fromString(version.getId().trim()) : UUID.randomUUID();

            String storagePath = version.getObjectRef() != null ? version.getObjectRef() : "/docs/" + docUuid + "/v" + version.getVersionNo();
            String checksum = version.getChecksum() != null ? version.getChecksum() : "SHA256-" + UUID.randomUUID().toString().replace("-", "");

            String sql = "INSERT INTO doc.document_versions (id, tenant_id, document_id, version_no, storage_path, " +
                    "checksum_sha256, created_at) VALUES (?, ?, ?, ?, ?, ?, now())";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, verId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, docUuid);
                ps.setInt(4, version.getVersionNo() > 0 ? version.getVersionNo() : 1);
                ps.setString(5, storagePath);
                ps.setString(6, checksum);
                ps.executeUpdate();
            }

            // Update document current_version_no
            String updateDocSql = "UPDATE doc.documents SET current_version_no = ?, updated_at = now() " +
                    "WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateDocSql)) {
                ps.setInt(1, version.getVersionNo() > 0 ? version.getVersionNo() : 1);
                ps.setObject(2, docUuid);
                ps.setObject(3, context.getTenantId());
                ps.executeUpdate();
            }

            version.setId(verId.toString());
            logger.info("Persisted document version [{}] v{} in doc.document_versions", docUuid, version.getVersionNo());
            return version;
        });
    }

    @Override
    public List<DocumentVersion> listVersions(UserSecurityContext context, String documentId) {
        if (context == null || context.getTenantId() == null || documentId == null) {
            return Collections.emptyList();
        }
        UUID docUuid;
        try {
            docUuid = UUID.fromString(documentId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<DocumentVersion> list = new ArrayList<>();
            String sql = "SELECT id, document_id, version_no, storage_path, checksum_sha256, created_at " +
                    "FROM doc.document_versions WHERE tenant_id = ? AND document_id = ? ORDER BY version_no ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, docUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DocumentVersion v = new DocumentVersion();
                        v.setId(rs.getString("id"));
                        v.setDocumentId(rs.getString("document_id"));
                        v.setVersionNo(rs.getInt("version_no"));
                        v.setObjectRef(rs.getString("storage_path"));
                        v.setChecksum(rs.getString("checksum_sha256"));
                        list.add(v);
                    }
                }
            }
            return list;
        });
    }

    private String normalizeClassification(String val) {
        if (val == null) return "INTERNAL";
        String u = val.toUpperCase(Locale.ROOT);
        if (u.equals("PUBLIC") || u.equals("INTERNAL") || u.equals("CONFIDENTIAL") || u.equals("RESTRICTED")) {
            return u;
        }
        return "INTERNAL";
    }

    private String normalizeStatus(String val) {
        if (val == null) return "DRAFT";
        String u = val.toUpperCase(Locale.ROOT);
        if (u.equals("DRAFT") || u.equals("SUBMITTED") || u.equals("APPROVED") ||
                u.equals("REJECTED") || u.equals("PUBLISHED") || u.equals("ARCHIVED") || u.equals("CANCELLED")) {
            return u;
        }
        return "DRAFT";
    }
}
