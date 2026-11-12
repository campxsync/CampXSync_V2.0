package com.campx.document;

import com.campx.document.DocumentDomainService.DefineStageRequirementRequest;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.StudentChecklistItem;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.UploadStudentDocumentRequest;
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
 * Live PostgreSQL JDBC persistence, multi-table atomic commit, and RLS isolation test
 * for STM-06-US-003 against dev ref azulbaxszsqxymqmlwfe.
 */
public class LivePostgresStudentDocumentUploadTest {

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private final UUID adminId = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private final UUID studentId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
    private final UUID otherStudentId = UUID.fromString("bdf7a805-ff27-4ecd-862e-4c0c7b7090ae");

    private final UUID docTypeTranscript = UUID.fromString("ef74df32-e775-4aa3-a108-52114969e4f9");
    private final UUID docTypeNationalId = UUID.fromString("fdf88717-6db1-45f8-8e27-8e085304a14b");

    private final Actor adminTenantA = new Actor(tenantA, adminId, "COLLEGE_ADMIN", "collegeadmin.dev@campx.edu");
    private final Actor studentTenantA = new Actor(tenantA, studentId, "STUDENT", "student1.campx@gmail.com");
    private final Actor studentTenantB = new Actor(tenantB, UUID.randomUUID(), "STUDENT", "student.b@campx.edu");

    private DocumentDomainService service;
    private PostgresDocumentPersistence persistence;

    @Before
    public void setUp() {
        DatabaseConfig config = new DatabaseConfig();
        Assume.assumeTrue("Database must be configured with dev ref credentials", config.isConfigured());

        DatabaseConnectionManager connectionManager = DatabaseConnectionManager.getInstance();
        connectionManager.initialize(config);
        persistence = new PostgresDocumentPersistence(connectionManager);
        DocumentIntelligenceService intelligenceService = new DocumentIntelligenceService();
        service = new DocumentDomainService(persistence, intelligenceService);
    }

