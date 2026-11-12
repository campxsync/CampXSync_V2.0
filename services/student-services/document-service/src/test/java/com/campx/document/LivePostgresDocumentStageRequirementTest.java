package com.campx.document;

import com.campx.document.DocumentDomainService.DefineStageRequirementRequest;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.config.DatabaseConfig;
import com.campx.document.config.DatabaseConnectionManager;
import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.PostgresDocumentPersistence;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live PostgreSQL JDBC persistence and RLS isolation test for STM-06-US-002 against dev ref azulbaxszsqxymqmlwfe.
 */
public class LivePostgresDocumentStageRequirementTest {

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private final UUID adminId = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private final UUID registrarId = UUID.fromString("80a8ffae-b5ad-4c61-a401-16f220893df5");
    private final UUID studentId = UUID.fromString("90a8ffae-b5ad-4c61-a401-16f220893df6");

    private final UUID docTypeTranscript = UUID.fromString("ef74df32-e775-4aa3-a108-52114969e4f9");
    private final UUID docTypeNationalId = UUID.fromString("fdf88717-6db1-45f8-8e27-8e085304a14b");
    private final UUID docTypeFeeReceipt = UUID.fromString("9337f376-bdc4-4c6f-813f-d60e5eac373a");
    private final UUID programCse = UUID.fromString("a0000000-0000-0000-0000-000000000040");

    private final Actor adminTenantA = new Actor(tenantA, adminId, "COLLEGE_ADMIN", "collegeadmin.dev@campx.edu");
    private final Actor studentTenantA = new Actor(tenantA, studentId, "STUDENT", "student.dev@campx.edu");
    private final Actor adminTenantB = new Actor(tenantB, UUID.randomUUID(), "COLLEGE_ADMIN", "admin.b@campx.edu");

    private DocumentDomainService service;

    @Before
    public void setUp() {
        DatabaseConfig config = new DatabaseConfig();
        Assume.assumeTrue("Database must be configured with dev ref credentials", config.isConfigured());

        DatabaseConnectionManager connectionManager = DatabaseConnectionManager.getInstance();
        connectionManager.initialize(config);
        PostgresDocumentPersistence persistence = new PostgresDocumentPersistence(connectionManager);
        DocumentIntelligenceService intelligenceService = new DocumentIntelligenceService();
        service = new DocumentDomainService(persistence, intelligenceService);
    }

