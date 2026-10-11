package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.DocumentVersion;
import com.campx.admin.college.model.CollegeModels.GovernanceDocument;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresGovernanceDocumentRepository} against PostgreSQL.
 * Verifies document registration, workflow state transitions, version history, and tenant isolation.
 */
public class PostgresGovernanceDocumentRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresGovernanceDocumentRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID testDocId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresGovernanceDocumentRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);

        testDocId = UUID.randomUUID();
    }

    @After
    public void tearDown() throws Exception {
        if (tenantId != null && testDocId != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM doc.document_versions WHERE document_id = ? AND tenant_id = ?")) {
                    ps.setObject(1, testDocId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM doc.documents WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, testDocId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveDocument() {
        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(testDocId.toString());
        doc.setTitle("Academic Integrity Policy 2026");
        doc.setClassification("INTERNAL");
        doc.setStatus("DRAFT");

        GovernanceDocument created = repository.createDocument(tenantContext, doc);
        assertNotNull(created);
        assertEquals(testDocId.toString(), created.getId());
        assertEquals("INTERNAL", created.getClassification());
        assertEquals(1, created.getCurrentVersion());

        Optional<GovernanceDocument> found = repository.findById(tenantContext, testDocId.toString());
        assertTrue(found.isPresent());
        assertEquals("Academic Integrity Policy 2026", found.get().getTitle());
        assertEquals("DRAFT", found.get().getStatus());
    }

    @Test
    public void testUpdateDocumentStatus() {
        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(testDocId.toString());
        doc.setTitle("Board Charter");
        doc.setClassification("CONFIDENTIAL");
        doc.setStatus("DRAFT");
        repository.createDocument(tenantContext, doc);

        doc.setStatus("APPROVED");
        doc.setCurrentVersion(2);
        GovernanceDocument updated = repository.updateDocument(tenantContext, doc);
        assertNotNull(updated);

        Optional<GovernanceDocument> found = repository.findById(tenantContext, testDocId.toString());
        assertTrue(found.isPresent());
        assertEquals("APPROVED", found.get().getStatus());
        assertEquals(2, found.get().getCurrentVersion());
    }

    @Test
    public void testDocumentVersioning() {
        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(testDocId.toString());
        doc.setTitle("Faculty Handbook");
        doc.setClassification("INTERNAL");
        doc.setStatus("DRAFT");
        repository.createDocument(tenantContext, doc);

        DocumentVersion v1 = new DocumentVersion();
        v1.setDocumentId(testDocId.toString());
        v1.setVersionNo(1);
        v1.setObjectRef("s3://campx-docs/faculty-v1.pdf");
        v1.setChecksum("sha256-abcdef123456");
        repository.createVersion(tenantContext, v1);

        DocumentVersion v2 = new DocumentVersion();
        v2.setDocumentId(testDocId.toString());
        v2.setVersionNo(2);
        v2.setObjectRef("s3://campx-docs/faculty-v2.pdf");
        v2.setChecksum("sha256-7890abcdef12");
        repository.createVersion(tenantContext, v2);

        List<DocumentVersion> versions = repository.listVersions(tenantContext, testDocId.toString());
        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).getVersionNo());
        assertEquals(2, versions.get(1).getVersionNo());
    }

    @Test
    public void testCrossTenantIsolation() {
        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(testDocId.toString());
        doc.setTitle("Private Board Resolution");
        doc.setClassification("CONFIDENTIAL");
        doc.setStatus("DRAFT");
        repository.createDocument(tenantContext, doc);

        Optional<GovernanceDocument> cross = repository.findById(otherTenantContext, testDocId.toString());
        assertFalse("Cross-tenant lookup must not find document", cross.isPresent());
    }
}
