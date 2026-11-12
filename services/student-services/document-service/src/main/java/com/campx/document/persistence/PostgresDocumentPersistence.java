package com.campx.document.persistence;

import com.campx.document.Actor;
import com.campx.document.DocumentDomainService;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.DocumentLink;
import com.campx.document.config.DatabaseConnectionManager;
import com.campx.document.security.DocumentSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL JDBC implementation for doc.document_types using HikariCP.
 */
public class PostgresDocumentPersistence implements DocumentPersistence {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresDocumentPersistence.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresDocumentPersistence(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    private Actor effectiveDbActor(Actor actor) {
        if (actor == null) {
            return null;
        }
        return actor.asTenantAdmin();
    }

    @Override
    public void saveDocumentType(final Actor actor, final DocumentType type) {
        DocumentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO doc.document_types "
                        + "(id, tenant_id, code, name, category, module, default_classification, "
                        + "requires_signature, retention_policy_code, is_active, created_at, updated_at, created_by, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, type.id);
                    ps.setObject(2, type.tenantId);
                    ps.setString(3, type.code);
                    ps.setString(4, type.name);
                    ps.setString(5, type.category);
                    ps.setString(6, type.module);
                    ps.setString(7, type.defaultClassification);
                    ps.setBoolean(8, type.requiresSignature);
                    ps.setString(9, type.retentionPolicyCode);
                    ps.setBoolean(10, type.isActive);
                    ps.setTimestamp(11, Timestamp.from(type.createdAt));
                    ps.setTimestamp(12, Timestamp.from(type.updatedAt));
                    ps.setObject(13, type.createdBy);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public DocumentType findDocumentTypeByCode(final Actor actor, final String code) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<DocumentType>() {
            @Override
            public DocumentType execute(Connection conn) throws SQLException {
                String sql = "SELECT id, tenant_id, code, name, category, module, default_classification, "
                        + "requires_signature, retention_policy_code, is_active, created_at, updated_at, created_by, updated_by, row_version "
                        + "FROM doc.document_types "
                        + "WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setString(2, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapDocumentType(rs);
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public DocumentType findDocumentTypeById(final Actor actor, final UUID id) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<DocumentType>() {
            @Override
            public DocumentType execute(Connection conn) throws SQLException {
                String sql = "SELECT id, tenant_id, code, name, category, module, default_classification, "
                        + "requires_signature, retention_policy_code, is_active, created_at, updated_at, created_by, updated_by, row_version "
                        + "FROM doc.document_types "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapDocumentType(rs);
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public List<DocumentType> listDocumentTypes(final Actor actor, final String category, final Boolean activeOnly) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<DocumentType>>() {
            @Override
            public List<DocumentType> execute(Connection conn) throws SQLException {
                StringBuilder sql = new StringBuilder("SELECT id, tenant_id, code, name, category, module, default_classification, ")
                        .append("requires_signature, retention_policy_code, is_active, created_at, updated_at, created_by, updated_by, row_version ")
                        .append("FROM doc.document_types ")
                        .append("WHERE tenant_id = ? AND deleted_at IS NULL ");

                List<Object> params = new ArrayList<Object>();
                params.add(actor.getTenantId());

                if (category != null && !category.trim().isEmpty()) {
                    sql.append("AND category = ? ");
                    params.add(category.trim());
                }
                if (activeOnly != null) {
                    sql.append("AND is_active = ? ");
                    params.add(activeOnly);
                }
                sql.append("ORDER BY code ASC");

                List<DocumentType> list = new ArrayList<DocumentType>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapDocumentType(rs));
                        }
                    }
                }
                return list;
            }
        });
    }