    @Test
    public void verifyLiveStageRequirementsPersistenceAndRlsIsolation() {
        // 1. Clean up any previous test rules if existing
        List<StageDocumentRequirement> existingList = service.listStageRequirements(adminTenantA, null, null, null);
        for (StageDocumentRequirement r : existingList) {
            try {
                service.deleteStageRequirement(adminTenantA, r.id);
            } catch (Exception ignored) {}
        }

        // 2. Define Rule 1: Mandatory Official Transcript for Admission (Institution-wide)
        DefineStageRequirementRequest req1 = new DefineStageRequirementRequest();
        req1.academicStage = "ADMISSION";
        req1.documentTypeId = docTypeTranscript;
        req1.isMandatory = true;
        req1.minCount = 1;
        req1.maxCount = 1;
        req1.submissionDeadlineDays = 15;
        req1.description = "Official attested transcript required prior to matriculation";

        StageDocumentRequirement rule1 = service.defineStageRequirement(adminTenantA, req1);
        assertNotNull("Rule 1 ID must be generated", rule1.id);
        assertEquals(tenantA, rule1.tenantId);
        assertEquals("ADMISSION", rule1.academicStage);
        assertEquals("DOC-TRANSCRIPT", rule1.documentTypeCode);
        assertTrue(rule1.isMandatory);
        assertEquals(1, rule1.minCount);

        // 3. Define Rule 2: Mandatory National ID for Enrollment under B.Tech CSE
        DefineStageRequirementRequest req2 = new DefineStageRequirementRequest();
        req2.academicStage = "ENROLLMENT";
        req2.documentTypeId = docTypeNationalId;
        req2.programId = programCse;
        req2.isMandatory = true;
        req2.minCount = 1;
        req2.maxCount = 2;
        req2.submissionDeadlineDays = 30;
        req2.description = "Government issued ID verified for program enrollment";

        StageDocumentRequirement rule2 = service.defineStageRequirement(adminTenantA, req2);
        assertNotNull("Rule 2 ID must be generated", rule2.id);
        assertEquals(tenantA, rule2.tenantId);
        assertEquals(programCse, rule2.programId);
        assertEquals("ENROLLMENT", rule2.academicStage);
        assertEquals("DOC-NATIONAL-ID", rule2.documentTypeCode);
        assertTrue(rule2.isMandatory);

        // 4. Define Rule 3: Optional Fee Receipt for Annual Progression
        DefineStageRequirementRequest req3 = new DefineStageRequirementRequest();
        req3.academicStage = "ANNUAL_PROGRESSION";
        req3.documentTypeId = docTypeFeeReceipt;
        req3.isMandatory = false;
        req3.minCount = 0;
        req3.maxCount = 1;
        req3.description = "Optional fee clearance proof for progression audit";

        StageDocumentRequirement rule3 = service.defineStageRequirement(adminTenantA, req3);
        assertNotNull(rule3.id);
        assertFalse(rule3.isMandatory);

        // 5. Committed Read-Back via findStageRequirementById
        StageDocumentRequirement readBack1 = service.getStageRequirement(adminTenantA, rule1.id);
        assertNotNull("Live read-back of Rule 1 from PostgreSQL must succeed", readBack1);
        assertEquals("ADMISSION", readBack1.academicStage);
        assertEquals("DOC-TRANSCRIPT", readBack1.documentTypeCode);
        assertEquals("Official Academic Transcript", readBack1.documentTypeName);

        // 6. Read-Back with Program JOIN enrichment
        StageDocumentRequirement readBack2 = service.getStageRequirement(adminTenantA, rule2.id);
        assertNotNull("Live read-back of Rule 2 must succeed", readBack2);
        assertEquals("ENROLLMENT", readBack2.academicStage);
        assertEquals(programCse, readBack2.programId);
        assertEquals("DEV-BTECH-CSE", readBack2.programCode);

        // 7. Verify List and Filtering in PostgreSQL
        List<StageDocumentRequirement> allRules = service.listStageRequirements(adminTenantA, null, null, true);
        assertTrue("Must list at least 3 rules", allRules.size() >= 3);

        List<StageDocumentRequirement> admissionRules = service.listStageRequirements(adminTenantA, null, "ADMISSION", true);
        assertEquals("Must return exactly 1 admission rule", 1, admissionRules.size());
        assertEquals("DOC-TRANSCRIPT", admissionRules.get(0).documentTypeCode);

        // 8. Verify Duplicate Prevention on PostgreSQL
        try {
            service.defineStageRequirement(adminTenantA, req1);
            fail("Expected 409 DUPLICATE_STAGE_REQUIREMENT on second insert of rule 1");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("DUPLICATE_STAGE_REQUIREMENT", e.getCode());
        }

        // 9. Verify Student Role Denial
        try {
            service.defineStageRequirement(studentTenantA, req1);
            fail("Expected 403 FORBIDDEN for student defining requirement");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }

        // 10. Verify Multi-Tenant PostgreSQL RLS Isolation
        try {
            service.getStageRequirement(adminTenantB, rule1.id);
            fail("Tenant B must NOT be able to view Tenant A's document requirement");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
        }

        List<StageDocumentRequirement> tenantBRules = service.listStageRequirements(adminTenantB, null, null, null);
        assertEquals("Tenant B must see 0 rules under RLS isolation", 0, tenantBRules.size());

        System.out.println(">>> LIVE POSTGRESQL VERIFICATION SUCCESSFUL: All 10 checkpoints passed! <<<");
    }
}
