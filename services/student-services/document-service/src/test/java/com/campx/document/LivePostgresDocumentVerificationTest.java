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
import com.campx.document.security.ControlledMalwareScanAdapter;
import com.campx.document.security.FakeDelegationPort;
import com.campx.document.security.SignedDownloadTokenService;
import com.campx.document.storage.DocumentStorageConfig;
import com.campx.document.storage.FakeDocumentObjectStorage;
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
    private final UUID registrarId = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private final UUID studentId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
    private final UUID docTypeTranscript = UUID.fromString("ef74df32-e775-4aa3-a108-52114969e4f9");
    private final UUID campusA = UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private final Actor registrarTenantA = new Actor(tenantA, registrarId, campusA, "REGISTRAR", "collegeadmin.dev@campx.edu", null);
    private final Actor studentTenantA = new Actor(tenantA, studentId, campusA, "STUDENT", "student1.campx@gmail.com", null);
    private final Actor registrarTenantB = new Actor(tenantB, UUID.randomUUID(), campusA, "REGISTRAR", "admin.b@campx.edu", null);

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
        FakeDelegationPort delegations = new FakeDelegationPort();
        delegations.grant(tenantA, registrarId, DocumentDomainService.DELEGATION_APPROVE_KEY);
        delegations.grant(tenantB, registrarTenantB.getUserId(), DocumentDomainService.DELEGATION_APPROVE_KEY);
        service = new DocumentDomainService(persistence, intelligenceService, new ControlledMalwareScanAdapter(),
                new SignedDownloadTokenService(null),
                new FakeDocumentObjectStorage(DocumentStorageConfig.DEFAULT_BUCKET, false),
                delegations);
    }

    @Test
    public void verifyLiveOfficerDocumentVerificationLifecycleAndIsolation() {
        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = docTypeTranscript;
        uploadReq.title = "Live Verification Doc - " + System.currentTimeMillis();
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 1048576L;
        uploadReq.fileName = "transcript_verify.pdf";
        uploadReq.collegeId = campusA;

        StudentDocument uploadedDoc = service.uploadStudentDocument(studentTenantA, uploadReq);
        assertNotNull("Uploaded doc must exist", uploadedDoc);
        assertEquals("SUBMITTED", uploadedDoc.status);
        System.out.println("[+] Checkpoint 1: Uploaded document id=" + uploadedDoc.id + " with status=SUBMITTED");

        List<VerificationQueueItem> queue = service.getVerificationQueue(registrarTenantA, "SUBMITTED");
        assertNotNull("Queue must not be null", queue);
        boolean foundInQueue = false;
        for (VerificationQueueItem item : queue) {
            if (item.documentId.equals(uploadedDoc.id)) {
                foundInQueue = true;
                assertEquals("SUBMITTED", item.status);
                assertEquals(uploadReq.title, item.title);
            }
        }
        assertTrue("Uploaded document must appear in verification queue", foundInQueue);
        System.out.println("[+] Checkpoint 2: Document found in verification queue");

        VerificationDecisionRequest approveReq = new VerificationDecisionRequest();
        approveReq.decision = "APPROVE";
        approveReq.verificationCode = "LIVE-VERIFIED-" + System.currentTimeMillis();
        approveReq.verificationNotes = "Live officer approval";

        DocumentVerificationRecord verification = service.verifyDocument(registrarTenantA, uploadedDoc.id, approveReq);
        assertNotNull(verification);
        assertEquals("VALID", verification.result);
        assertEquals(registrarId, verification.createdBy);
        System.out.println("[+] Checkpoint 3: Document approved result=VALID");

        StudentDocument afterApprove = persistence.findStudentDocumentById(studentTenantA, uploadedDoc.id);
        assertNotNull(afterApprove);
        assertEquals("APPROVED", afterApprove.status);

        DocumentVerificationRecord dbVerif = persistence.findLatestVerificationByDocumentId(registrarTenantA, uploadedDoc.id);
        assertNotNull(dbVerif);
        assertEquals("VALID", dbVerif.result);

        List<StudentChecklistItem> checklist = service.getStudentChecklist(studentTenantA, studentId, null, null);
        assertNotNull(checklist);

        try {
            service.getVerificationQueue(registrarTenantB, "SUBMITTED");
        } catch (DocumentException e) {
            assertTrue(e.getStatusCode() == 403 || e.getStatusCode() == 401);
        }

        try {
            service.verifyDocument(registrarTenantB, uploadedDoc.id, approveReq);
            fail("Cross-tenant registrar must not verify tenant A document");
        } catch (DocumentException e) {
            assertTrue(e.getStatusCode() == 403 || e.getStatusCode() == 404);
        }
    }
}
