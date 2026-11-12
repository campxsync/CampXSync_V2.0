package com.campx.document;

import com.campx.document.DocumentDomainService.DocumentVerificationRecord;
import com.campx.document.DocumentDomainService.StudentChecklistItem;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.UploadStudentDocumentRequest;
import com.campx.document.DocumentDomainService.VerificationDecisionRequest;
import com.campx.document.DocumentDomainService.VerificationQueueItem;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live PostgreSQL JDBC integration test for STM-06-US-004:
 * Institutional officer verifies and approves/rejects submitted student documents
 * against development Supabase reference azulbaxszsqxymqmlwfe.
 */
public class LivePostgresDocumentVerificationTest {

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private final UUID adminId = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private final UUID studentId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
    private final UUID docTypeTranscript = UUID.fromString("ef74df32-e775-4aa3-a108-52114969e4f9");

    private final Actor adminTenantA = new Actor(tenantA, adminId, "COLLEGE_ADMIN", "collegeadmin.dev@campx.edu");
    private final Actor studentTenantA = new Actor(tenantA, studentId, "STUDENT", "student1.campx@gmail.com");
    private final Actor adminTenantB = new Actor(tenantB, UUID.randomUUID(), "COLLEGE_ADMIN", "admin.b@campx.edu");

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
    public void verifyLiveOfficerDocumentVerificationLifecycleAndIsolation() {
        // Checkpoint 1: Upload a student document to verify
        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = docTypeTranscript;
        uploadReq.title = "Live Verification Doc - " + System.currentTimeMillis();
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 1048576L;
        uploadReq.fileName = "transcript_verify.pdf";

        StudentDocument uploadedDoc = service.uploadStudentDocument(studentTenantA, uploadReq);
        assertNotNull("Uploaded doc must exist", uploadedDoc);
        assertEquals("SUBMITTED", uploadedDoc.status);
        System.out.println("[+] Checkpoint 1: Uploaded document id=" + uploadedDoc.id + " with status=SUBMITTED");

        // Checkpoint 2: Officer retrieves verification queue
        List<VerificationQueueItem> queue = service.getVerificationQueue(adminTenantA, "SUBMITTED");
        assertNotNull("Queue must not be null", queue);
        boolean foundInQueue = false;
        for (VerificationQueueItem item : queue) {
            if (item.documentId.equals(uploadedDoc.id)) {
                foundInQueue = true;
                assertEquals("SUBMITTED", item.status);
                assertEquals(uploadReq.title, item.title);
                assertNotNull("Checksum must be present", item.checksumSha256);
            }
        }
        assertTrue("Uploaded document must be listed in verification queue", foundInQueue);
        System.out.println("[+] Checkpoint 2: Document found in officer verification queue");

        // Checkpoint 3: Negative validation - Officer attempts to reject without mandatory reason
        VerificationDecisionRequest invalidRej = new VerificationDecisionRequest();
        invalidRej.decision = "REJECT";
        invalidRej.rejectionReason = "";
        try {
            service.verifyDocument(adminTenantA, uploadedDoc.id, invalidRej);
            fail("Expected DocumentException(422) for blank rejection reason");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("REJECTION_REASON_REQUIRED", e.getCode());
            System.out.println("[+] Checkpoint 3: Blank rejection reason correctly rejected with 422");
        }

        // Checkpoint 4: Officer approves the document
        VerificationDecisionRequest approveReq = new VerificationDecisionRequest();
        approveReq.decision = "APPROVE";
        approveReq.verificationCode = "VER-LIVE-001";
        approveReq.verificationNotes = "Official transcript verified with registrar stamp";

        DocumentVerificationRecord approvalRecord = service.verifyDocument(adminTenantA, uploadedDoc.id, approveReq);
        assertNotNull("Approval record must be created", approvalRecord);
        assertEquals("VALID", approvalRecord.result);
        assertEquals("VER-LIVE-001", approvalRecord.verificationCode);
        assertEquals(adminId, approvalRecord.createdBy);
        System.out.println("[+] Checkpoint 4: Officer approved document with result=VALID, code=VER-LIVE-001");

        // Checkpoint 5: Live database readback from doc.documents and doc.verifications
        StudentDocument reloadedDoc = persistence.findStudentDocumentById(adminTenantA, uploadedDoc.id);
        assertNotNull("Reloaded doc must exist", reloadedDoc);
        assertEquals("APPROVED", reloadedDoc.status);

        DocumentVerificationRecord dbVerif = persistence.findLatestVerificationByDocumentId(adminTenantA, uploadedDoc.id);
        assertNotNull("Verification record must be persisted in doc.verifications", dbVerif);
        assertEquals("VALID", dbVerif.result);
        assertEquals("VER-LIVE-001", dbVerif.verificationCode);
        System.out.println("[+] Checkpoint 5: Live DB readback confirmed status=APPROVED in doc.documents and valid row in doc.verifications");

        // Checkpoint 6: Student checklist reflects VERIFIED status
        List<StudentChecklistItem> checklist = service.getStudentChecklist(studentTenantA, studentId, "ADMISSION", null);
        assertNotNull(checklist);
        boolean verifiedFound = false;
        for (StudentChecklistItem item : checklist) {
            if (item.requirement.documentTypeId.equals(docTypeTranscript)) {
                if ("VERIFIED".equals(item.status)) {
                    verifiedFound = true;
                }
            }
        }
        assertTrue("Student checklist requirement must show VERIFIED status", verifiedFound);
        System.out.println("[+] Checkpoint 6: Student checklist successfully shows VERIFIED");

        // Checkpoint 7: Student uploads a second document for rejection test
        UploadStudentDocumentRequest uploadReq2 = new UploadStudentDocumentRequest();
        uploadReq2.documentTypeId = docTypeTranscript;
        uploadReq2.title = "Second Doc for Rejection Test - " + System.currentTimeMillis();
        uploadReq2.mimeType = "application/pdf";
        uploadReq2.sizeBytes = 512000L;
        uploadReq2.fileName = "bad_scan.pdf";

        StudentDocument doc2 = service.uploadStudentDocument(studentTenantA, uploadReq2);
        assertNotNull(doc2);

        VerificationDecisionRequest rejectReq = new VerificationDecisionRequest();
        rejectReq.decision = "REJECT";
        rejectReq.rejectionReason = "Scan is blurred and illegible";

        DocumentVerificationRecord rejRecord = service.verifyDocument(adminTenantA, doc2.id, rejectReq);
        assertNotNull(rejRecord);
        assertEquals("INVALID", rejRecord.result);
        assertEquals("Scan is blurred and illegible", rejRecord.notes);

        StudentDocument reloadedDoc2 = persistence.findStudentDocumentById(adminTenantA, doc2.id);
        assertEquals("REJECTED", reloadedDoc2.status);
        System.out.println("[+] Checkpoint 7: Officer rejection recorded with status=REJECTED and result=INVALID");

        // Checkpoint 8: RBAC Security - Student cannot verify own or other documents
        VerificationDecisionRequest studentAttempt = new VerificationDecisionRequest();
        studentAttempt.decision = "APPROVE";
        try {
            service.verifyDocument(studentTenantA, doc2.id, studentAttempt);
            fail("Expected DocumentException(403) when student calls verifyDocument");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
            System.out.println("[+] Checkpoint 8: Student verification attempt blocked with 403 Forbidden");
        }

        // Checkpoint 9: Multi-tenant isolation - Tenant B admin cannot see or verify Tenant A document
        try {
            service.verifyDocument(adminTenantB, uploadedDoc.id, approveReq);
            fail("Expected 404 because document does not belong to Tenant B");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_NOT_FOUND", e.getCode());
            System.out.println("[+] Checkpoint 9: Cross-tenant verification attempt rejected with 404");
        }
    }
}