    @Override
    public void updateDocumentType(final Actor actor, final DocumentType type) {
        DocumentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE doc.document_types SET "
                        + "name = ?, category = ?, module = ?, default_classification = ?, "
                        + "requires_signature = ?, retention_policy_code = ?, is_active = ?, "
                        + "updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, type.name);
                    ps.setString(2, type.category);
                    ps.setString(3, type.module);
                    ps.setString(4, type.defaultClassification);
                    ps.setBoolean(5, type.requiresSignature);
                    ps.setString(6, type.retentionPolicyCode);
                    ps.setBoolean(7, type.isActive);
                    ps.setTimestamp(8, Timestamp.from(type.updatedAt));
                    ps.setObject(9, actor.getUserId());
                    ps.setObject(10, actor.getTenantId());
                    ps.setObject(11, type.id);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    private DocumentType mapDocumentType(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID tenantId = (UUID) rs.getObject("tenant_id");
        String code = rs.getString("code");
        String name = rs.getString("name");
        String category = rs.getString("category");
        String module = rs.getString("module");
        String defaultClassification = rs.getString("default_classification");
        boolean requiresSignature = rs.getBoolean("requires_signature");
        String retentionPolicyCode = rs.getString("retention_policy_code");
        boolean isActive = rs.getBoolean("is_active");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        UUID createdBy = (UUID) rs.getObject("created_by");
        UUID updatedBy = (UUID) rs.getObject("updated_by");
        int rowVersion = rs.getInt("row_version");

        return new DocumentType(
                id,
                tenantId,
                code,
                name,
                category,
                module,
                defaultClassification,
                requiresSignature,
                retentionPolicyCode,
                isActive,
                createdAtTs != null ? createdAtTs.toInstant() : null,
                updatedAtTs != null ? updatedAtTs.toInstant() : null,
                createdBy,
                updatedBy,
                rowVersion
        );
    }

    @Override
    public void saveStageRequirement(final Actor actor, final StageDocumentRequirement req) {
        DocumentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO doc.stage_document_requirements "
                        + "(id, tenant_id, program_id, academic_stage, document_type_id, "
                        + "is_mandatory, min_count, max_count, submission_deadline_days, description, "
                        + "is_active, created_at, updated_at, created_by, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, req.id);
                    ps.setObject(2, req.tenantId);
                    ps.setObject(3, req.programId);
                    ps.setString(4, req.academicStage);
                    ps.setObject(5, req.documentTypeId);
                    ps.setBoolean(6, req.isMandatory);
                    ps.setInt(7, req.minCount);
                    if (req.maxCount != null) {
                        ps.setInt(8, req.maxCount);
                    } else {
                        ps.setNull(8, java.sql.Types.INTEGER);
                    }
                    if (req.submissionDeadlineDays != null) {
                        ps.setInt(9, req.submissionDeadlineDays);
                    } else {
                        ps.setNull(9, java.sql.Types.INTEGER);
                    }
                    ps.setString(10, req.description);
                    ps.setBoolean(11, req.isActive);
                    ps.setTimestamp(12, Timestamp.from(req.createdAt));
                    ps.setTimestamp(13, Timestamp.from(req.updatedAt));
                    ps.setObject(14, req.createdBy);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public StageDocumentRequirement findStageRequirementById(final Actor actor, final UUID id) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<StageDocumentRequirement>() {
            @Override
            public StageDocumentRequirement execute(Connection conn) throws SQLException {
                String sql = "SELECT r.id, r.tenant_id, r.program_id, p.code AS program_code, p.name AS program_name, "
                        + "r.academic_stage, r.document_type_id, dt.code AS document_type_code, dt.name AS document_type_name, "
                        + "r.is_mandatory, r.min_count, r.max_count, r.submission_deadline_days, r.description, "
                        + "r.is_active, r.created_at, r.updated_at, r.created_by, r.updated_by, r.row_version "
                        + "FROM doc.stage_document_requirements r "
                        + "JOIN doc.document_types dt ON dt.id = r.document_type_id AND dt.tenant_id = r.tenant_id "
                        + "LEFT JOIN acd.programs p ON p.id = r.program_id AND p.tenant_id = r.tenant_id "
                        + "WHERE r.tenant_id = ? AND r.id = ? AND r.deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapStageRequirement(rs);
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public StageDocumentRequirement findStageRequirement(final Actor actor, final UUID programId, final String stage, final UUID documentTypeId) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<StageDocumentRequirement>() {
            @Override
            public StageDocumentRequirement execute(Connection conn) throws SQLException {
                StringBuilder sql = new StringBuilder("SELECT r.id, r.tenant_id, r.program_id, p.code AS program_code, p.name AS program_name, ")
                        .append("r.academic_stage, r.document_type_id, dt.code AS document_type_code, dt.name AS document_type_name, ")
                        .append("r.is_mandatory, r.min_count, r.max_count, r.submission_deadline_days, r.description, ")
                        .append("r.is_active, r.created_at, r.updated_at, r.created_by, r.updated_by, r.row_version ")
                        .append("FROM doc.stage_document_requirements r ")
                        .append("JOIN doc.document_types dt ON dt.id = r.document_type_id AND dt.tenant_id = r.tenant_id ")
                        .append("LEFT JOIN acd.programs p ON p.id = r.program_id AND p.tenant_id = r.tenant_id ")
                        .append("WHERE r.tenant_id = ? AND r.academic_stage = ? AND r.document_type_id = ? AND r.deleted_at IS NULL ");
                if (programId != null) {
                    sql.append("AND r.program_id = ?");
                } else {
                    sql.append("AND r.program_id IS NULL");
                }
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setString(2, stage);
                    ps.setObject(3, documentTypeId);
                    if (programId != null) {
                        ps.setObject(4, programId);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapStageRequirement(rs);
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public List<StageDocumentRequirement> listStageRequirements(final Actor actor, final UUID programId, final String stage, final Boolean activeOnly) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<StageDocumentRequirement>>() {
            @Override
            public List<StageDocumentRequirement> execute(Connection conn) throws SQLException {
                StringBuilder sql = new StringBuilder("SELECT r.id, r.tenant_id, r.program_id, p.code AS program_code, p.name AS program_name, ")
                        .append("r.academic_stage, r.document_type_id, dt.code AS document_type_code, dt.name AS document_type_name, ")
                        .append("r.is_mandatory, r.min_count, r.max_count, r.submission_deadline_days, r.description, ")
                        .append("r.is_active, r.created_at, r.updated_at, r.created_by, r.updated_by, r.row_version ")
                        .append("FROM doc.stage_document_requirements r ")
                        .append("JOIN doc.document_types dt ON dt.id = r.document_type_id AND dt.tenant_id = r.tenant_id ")
                        .append("LEFT JOIN acd.programs p ON p.id = r.program_id AND p.tenant_id = r.tenant_id ")
                        .append("WHERE r.tenant_id = ? AND r.deleted_at IS NULL ");

                List<Object> params = new ArrayList<Object>();
                params.add(actor.getTenantId());

                if (programId != null) {
                    sql.append("AND r.program_id = ? ");
                    params.add(programId);
                }
                if (stage != null && !stage.trim().isEmpty()) {
                    sql.append("AND r.academic_stage = ? ");
                    params.add(stage.trim());
                }
                if (activeOnly != null) {
                    sql.append("AND r.is_active = ? ");
                    params.add(activeOnly);
                }
                sql.append("ORDER BY r.academic_stage ASC, dt.code ASC");

                List<StageDocumentRequirement> list = new ArrayList<StageDocumentRequirement>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapStageRequirement(rs));
                        }
                    }
                }
                return list;
            }
        });
    }

    @Override
    public void updateStageRequirement(final Actor actor, final StageDocumentRequirement req) {
        DocumentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE doc.stage_document_requirements SET "
                        + "is_mandatory = ?, min_count = ?, max_count = ?, submission_deadline_days = ?, "
                        + "description = ?, is_active = ?, updated_at = ?, updated_by = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, req.isMandatory);
                    ps.setInt(2, req.minCount);
                    if (req.maxCount != null) {
                        ps.setInt(3, req.maxCount);
                    } else {
                        ps.setNull(3, java.sql.Types.INTEGER);
                    }
                    if (req.submissionDeadlineDays != null) {
                        ps.setInt(4, req.submissionDeadlineDays);
                    } else {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    }
                    ps.setString(5, req.description);
                    ps.setBoolean(6, req.isActive);
                    ps.setTimestamp(7, Timestamp.from(req.updatedAt));
                    ps.setObject(8, actor.getUserId());
                    ps.setObject(9, actor.getTenantId());
                    ps.setObject(10, req.id);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void deleteStageRequirement(final Actor actor, final UUID id) {
        DocumentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE doc.stage_document_requirements SET "
                        + "deleted_at = now(), updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getUserId());
                    ps.setObject(2, actor.getTenantId());
                    ps.setObject(3, id);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    private StageDocumentRequirement mapStageRequirement(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID tenantId = (UUID) rs.getObject("tenant_id");
        UUID programId = (UUID) rs.getObject("program_id");
        String programCode = rs.getString("program_code");
        String programName = rs.getString("program_name");
        String academicStage = rs.getString("academic_stage");
        UUID documentTypeId = (UUID) rs.getObject("document_type_id");
        String documentTypeCode = rs.getString("document_type_code");
        String documentTypeName = rs.getString("document_type_name");
        boolean isMandatory = rs.getBoolean("is_mandatory");
        int minCount = rs.getInt("min_count");
        int maxVal = rs.getInt("max_count");
        Integer maxCount = rs.wasNull() ? null : Integer.valueOf(maxVal);
        int deadlineVal = rs.getInt("submission_deadline_days");
        Integer deadline = rs.wasNull() ? null : Integer.valueOf(deadlineVal);
        String description = rs.getString("description");
        boolean isActive = rs.getBoolean("is_active");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        UUID createdBy = (UUID) rs.getObject("created_by");
        UUID updatedBy = (UUID) rs.getObject("updated_by");
        int rowVersion = rs.getInt("row_version");

        return new StageDocumentRequirement(
                id,
                tenantId,
                programId,
                programCode,
                programName,
                academicStage,
                documentTypeId,
                documentTypeCode,
                documentTypeName,
                isMandatory,
                minCount,
                maxCount,
                deadline,
                description,
                isActive,
                createdAtTs != null ? createdAtTs.toInstant() : null,
                updatedAtTs != null ? updatedAtTs.toInstant() : null,
                createdBy,
                updatedBy,
                rowVersion
        );
    }

    @Override
    public void saveStudentDocument(final Actor actor, final DocumentDomainService.StudentDocument doc,
                                    final DocumentDomainService.DocumentVersion version,
                                    final DocumentDomainService.DocumentLink link) {
        DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String docSql = "INSERT INTO doc.documents "
                        + "(id, tenant_id, college_id, document_type_id, title, classification, status, owner_id, "
                        + "storage_bucket, storage_path, mime_type, size_bytes, checksum_sha256, current_version_no, "
                        + "created_at, updated_at, created_by, updated_by, expires_at, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(docSql)) {
                    ps.setObject(1, doc.id);
                    ps.setObject(2, doc.tenantId);
                    ps.setObject(3, doc.collegeId);
                    ps.setObject(4, doc.documentTypeId);
                    ps.setString(5, doc.title);
                    ps.setString(6, doc.classification);
                    ps.setString(7, doc.status);
                    ps.setObject(8, doc.ownerId);
                    ps.setString(9, doc.storageBucket);
                    ps.setString(10, doc.storagePath);
                    ps.setString(11, doc.mimeType);
                    ps.setLong(12, doc.sizeBytes);
                    ps.setString(13, doc.checksumSha256);
                    ps.setInt(14, doc.currentVersionNo);
                    ps.setTimestamp(15, Timestamp.from(doc.createdAt));
                    ps.setTimestamp(16, Timestamp.from(doc.updatedAt));
                    ps.setObject(17, doc.createdBy);
                    ps.setObject(18, doc.updatedBy);
                    ps.setTimestamp(19, doc.expiresAt != null ? Timestamp.from(doc.expiresAt) : null);
                    ps.executeUpdate();
                }

                String verSql = "INSERT INTO doc.document_versions "
                        + "(id, tenant_id, document_id, version_no, storage_path, size_bytes, checksum_sha256, change_note, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(verSql)) {
                    ps.setObject(1, version.id);
                    ps.setObject(2, version.tenantId);
                    ps.setObject(3, version.documentId);
                    ps.setInt(4, version.versionNo);
                    ps.setString(5, version.storagePath);
                    ps.setLong(6, version.sizeBytes);
                    ps.setString(7, version.checksumSha256);
                    ps.setString(8, version.changeNote);
                    ps.setTimestamp(9, Timestamp.from(version.createdAt));
                    ps.setObject(10, version.createdBy);
                    ps.executeUpdate();
                }

                String linkSql = "INSERT INTO doc.document_links "
                        + "(id, tenant_id, document_id, entity_type, entity_id, relation, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                    ps.setObject(1, link.id);
                    ps.setObject(2, link.tenantId);
                    ps.setObject(3, link.documentId);
                    ps.setString(4, link.entityType);
                    ps.setObject(5, link.entityId);
                    ps.setString(6, link.relation);
                    ps.setTimestamp(7, Timestamp.from(link.createdAt));
                    ps.setObject(8, link.createdBy);
                    ps.executeUpdate();
                }

                return null;
            }
        });
    }

    @Override
    public DocumentDomainService.StudentDocument findStudentDocumentById(final Actor actor, final UUID id) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<DocumentDomainService.StudentDocument>() {
            @Override
            public DocumentDomainService.StudentDocument execute(Connection conn) throws SQLException {
                String sql = "SELECT d.id, d.tenant_id, d.college_id, d.document_type_id, dt.code AS doc_type_code, dt.name AS doc_type_name, "
                        + "d.title, d.classification, d.status, d.owner_id, d.storage_bucket, d.storage_path, d.mime_type, d.size_bytes, "
                        + "d.checksum_sha256, d.current_version_no, d.created_at, d.updated_at, d.created_by, d.updated_by, d.row_version, "
                        + "v.result AS latest_verification_result, v.verification_code AS latest_verification_code, v.verified_at AS latest_verified_at, "
                        + "d.expires_at, dt.retention_policy_code "
                        + "FROM doc.documents d "
                        + "LEFT JOIN doc.document_types dt ON dt.id = d.document_type_id "
                        + "LEFT JOIN LATERAL ( "
                        + "    SELECT result, verification_code, verified_at "
                        + "    FROM doc.verifications "
                        + "    WHERE tenant_id = d.tenant_id AND document_id = d.id "
                        + "    ORDER BY verified_at DESC LIMIT 1 "
                        + ") v ON true "
                        + "WHERE d.tenant_id = ? AND d.id = ? AND d.deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapStudentDocument(rs);
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public List<DocumentDomainService.StudentDocument> listStudentDocuments(final Actor actor, final UUID studentId, final String status) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<DocumentDomainService.StudentDocument>>() {
            @Override
            public List<DocumentDomainService.StudentDocument> execute(Connection conn) throws SQLException {
                StringBuilder sql = new StringBuilder(
                        "SELECT d.id, d.tenant_id, d.college_id, d.document_type_id, dt.code AS doc_type_code, dt.name AS doc_type_name, "
                        + "d.title, d.classification, d.status, d.owner_id, d.storage_bucket, d.storage_path, d.mime_type, d.size_bytes, "
                        + "d.checksum_sha256, d.current_version_no, d.created_at, d.updated_at, d.created_by, d.updated_by, d.row_version, "
                        + "v.result AS latest_verification_result, v.verification_code AS latest_verification_code, v.verified_at AS latest_verified_at, "
                        + "d.expires_at, dt.retention_policy_code "
                        + "FROM doc.documents d "
                        + "LEFT JOIN doc.document_types dt ON dt.id = d.document_type_id "
                        + "LEFT JOIN LATERAL ( "
                        + "    SELECT result, verification_code, verified_at "
                        + "    FROM doc.verifications "
                        + "    WHERE tenant_id = d.tenant_id AND document_id = d.id "
                        + "    ORDER BY verified_at DESC LIMIT 1 "
                        + ") v ON true "
                        + "WHERE d.tenant_id = ? AND d.deleted_at IS NULL"
                );
                if (studentId != null) {
                    sql.append(" AND d.owner_id = ?");
                }
                if (status != null && !status.trim().isEmpty()) {
                    sql.append(" AND d.status = ?");
                }
                sql.append(" ORDER BY d.created_at DESC");

                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int idx = 1;
                    ps.setObject(idx++, actor.getTenantId());
                    if (studentId != null) {
                        ps.setObject(idx++, studentId);
                    }
                    if (status != null && !status.trim().isEmpty()) {
                        ps.setString(idx++, status.trim().toUpperCase());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        List<DocumentDomainService.StudentDocument> list = new ArrayList<DocumentDomainService.StudentDocument>();
                        while (rs.next()) {
                            list.add(mapStudentDocument(rs));
                        }
                        return list;
                    }
                }
            }
        });
    }

    @Override
    public List<DocumentDomainService.DocumentVersion> listDocumentVersions(final Actor actor, final UUID documentId) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<DocumentDomainService.DocumentVersion>>() {
            @Override
            public List<DocumentDomainService.DocumentVersion> execute(Connection conn) throws SQLException {
                String sql = "SELECT id, tenant_id, document_id, version_no, storage_path, size_bytes, checksum_sha256, change_note, created_at, created_by "
                        + "FROM doc.document_versions "
                        + "WHERE tenant_id = ? AND document_id = ? "
                        + "ORDER BY version_no ASC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, documentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<DocumentDomainService.DocumentVersion> list = new ArrayList<DocumentDomainService.DocumentVersion>();
                        while (rs.next()) {
                            list.add(mapDocumentVersion(rs));
                        }
                        return list;
                    }
                }
            }
        });
    }

    private DocumentDomainService.StudentDocument mapStudentDocument(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID tenantId = (UUID) rs.getObject("tenant_id");
        UUID collegeId = (UUID) rs.getObject("college_id");
        UUID documentTypeId = (UUID) rs.getObject("document_type_id");
        String documentTypeCode = rs.getString("doc_type_code");
        String documentTypeName = rs.getString("doc_type_name");
        String title = rs.getString("title");
        String classification = rs.getString("classification");
        String status = rs.getString("status");
        UUID ownerId = (UUID) rs.getObject("owner_id");
        String storageBucket = rs.getString("storage_bucket");
        String storagePath = rs.getString("storage_path");
        String mimeType = rs.getString("mime_type");
        long sizeBytes = rs.getLong("size_bytes");
        String checksumSha256 = rs.getString("checksum_sha256");
        int currentVersionNo = rs.getInt("current_version_no");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        UUID createdBy = (UUID) rs.getObject("created_by");
        UUID updatedBy = (UUID) rs.getObject("updated_by");
        int rowVersion = rs.getInt("row_version");

        String verResult = null;
        String verCode = null;
        Instant verAt = null;
        try {
            verResult = rs.getString("latest_verification_result");
            verCode = rs.getString("latest_verification_code");
            Timestamp verTs = rs.getTimestamp("latest_verified_at");
            if (verTs != null) {
                verAt = verTs.toInstant();
            }
        } catch (SQLException ignored) {
            // column may not exist in queries that don't join verifications
        }

        Instant expiresAt = null;
        try {
            Timestamp expTs = rs.getTimestamp("expires_at");
            if (expTs != null) {
                expiresAt = expTs.toInstant();
            }
        } catch (SQLException ignored) {
        }

        String retentionPolicyCode = null;
        try {
            retentionPolicyCode = rs.getString("retention_policy_code");
        } catch (SQLException ignored) {
        }

        return new DocumentDomainService.StudentDocument(
                id,
                tenantId,
                collegeId,
                documentTypeId,
                documentTypeCode,
                documentTypeName,
                title,
                classification,
                status,
                ownerId,
                storageBucket,
                storagePath,
                mimeType,
                sizeBytes,
                checksumSha256,
                currentVersionNo,
                createdAtTs != null ? createdAtTs.toInstant() : null,
                updatedAtTs != null ? updatedAtTs.toInstant() : null,
                createdBy,
                updatedBy,
                rowVersion,
                verResult,
                verCode,
                verAt,
                expiresAt,
                retentionPolicyCode
        );
    }

    private DocumentDomainService.DocumentVersion mapDocumentVersion(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID tenantId = (UUID) rs.getObject("tenant_id");
        UUID documentId = (UUID) rs.getObject("document_id");
        int versionNo = rs.getInt("version_no");
        String storagePath = rs.getString("storage_path");
        long sizeBytes = rs.getLong("size_bytes");
        String checksumSha256 = rs.getString("checksum_sha256");
        String changeNote = rs.getString("change_note");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        UUID createdBy = (UUID) rs.getObject("created_by");

        return new DocumentDomainService.DocumentVersion(
                id,
                tenantId,
                documentId,
                versionNo,
                storagePath,
                sizeBytes,
                checksumSha256,
                changeNote,
                createdAtTs != null ? createdAtTs.toInstant() : null,
                createdBy
        );
    }

    @Override
    public List<DocumentDomainService.VerificationQueueItem> getVerificationQueue(final Actor actor, final String statusFilter) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<DocumentDomainService.VerificationQueueItem>>() {
            @Override
            public List<DocumentDomainService.VerificationQueueItem> execute(Connection conn) throws SQLException {
                StringBuilder sql = new StringBuilder(
                        "SELECT d.id, d.tenant_id, d.college_id, d.owner_id, "
                        + "COALESCE(u.full_name, u.email, d.owner_id::text) AS student_name, "
                        + "d.document_type_id, dt.code AS doc_type_code, dt.name AS doc_type_name, "
                        + "d.title, d.classification, d.status, d.mime_type, d.size_bytes, "
                        + "d.checksum_sha256, d.storage_path, d.current_version_no, d.created_at, "
                        + "v.result AS latest_verification_result, v.verification_code AS latest_verification_code, v.verified_at AS latest_verified_at "
                        + "FROM doc.documents d "
                        + "LEFT JOIN doc.document_types dt ON dt.id = d.document_type_id "
                        + "LEFT JOIN iam.user_profiles u ON u.id = d.owner_id "
                        + "LEFT JOIN LATERAL ( "
                        + "    SELECT result, verification_code, verified_at "
                        + "    FROM doc.verifications "
                        + "    WHERE tenant_id = d.tenant_id AND document_id = d.id "
                        + "    ORDER BY verified_at DESC LIMIT 1 "
                        + ") v ON true "
                        + "WHERE d.tenant_id = ? AND d.deleted_at IS NULL"
                );

                if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                    sql.append(" AND d.status = ?");
                }
                sql.append(" ORDER BY d.created_at DESC");

                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int idx = 1;
                    ps.setObject(idx++, actor.getTenantId());
                    if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                        ps.setString(idx++, statusFilter.trim().toUpperCase());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        List<DocumentDomainService.VerificationQueueItem> list = new ArrayList<DocumentDomainService.VerificationQueueItem>();
                        while (rs.next()) {
                            UUID docId = (UUID) rs.getObject("id");
                            UUID tenantId = (UUID) rs.getObject("tenant_id");
                            UUID collegeId = (UUID) rs.getObject("college_id");
                            UUID ownerId = (UUID) rs.getObject("owner_id");
                            String studentName = rs.getString("student_name");
                            UUID docTypeId = (UUID) rs.getObject("document_type_id");
                            String docTypeCode = rs.getString("doc_type_code");
                            String docTypeName = rs.getString("doc_type_name");
                            String title = rs.getString("title");
                            String classification = rs.getString("classification");
                            String status = rs.getString("status");
                            String mimeType = rs.getString("mime_type");
                            long sizeBytes = rs.getLong("size_bytes");
                            String checksumSha256 = rs.getString("checksum_sha256");
                            String storagePath = rs.getString("storage_path");
                            int currentVersionNo = rs.getInt("current_version_no");
                            Timestamp createdAtTs = rs.getTimestamp("created_at");
                            String verResult = rs.getString("latest_verification_result");
                            String verCode = rs.getString("latest_verification_code");
                            Timestamp verAtTs = rs.getTimestamp("latest_verified_at");

                            list.add(new DocumentDomainService.VerificationQueueItem(
                                    docId,
                                    tenantId,
                                    collegeId,
                                    ownerId,
                                    studentName,
                                    docTypeId,
                                    docTypeCode,
                                    docTypeName,
                                    title,
                                    classification,
                                    status,
                                    mimeType,
                                    sizeBytes,
                                    checksumSha256,
                                    storagePath,
                                    currentVersionNo,
                                    createdAtTs != null ? createdAtTs.toInstant() : null,
                                    verResult,
                                    verCode,
                                    verAtTs != null ? verAtTs.toInstant() : null
                            ));
                        }
                        return list;
                    }
                }
            }
        });
    }

    @Override
    public void recordVerificationDecision(final Actor actor, final UUID documentId, final String newStatus, final DocumentDomainService.DocumentVerificationRecord verification) {
        DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String updateDocSql = "UPDATE doc.documents SET "
                        + "status = ?, updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(updateDocSql)) {
                    ps.setString(1, newStatus);
                    ps.setObject(2, actor.getUserId());
                    ps.setObject(3, actor.getTenantId());
                    ps.setObject(4, documentId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Failed to update document status: document " + documentId + " not found in tenant " + actor.getTenantId());
                    }
                }

                String insertVerSql = "INSERT INTO doc.verifications "
                        + "(id, tenant_id, document_id, entity_type, entity_id, verification_code, result, verified_at, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertVerSql)) {
                    ps.setObject(1, verification.id);
                    ps.setObject(2, verification.tenantId);
                    ps.setObject(3, verification.documentId);
                    ps.setString(4, verification.entityType);
                    ps.setObject(5, verification.entityId);
                    ps.setString(6, verification.verificationCode);
                    ps.setString(7, verification.result);
                    ps.setTimestamp(8, Timestamp.from(verification.verifiedAt));
                    ps.setTimestamp(9, Timestamp.from(verification.createdAt));
                    ps.setObject(10, verification.createdBy);
                    ps.executeUpdate();
                }

                return null;
            }
        });
    }

    @Override
    public DocumentDomainService.DocumentVerificationRecord findLatestVerificationByDocumentId(final Actor actor, final UUID documentId) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<DocumentDomainService.DocumentVerificationRecord>() {
            @Override
            public DocumentDomainService.DocumentVerificationRecord execute(Connection conn) throws SQLException {
                String sql = "SELECT id, tenant_id, document_id, entity_type, entity_id, verification_code, result, verified_at, created_at, created_by "
                        + "FROM doc.verifications "
                        + "WHERE tenant_id = ? AND document_id = ? "
                        + "ORDER BY verified_at DESC LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, documentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            UUID id = (UUID) rs.getObject("id");
                            UUID tenantId = (UUID) rs.getObject("tenant_id");
                            UUID docId = (UUID) rs.getObject("document_id");
                            String entityType = rs.getString("entity_type");
                            UUID entityId = (UUID) rs.getObject("entity_id");
                            String code = rs.getString("verification_code");
                            String result = rs.getString("result");
                            Timestamp verifiedAtTs = rs.getTimestamp("verified_at");
                            Timestamp createdAtTs = rs.getTimestamp("created_at");
                            UUID createdBy = (UUID) rs.getObject("created_by");

                            return new DocumentDomainService.DocumentVerificationRecord(
                                    id, tenantId, docId, entityType, entityId, code, result, "",
                                    verifiedAtTs != null ? verifiedAtTs.toInstant() : null,
                                    createdAtTs != null ? createdAtTs.toInstant() : null,
                                    createdBy
                            );
                        }
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void resubmitStudentDocument(final Actor actor, final UUID documentId, final DocumentDomainService.DocumentVersion newVersion, final String newMimeType) {
        DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                // 1. Insert new version in doc.document_versions
                String insertVerSql = "INSERT INTO doc.document_versions "
                        + "(id, tenant_id, document_id, version_no, storage_path, size_bytes, checksum_sha256, change_note, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertVerSql)) {
                    ps.setObject(1, newVersion.id);
                    ps.setObject(2, newVersion.tenantId);
                    ps.setObject(3, newVersion.documentId);
                    ps.setInt(4, newVersion.versionNo);
                    ps.setString(5, newVersion.storagePath);
                    ps.setLong(6, newVersion.sizeBytes);
                    ps.setString(7, newVersion.checksumSha256);
                    ps.setString(8, newVersion.changeNote);
                    ps.setTimestamp(9, Timestamp.from(newVersion.createdAt));
                    ps.setObject(10, newVersion.createdBy);
                    ps.executeUpdate();
                }

                // 2. Update doc.documents with new version number, reset status to SUBMITTED, update storage_path, size_bytes, checksum, mime_type, updated_at
                String updateDocSql = "UPDATE doc.documents SET "
                        + "current_version_no = ?, status = 'SUBMITTED', storage_path = ?, size_bytes = ?, "
                        + "checksum_sha256 = ?, mime_type = ?, updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(updateDocSql)) {
                    ps.setInt(1, newVersion.versionNo);
                    ps.setString(2, newVersion.storagePath);
                    ps.setLong(3, newVersion.sizeBytes);
                    ps.setString(4, newVersion.checksumSha256);
                    ps.setString(5, newMimeType);
                    ps.setObject(6, actor.getUserId());
                    ps.setObject(7, actor.getTenantId());
                    ps.setObject(8, documentId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Failed to update document: " + documentId + " not found in tenant " + actor.getTenantId());
                    }
                }

                return null;
            }
        });
    }

    @Override
    public DocumentDomainService.DocumentAccessLog recordAccessLog(final Actor actor, final UUID documentId, final String channel, final String status, final String clientIp) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<DocumentDomainService.DocumentAccessLog>() {
            @Override
            public DocumentDomainService.DocumentAccessLog execute(Connection conn) throws SQLException {
                UUID logId = UUID.randomUUID();
                Instant now = Instant.now();
                String sql = "INSERT INTO doc.document_deliveries "
                        + "(id, tenant_id, document_id, channel, status, delivered_at, client_ip, created_at, updated_at, created_by, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, logId);
                    ps.setObject(2, actor.getTenantId());
                    ps.setObject(3, documentId);
                    ps.setString(4, channel);
                    ps.setString(5, status);
                    ps.setTimestamp(6, Timestamp.from(now));
                    if (clientIp != null && !clientIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(clientIp.trim())) {
                        ps.setString(7, clientIp.trim());
                    } else {
                        ps.setNull(7, java.sql.Types.OTHER);
                    }
                    ps.setTimestamp(8, Timestamp.from(now));
                    ps.setTimestamp(9, Timestamp.from(now));
                    ps.setObject(10, actor.getUserId());
                    ps.executeUpdate();
                }
                return new DocumentDomainService.DocumentAccessLog(
                        logId,
                        actor.getTenantId(),
                        documentId,
                        channel,
                        status,
                        clientIp,
                        now,
                        now,
                        actor.getUserId()
                );
            }
        });
    }

    @Override
    public List<DocumentDomainService.DocumentAccessLog> listAccessLogs(final Actor actor, final UUID documentId) {
        return DocumentSecurityContext.fromActor(effectiveDbActor(actor)).executeInTransaction(connectionManager, new DocumentSecurityContext.TransactionCallback<List<DocumentDomainService.DocumentAccessLog>>() {
            @Override
            public List<DocumentDomainService.DocumentAccessLog> execute(Connection conn) throws SQLException {
                String sql = "SELECT id, tenant_id, document_id, channel, status, delivered_at, host(client_ip) AS client_ip, created_at, created_by "
                        + "FROM doc.document_deliveries "
                        + "WHERE tenant_id = ? AND document_id = ? AND deleted_at IS NULL "
                        + "ORDER BY delivered_at DESC";
                List<DocumentDomainService.DocumentAccessLog> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, documentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            UUID id = (UUID) rs.getObject("id");
                            UUID tenantId = (UUID) rs.getObject("tenant_id");
                            UUID docId = (UUID) rs.getObject("document_id");
                            String channel = rs.getString("channel");
                            String status = rs.getString("status");
                            String clientIp = rs.getString("client_ip");
                            Timestamp deliveredAtTs = rs.getTimestamp("delivered_at");
                            Timestamp createdAtTs = rs.getTimestamp("created_at");
                            UUID createdBy = (UUID) rs.getObject("created_by");

                            list.add(new DocumentDomainService.DocumentAccessLog(
                                    id, tenantId, docId, channel, status, clientIp,
                                    deliveredAtTs != null ? deliveredAtTs.toInstant() : null,
                                    createdAtTs != null ? createdAtTs.toInstant() : null,
                                    createdBy
                            ));
                        }
                    }
                }
                return list;
            }
        });
    }
}