    @Test
    public void verifyLiveStudentDocumentUploadMultiTableCommitAndIsolation() {
        // 1. Ensure requirement rule exists for ADMISSION stage
        List<StageDocumentRequirement> existingRules = service.listStageRequirements(adminTenantA, null, "ADMISSION", true);
        StageDocumentRequirement admissionRule;
        if (existingRules.isEmpty()) {
            DefineStageRequirementRequest req = new DefineStageRequirementRequest();
            req.academicStage = "ADMISSION";
            req.documentTypeId = docTypeTranscript;
            req.isMandatory = true;
            req.minCount = 1;
            admissionRule = service.defineStageRequirement(adminTenantA, req);
        } else {
            admissionRule = existingRules.get(0);
        }
        assertNotNull("Admission requirement must exist", admissionRule);

        // 2. Perform live student document upload (STM-06-US-003)
        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = admissionRule.documentTypeId;
        uploadReq.stageRequirementId = admissionRule.id;
        uploadReq.title = "Live Test 12th Grade Transcript " + System.currentTimeMillis();
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 2048576L; // ~2MB
        uploadReq.fileName = "student_transcript.pdf";
        uploadReq.changeNote = "Initial upload for stage requirement";

        StudentDocument uploadedDoc = service.uploadStudentDocument(studentTenantA, uploadReq);
        assertNotNull("Uploaded document must not be null", uploadedDoc);
        assertNotNull("Document ID must be generated", uploadedDoc.id);
        assertEquals(tenantA, uploadedDoc.tenantId);
        assertEquals(studentId, uploadedDoc.ownerId);
        assertEquals(admissionRule.documentTypeId, uploadedDoc.documentTypeId);
        assertEquals(uploadReq.title, uploadedDoc.title);
        assertEquals("SUBMITTED", uploadedDoc.status);
        assertEquals("application/pdf", uploadedDoc.mimeType);
        assertEquals(2048576L, (long) uploadedDoc.sizeBytes);
        assertEquals(1, uploadedDoc.currentVersionNo);
        assertNotNull(uploadedDoc.storageBucket);
        assertNotNull(uploadedDoc.storagePath);

        // 3. Live committed read-back of Document
        StudentDocument readBack = persistence.findStudentDocumentById(studentTenantA, uploadedDoc.id);
        assertNotNull("Live read-back of document must succeed from PostgreSQL", readBack);
        assertEquals(uploadedDoc.id, readBack.id);
        assertEquals("SUBMITTED", readBack.status);

        // 4. Verify Document Version (atomic multi-table persistence)
        List<DocumentVersion> versions = persistence.listDocumentVersions(studentTenantA, uploadedDoc.id);
        assertNotNull("Versions must not be null", versions);
        assertEquals("Must have version 1", 1, versions.size());
        DocumentVersion v1 = versions.get(0);
        assertEquals(1, v1.versionNo);
        assertEquals(uploadedDoc.storagePath, v1.storagePath);
        assertEquals(2048576L, (long) v1.sizeBytes);
        assertEquals("Initial upload for stage requirement", v1.changeNote);

        // 5. Verify Student Submissions List
        List<StudentDocument> studentList = service.listStudentDocuments(studentTenantA, studentId, null);
        assertNotNull(studentList);
        assertTrue("Student must have at least 1 document", studentList.size() >= 1);
        boolean foundInList = false;
        for (StudentDocument doc : studentList) {
            if (doc.id.equals(uploadedDoc.id)) {
                foundInList = true;
                assertEquals("DOC-TRANSCRIPT", doc.documentTypeCode);
                break;
            }
        }
        assertTrue("Uploaded document must appear in list with joined document type code", foundInList);

        // 6. Verify Student Checklist Computation
        List<StudentChecklistItem> checklist = service.getStudentChecklist(studentTenantA, studentId, "ADMISSION", null);
        assertNotNull("Checklist must not be null", checklist);
        assertTrue("Checklist must contain at least 1 item", checklist.size() >= 1);
        StudentChecklistItem chkItem = checklist.get(0);
        assertEquals("SUBMITTED", chkItem.status);
        assertTrue("Checklist must include submitted document", chkItem.submittedDocuments.size() >= 1);

        // 7. Verify Validation Error: Invalid MIME Type
        UploadStudentDocumentRequest badMimeReq = new UploadStudentDocumentRequest();
        badMimeReq.documentTypeId = admissionRule.documentTypeId;
        badMimeReq.title = "Executable File";
        badMimeReq.mimeType = "application/x-dosexec";
        badMimeReq.sizeBytes = 1000L;
        try {
            service.uploadStudentDocument(studentTenantA, badMimeReq);
            fail("Expected 422 UNSUPPORTED_MIME_TYPE");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("UNSUPPORTED_MIME_TYPE", e.getCode());
        }

        // 8. Verify Validation Error: File size > 10MB
        UploadStudentDocumentRequest tooBigReq = new UploadStudentDocumentRequest();
        tooBigReq.documentTypeId = admissionRule.documentTypeId;
        tooBigReq.title = "Huge Video";
        tooBigReq.mimeType = "application/pdf";
        tooBigReq.sizeBytes = 15 * 1024 * 1024L; // 15MB
        try {
            service.uploadStudentDocument(studentTenantA, tooBigReq);
            fail("Expected 422 FILE_TOO_LARGE");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("FILE_TOO_LARGE", e.getCode());
        }

        // 9. Verify Cross-Student Access Denial
        try {
            service.listStudentDocuments(studentTenantA, otherStudentId, null);
            fail("Expected 403 FORBIDDEN when student queries other student's documents");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }

        // 10. Verify Cross-Tenant Isolation
        StudentDocument crossTenantDoc = persistence.findStudentDocumentById(studentTenantB, uploadedDoc.id);
        org.junit.Assert.assertNull("Tenant B must NOT be able to view Tenant A's document under RLS", crossTenantDoc);
        List<StudentDocument> crossList = service.listStudentDocuments(studentTenantB, studentTenantB.getUserId(), null);
        assertTrue("Tenant B must see 0 documents under tenant isolation", crossList.isEmpty());

        System.out.println(">>> LIVE POSTGRESQL STM-06-US-003 VERIFICATION SUCCESSFUL: All 10 checkpoints passed! <<<");
    }
}
