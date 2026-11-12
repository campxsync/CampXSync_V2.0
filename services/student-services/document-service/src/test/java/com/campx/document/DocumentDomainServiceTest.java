package com.campx.document;

import com.campx.document.DocumentDomainService.ConfigureDocumentTypeRequest;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.UpdateDocumentTypeRequest;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.DefineStageRequirementRequest;
import com.campx.document.DocumentDomainService.UpdateStageRequirementRequest;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.UploadStudentDocumentRequest;
import com.campx.document.DocumentDomainService.StudentChecklistItem;
import com.campx.document.DocumentDomainService.DocumentVerificationRecord;
import com.campx.document.DocumentDomainService.VerificationDecisionRequest;
import com.campx.document.DocumentDomainService.VerificationQueueItem;
import com.campx.document.DocumentDomainService.ResubmitDocumentRequest;
import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.InMemoryDocumentPersistence;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Automated test suite for STM-06-US-001 (Configure document types) and document catalog.
 */
public class DocumentDomainServiceTest {

    private InMemoryDocumentPersistence persistence;
    private DocumentIntelligenceService intelligenceService;
    private DocumentDomainService service;

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private final Actor adminTenantA = new Actor(tenantA, UUID.randomUUID(), "COLLEGE_ADMIN", "admin.a@campx.edu");
    private final Actor registrarTenantA = new Actor(tenantA, UUID.randomUUID(), "REGISTRAR", "registrar.a@campx.edu");
    private final Actor studentTenantA = new Actor(tenantA, UUID.randomUUID(), "STUDENT", "student.a@campx.edu");
    private final Actor adminTenantB = new Actor(tenantB, UUID.randomUUID(), "COLLEGE_ADMIN", "admin.b@campx.edu");

    @Before
    public void setUp() {
        persistence = new InMemoryDocumentPersistence();
        intelligenceService = new DocumentIntelligenceService();
        service = new DocumentDomainService(persistence, intelligenceService);
    }

    @Test
    public void collegeAdminCanConfigureDocumentType() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-TRANSCRIPT";
        req.name = "Official Academic Transcript";
        req.category = "ACADEMIC";
        req.module = "STUDENT";
        req.defaultClassification = "CONFIDENTIAL";
        req.requiresSignature = true;
        req.retentionPolicyCode = "RET_PERMANENT";
        req.isActive = true;

        DocumentType created = service.configureDocumentType(adminTenantA, req);

        assertNotNull("Created document type must not be null", created);
        assertNotNull("ID must be generated", created.id);
        assertEquals(tenantA, created.tenantId);
        assertEquals("DOC-TRANSCRIPT", created.code);
        assertEquals("Official Academic Transcript", created.name);
        assertEquals("ACADEMIC", created.category);
        assertEquals("STUDENT", created.module);
        assertEquals("CONFIDENTIAL", created.defaultClassification);
        assertTrue(created.requiresSignature);
        assertEquals("RET_PERMANENT", created.retentionPolicyCode);
        assertTrue(created.isActive);
        assertEquals(1, created.rowVersion);

        // Verify retrieval via findById and findByCode
        DocumentType byId = persistence.findDocumentTypeById(adminTenantA, created.id);
        assertNotNull(byId);
        assertEquals("DOC-TRANSCRIPT", byId.code);

        DocumentType byCode = persistence.findDocumentTypeByCode(adminTenantA, "DOC-TRANSCRIPT");
        assertNotNull(byCode);
        assertEquals(created.id, byCode.id);
    }

    @Test
    public void registrarCanConfigureDocumentType() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-NATIONAL-ID";
        req.name = "Government Issued National ID / Passport";
        req.category = "IDENTIFICATION";
        req.defaultClassification = "RESTRICTED";
        req.requiresSignature = false;

        DocumentType created = service.configureDocumentType(registrarTenantA, req);
        assertNotNull(created);
        assertEquals("DOC-NATIONAL-ID", created.code);
        assertEquals("RESTRICTED", created.defaultClassification);
        assertEquals("STUDENT", created.module); // default
    }

    @Test
    public void studentCannotConfigureDocumentType() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-STUDENT-CARD";
        req.name = "Student Identification Card";

        try {
            service.configureDocumentType(studentTenantA, req);
            fail("Expected DocumentException(403) for student role");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void duplicateDocumentTypeCodeInSameTenantIsRejected() {
        ConfigureDocumentTypeRequest req1 = new ConfigureDocumentTypeRequest();
        req1.code = "DOC-BIRTH-CERT";
        req1.name = "Birth Certificate";
        service.configureDocumentType(adminTenantA, req1);

        ConfigureDocumentTypeRequest req2 = new ConfigureDocumentTypeRequest();
        req2.code = "DOC-BIRTH-CERT";
        req2.name = "Birth Certificate Duplicate";

        try {
            service.configureDocumentType(adminTenantA, req2);
            fail("Expected DocumentException(409) for duplicate code");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("DUPLICATE_DOCUMENT_TYPE_CODE", e.getCode());
        }
    }

    @Test
    public void sameDocumentTypeCodeInDifferentTenantIsAllowed() {
        ConfigureDocumentTypeRequest reqA = new ConfigureDocumentTypeRequest();
        reqA.code = "DOC-DIPLOMA";
        reqA.name = "Diploma Certificate";
        DocumentType docA = service.configureDocumentType(adminTenantA, reqA);

        ConfigureDocumentTypeRequest reqB = new ConfigureDocumentTypeRequest();
        reqB.code = "DOC-DIPLOMA";
        reqB.name = "Diploma Certificate for College B";
        DocumentType docB = service.configureDocumentType(adminTenantB, reqB);

        assertNotNull(docA);
        assertNotNull(docB);
        assertEquals(tenantA, docA.tenantId);
        assertEquals(tenantB, docB.tenantId);
    }

    @Test
    public void missingDocumentTypeCodeIsRejected() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "";
        req.name = "Some Document";

        try {
            service.configureDocumentType(adminTenantA, req);
            fail("Expected DocumentException(422) for empty code");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_CODE_REQUIRED", e.getCode());
        }
    }

    @Test
    public void invalidDocumentTypeCodeFormatIsRejected() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "doc transcript invalid spaces";
        req.name = "Some Document";

        try {
            service.configureDocumentType(adminTenantA, req);
            fail("Expected DocumentException(422) for invalid code pattern");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_DOCUMENT_TYPE_CODE", e.getCode());
        }
    }

    @Test
    public void missingDocumentTypeNameIsRejected() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-TEST";
        req.name = "";

        try {
            service.configureDocumentType(adminTenantA, req);
            fail("Expected DocumentException(422) for empty name");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_NAME_INVALID", e.getCode());
        }
    }

    @Test
    public void invalidClassificationIsRejected() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-SECRET";
        req.name = "Secret Document";
        req.defaultClassification = "TOP_SECRET";

        try {
            service.configureDocumentType(adminTenantA, req);
            fail("Expected DocumentException(422) for invalid classification");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_CLASSIFICATION", e.getCode());
        }
    }

    @Test
    public void listDocumentTypesWithCategoryFilter() {
        ConfigureDocumentTypeRequest req1 = new ConfigureDocumentTypeRequest();
        req1.code = "DOC-1";
        req1.name = "Doc 1";
        req1.category = "ACADEMIC";
        service.configureDocumentType(adminTenantA, req1);

        ConfigureDocumentTypeRequest req2 = new ConfigureDocumentTypeRequest();
        req2.code = "DOC-2";
        req2.name = "Doc 2";
        req2.category = "IDENTIFICATION";
        service.configureDocumentType(adminTenantA, req2);

        List<DocumentType> all = service.listDocumentTypes(adminTenantA, null, null);
        assertEquals(2, all.size());

        List<DocumentType> academicOnly = service.listDocumentTypes(adminTenantA, "ACADEMIC", null);
        assertEquals(1, academicOnly.size());
        assertEquals("DOC-1", academicOnly.get(0).code);
    }

    @Test
    public void listDocumentTypesWithActiveFilter() {
        ConfigureDocumentTypeRequest req1 = new ConfigureDocumentTypeRequest();
        req1.code = "DOC-ACTIVE";
        req1.name = "Active Doc";
        req1.isActive = true;
        service.configureDocumentType(adminTenantA, req1);

        ConfigureDocumentTypeRequest req2 = new ConfigureDocumentTypeRequest();
        req2.code = "DOC-INACTIVE";
        req2.name = "Inactive Doc";
        req2.isActive = false;
        service.configureDocumentType(adminTenantA, req2);

        List<DocumentType> activeOnly = service.listDocumentTypes(adminTenantA, null, true);
        assertEquals(1, activeOnly.size());
        assertEquals("DOC-ACTIVE", activeOnly.get(0).code);
    }

    @Test
    public void getDocumentTypeById() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-LOOKUP";
        req.name = "Lookup Doc";
        DocumentType created = service.configureDocumentType(adminTenantA, req);

        DocumentType fetched = service.getDocumentType(adminTenantA, created.id);
        assertNotNull(fetched);
        assertEquals("DOC-LOOKUP", fetched.code);
    }

    @Test
    public void getDocumentTypeNotFoundThrows404() {
        try {
            service.getDocumentType(adminTenantA, UUID.randomUUID());
            fail("Expected DocumentException(404)");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_NOT_FOUND", e.getCode());
        }
    }

    @Test
    public void updateDocumentTypeSucceeds() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-UPDATE";
        req.name = "Original Name";
        req.category = "ACADEMIC";
        DocumentType created = service.configureDocumentType(adminTenantA, req);
        assertEquals(1, created.rowVersion);

        UpdateDocumentTypeRequest updateReq = new UpdateDocumentTypeRequest();
        updateReq.name = "Updated Name";
        updateReq.category = "LEGAL";
        updateReq.defaultClassification = "RESTRICTED";
        updateReq.requiresSignature = true;
        updateReq.isActive = false;

        DocumentType updated = service.updateDocumentType(adminTenantA, created.id, updateReq);
        assertEquals("Updated Name", updated.name);
        assertEquals("LEGAL", updated.category);
        assertEquals("RESTRICTED", updated.defaultClassification);
        assertTrue(updated.requiresSignature);
        assertFalse(updated.isActive);
        assertEquals(2, updated.rowVersion);
    }

    @Test
    public void studentCannotUpdateDocumentType() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-SECURE";
        req.name = "Secure Doc";
        DocumentType created = service.configureDocumentType(adminTenantA, req);

        UpdateDocumentTypeRequest updateReq = new UpdateDocumentTypeRequest();
        updateReq.name = "Hacked Name";

        try {
            service.updateDocumentType(studentTenantA, created.id, updateReq);
            fail("Expected DocumentException(403) for student update attempt");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void unauthenticatedCallerIsRejected() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-TEST";
        req.name = "Test Doc";

        try {
            service.configureDocumentType(null, req);
            fail("Expected DocumentException(401)");
        } catch (DocumentException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals("UNAUTHENTICATED", e.getCode());
        }
    }

    // ==========================================
    // STM-06-US-002: Define mandatory documents for programmes and stages
    // ==========================================

    @Test
    public void collegeAdminCanDefineStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-BIRTH-CERT";
        dtReq.name = "Birth Certificate";
        dtReq.category = "IDENTIFICATION";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = dt.id;
        req.isMandatory = true;
        req.minCount = 1;
        req.maxCount = 1;
        req.submissionDeadlineDays = 15;
        req.description = "Mandatory birth certificate for admission verification";

        StageDocumentRequirement created = service.defineStageRequirement(adminTenantA, req);

        assertNotNull(created);
        assertNotNull(created.id);
        assertEquals(tenantA, created.tenantId);
        assertEquals("ADMISSION", created.academicStage);
        assertEquals(dt.id, created.documentTypeId);
        assertEquals("DOC-BIRTH-CERT", created.documentTypeCode);
        assertEquals("Birth Certificate", created.documentTypeName);
        assertTrue(created.isMandatory);
        assertEquals(1, created.minCount);
        assertEquals(Integer.valueOf(1), created.maxCount);
        assertEquals(Integer.valueOf(15), created.submissionDeadlineDays);
        assertTrue(created.isActive);

        StageDocumentRequirement fetched = service.getStageRequirement(adminTenantA, created.id);
        assertNotNull(fetched);
        assertEquals(created.id, fetched.id);
    }

    @Test
    public void collegeAdminCanDefineProgramSpecificStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-PORTFOLIO";
        dtReq.name = "Design Portfolio";
        dtReq.category = "ACADEMIC";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        UUID programId = UUID.randomUUID();
        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.programId = programId;
        req.academicStage = "EXAMINATION";
        req.documentTypeId = dt.id;
        req.isMandatory = false;
        req.minCount = 1;
        req.maxCount = 3;

        StageDocumentRequirement created = service.defineStageRequirement(adminTenantA, req);
        assertNotNull(created);
        assertEquals(programId, created.programId);
        assertEquals("EXAMINATION", created.academicStage);
        assertFalse(created.isMandatory);
        assertEquals(Integer.valueOf(3), created.maxCount);
    }

    @Test
    public void registrarCanDefineStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-MIGRATION";
        dtReq.name = "Migration Certificate";
        dtReq.category = "ACADEMIC";
        DocumentType dt = service.configureDocumentType(registrarTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ENROLLMENT";
        req.documentTypeId = dt.id;
        req.isMandatory = true;

        StageDocumentRequirement created = service.defineStageRequirement(registrarTenantA, req);
        assertNotNull(created);
        assertEquals("ENROLLMENT", created.academicStage);
    }

    @Test
    public void studentCannotDefineStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-GRADE-12";
        dtReq.name = "Grade 12 Marksheet";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = dt.id;

        try {
            service.defineStageRequirement(studentTenantA, req);
            fail("Expected DocumentException(403) for student define requirement attempt");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void cannotDefineRequirementForInvalidStage() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-MEDICAL";
        dtReq.name = "Medical Certificate";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "SPORTS_MEET"; // Invalid stage
        req.documentTypeId = dt.id;

        try {
            service.defineStageRequirement(adminTenantA, req);
            fail("Expected DocumentException(422) for invalid stage");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_ACADEMIC_STAGE", e.getCode());
        }
    }

    @Test
    public void cannotDefineRequirementForNonExistentDocumentType() {
        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "GRADUATION";
        req.documentTypeId = UUID.randomUUID();

        try {
            service.defineStageRequirement(adminTenantA, req);
            fail("Expected DocumentException(404) for non-existent document type");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_NOT_FOUND", e.getCode());
        }
    }

    @Test
    public void cannotDefineDuplicateStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-CLEARANCE";
        dtReq.name = "No Dues Clearance Certificate";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "GRADUATION";
        req.documentTypeId = dt.id;
        service.defineStageRequirement(adminTenantA, req);

        try {
            service.defineStageRequirement(adminTenantA, req);
            fail("Expected DocumentException(409) for duplicate stage requirement");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("DUPLICATE_STAGE_REQUIREMENT", e.getCode());
        }
    }

    @Test
    public void invalidMinMaxCountRejected() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-REC";
        dtReq.name = "Recommendation Letter";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = dt.id;
        req.minCount = 3;
        req.maxCount = 1; // min > max

        try {
            service.defineStageRequirement(adminTenantA, req);
            fail("Expected DocumentException(422) when minCount > maxCount");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_MAX_COUNT", e.getCode());
        }

        req.minCount = -2;
        req.maxCount = 2;
        try {
            service.defineStageRequirement(adminTenantA, req);
            fail("Expected DocumentException(422) when minCount < 0");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_MIN_COUNT", e.getCode());
        }
    }

    @Test
    public void tenantIsolationEnforcedOnStageRequirements() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-TENANT-ISO";
        dtReq.name = "Tenant Isolated Document";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ANNUAL_PROGRESSION";
        req.documentTypeId = dt.id;
        StageDocumentRequirement created = service.defineStageRequirement(adminTenantA, req);

        // Tenant B cannot find Tenant A's requirement by ID
        try {
            service.getStageRequirement(adminTenantB, created.id);
            fail("Expected 404 for cross-tenant stage requirement access");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
        }

        // Tenant B list returns empty
        List<StageDocumentRequirement> tenantBList = service.listStageRequirements(adminTenantB, null, null, null);
        assertEquals(0, tenantBList.size());
    }

    @Test
    public void canUpdateStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-UPDATE-REQ";
        dtReq.name = "Updatable Document";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = dt.id;
        req.isMandatory = true;
        StageDocumentRequirement created = service.defineStageRequirement(adminTenantA, req);
        assertTrue(created.isMandatory);

        UpdateStageRequirementRequest updateReq = new UpdateStageRequirementRequest();
        updateReq.isMandatory = false;
        updateReq.minCount = 2;
        updateReq.maxCount = 4;
        updateReq.description = "Updated requirement rules";

        StageDocumentRequirement updated = service.updateStageRequirement(adminTenantA, created.id, updateReq);
        assertFalse(updated.isMandatory);
        assertEquals(2, updated.minCount);
        assertEquals(Integer.valueOf(4), updated.maxCount);
        assertEquals("Updated requirement rules", updated.description);
        assertEquals(2, updated.rowVersion);
    }

    @Test
    public void canDeleteStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-DELETE-REQ";
        dtReq.name = "Deletable Document";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "GRADUATION";
        req.documentTypeId = dt.id;
        StageDocumentRequirement created = service.defineStageRequirement(adminTenantA, req);

        service.deleteStageRequirement(adminTenantA, created.id);

        try {
            service.getStageRequirement(adminTenantA, created.id);
            fail("Expected 404 after deleting stage requirement");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    public void canListAndFilterRequirementsByStageAndProgram() {
        ConfigureDocumentTypeRequest dt1 = new ConfigureDocumentTypeRequest();
        dt1.code = "DOC-F1";
        dt1.name = "Filter Doc 1";
        DocumentType t1 = service.configureDocumentType(adminTenantA, dt1);

        ConfigureDocumentTypeRequest dt2 = new ConfigureDocumentTypeRequest();
        dt2.code = "DOC-F2";
        dt2.name = "Filter Doc 2";
        DocumentType t2 = service.configureDocumentType(adminTenantA, dt2);

        UUID progId = UUID.randomUUID();

        DefineStageRequirementRequest r1 = new DefineStageRequirementRequest();
        r1.academicStage = "ADMISSION";
        r1.documentTypeId = t1.id;
        service.defineStageRequirement(adminTenantA, r1);

        DefineStageRequirementRequest r2 = new DefineStageRequirementRequest();
        r2.academicStage = "GRADUATION";
        r2.programId = progId;
        r2.documentTypeId = t2.id;
        service.defineStageRequirement(adminTenantA, r2);

        List<StageDocumentRequirement> all = service.listStageRequirements(adminTenantA, null, null, null);
        assertEquals(2, all.size());

        List<StageDocumentRequirement> admissionOnly = service.listStageRequirements(adminTenantA, null, "ADMISSION", null);
        assertEquals(1, admissionOnly.size());
        assertEquals("ADMISSION", admissionOnly.get(0).academicStage);

        List<StageDocumentRequirement> progOnly = service.listStageRequirements(adminTenantA, progId, null, null);
        assertEquals(1, progOnly.size());
        assertEquals(progId, progOnly.get(0).programId);
    }

    // ==========================================
    // STM-06-US-003: Student Uploads Documents Tests
    // ==========================================

    @Test
    public void studentCanUploadDocumentAgainstStageRequirement() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-NAT-ID";
        dtReq.name = "National Identity Card";
        dtReq.category = "IDENTITY";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest reqReq = new DefineStageRequirementRequest();
        reqReq.academicStage = "ENROLLMENT";
        reqReq.documentTypeId = dt.id;
        reqReq.isMandatory = true;
        reqReq.submissionDeadlineDays = 30;
        StageDocumentRequirement sdr = service.defineStageRequirement(adminTenantA, reqReq);

        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = dt.id;
        uploadReq.stageRequirementId = sdr.id;
        uploadReq.title = "Aadhaar Card Front and Back";
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 2048500L;
        uploadReq.fileName = "aadhaar_verified.pdf";
        uploadReq.changeNote = "Original scanned PDF";

        StudentDocument doc = service.uploadStudentDocument(studentTenantA, uploadReq);

        assertNotNull(doc);
        assertEquals(studentTenantA.getTenantId(), doc.tenantId);
        assertEquals(studentTenantA.getUserId(), doc.ownerId);
        assertEquals("SUBMITTED", doc.status);
        assertEquals(1, doc.currentVersionNo);
        assertEquals("application/pdf", doc.mimeType);
        assertEquals(2048500L, doc.sizeBytes);
        assertTrue(doc.storagePath.contains(studentTenantA.getUserId().toString()));

        List<DocumentVersion> versions = persistence.listDocumentVersions(studentTenantA, doc.id);
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).versionNo);
        assertEquals("Original scanned PDF", versions.get(0).changeNote);

        List<StudentDocument> myDocs = service.listStudentDocuments(studentTenantA, studentTenantA.getUserId(), null);
        assertEquals(1, myDocs.size());
        assertEquals(doc.id, myDocs.get(0).id);
    }

    @Test
    public void studentCannotUploadWithoutValidTitle() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-T1";
        dtReq.name = "Doc Title Test";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest req = new UploadStudentDocumentRequest();
        req.documentTypeId = dt.id;
        req.title = " ";
        req.mimeType = "application/pdf";
        req.sizeBytes = 1000L;

        try {
            service.uploadStudentDocument(studentTenantA, req);
            fail("Expected DocumentException for invalid title");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_TITLE", e.getCode());
        }
    }

    @Test
    public void studentCannotUploadUnsupportedMimeType() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-MIME";
        dtReq.name = "Doc Mime Test";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest req = new UploadStudentDocumentRequest();
        req.documentTypeId = dt.id;
        req.title = "Valid Title";
        req.mimeType = "application/x-executable";
        req.sizeBytes = 1000L;

        try {
            service.uploadStudentDocument(studentTenantA, req);
            fail("Expected DocumentException for unsupported MIME type");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("UNSUPPORTED_MIME_TYPE", e.getCode());
        }
    }

    @Test
    public void studentCannotUploadFileExceeding10MB() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-SIZE";
        dtReq.name = "Doc Size Test";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest req = new UploadStudentDocumentRequest();
        req.documentTypeId = dt.id;
        req.title = "Big File Title";
        req.mimeType = "application/pdf";
        req.sizeBytes = 11 * 1024 * 1024L; // 11MB > 10MB

        try {
            service.uploadStudentDocument(studentTenantA, req);
            fail("Expected DocumentException for file too large");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("FILE_TOO_LARGE", e.getCode());
        }
    }

    @Test
    public void studentCannotUploadWithMismatchedStageRequirementType() {
        ConfigureDocumentTypeRequest dtReq1 = new ConfigureDocumentTypeRequest();
        dtReq1.code = "DOC-TYPE-A";
        dtReq1.name = "Type A";
        DocumentType dtA = service.configureDocumentType(adminTenantA, dtReq1);

        ConfigureDocumentTypeRequest dtReq2 = new ConfigureDocumentTypeRequest();
        dtReq2.code = "DOC-TYPE-B";
        dtReq2.name = "Type B";
        DocumentType dtB = service.configureDocumentType(adminTenantA, dtReq2);

        DefineStageRequirementRequest sdrReq = new DefineStageRequirementRequest();
        sdrReq.academicStage = "ADMISSION";
        sdrReq.documentTypeId = dtA.id;
        StageDocumentRequirement sdr = service.defineStageRequirement(adminTenantA, sdrReq);

        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = dtB.id; // Mismatched doc type!
        uploadReq.stageRequirementId = sdr.id;
        uploadReq.title = "Mismatched Doc";
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 1000L;

        try {
            service.uploadStudentDocument(studentTenantA, uploadReq);
            fail("Expected DocumentException for requirement type mismatch");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("REQUIREMENT_TYPE_MISMATCH", e.getCode());
        }
    }

    @Test
    public void studentCannotViewOtherStudentsDocuments() {
        UUID otherStudentId = UUID.randomUUID();
        try {
            service.listStudentDocuments(studentTenantA, otherStudentId, null);
            fail("Expected DocumentException when student queries another student's documents");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void crossTenantStudentUploadIsIsolated() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-TENANT-A";
        dtReq.name = "Tenant A Only Doc";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        Actor studentTenantB = new Actor(tenantB, UUID.randomUUID(), "STUDENT", "student.b@campx.edu");
        UploadStudentDocumentRequest uploadReq = new UploadStudentDocumentRequest();
        uploadReq.documentTypeId = dt.id;
        uploadReq.title = "Cross Tenant Upload";
        uploadReq.mimeType = "application/pdf";
        uploadReq.sizeBytes = 1000L;

        try {
            service.uploadStudentDocument(studentTenantB, uploadReq);
            fail("Expected 404 because document type does not exist in Tenant B");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_NOT_FOUND", e.getCode());
        }
    }

    @Test
    public void studentChecklistComputesCorrectStatuses() {
        ConfigureDocumentTypeRequest dt1 = new ConfigureDocumentTypeRequest();
        dt1.code = "DOC-CHK-1";
        dt1.name = "Checklist Doc 1";
        DocumentType t1 = service.configureDocumentType(adminTenantA, dt1);

        ConfigureDocumentTypeRequest dt2 = new ConfigureDocumentTypeRequest();
        dt2.code = "DOC-CHK-2";
        dt2.name = "Checklist Doc 2";
        DocumentType t2 = service.configureDocumentType(adminTenantA, dt2);

        DefineStageRequirementRequest r1 = new DefineStageRequirementRequest();
        r1.academicStage = "EXAMINATION";
        r1.documentTypeId = t1.id;
        r1.isMandatory = true;
        service.defineStageRequirement(adminTenantA, r1);

        DefineStageRequirementRequest r2 = new DefineStageRequirementRequest();
        r2.academicStage = "EXAMINATION";
        r2.documentTypeId = t2.id;
        r2.isMandatory = false;
        service.defineStageRequirement(adminTenantA, r2);

        // Upload only Doc 1
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t1.id;
        up.title = "Submitted Doc 1";
        up.mimeType = "application/pdf";
        up.sizeBytes = 5000L;
        service.uploadStudentDocument(studentTenantA, up);

        List<StudentChecklistItem> checklist = service.getStudentChecklist(studentTenantA, studentTenantA.getUserId(), "EXAMINATION", null);
        assertEquals(2, checklist.size());

        StudentChecklistItem item1 = checklist.get(0).requirement.documentTypeId.equals(t1.id) ? checklist.get(0) : checklist.get(1);
        StudentChecklistItem item2 = checklist.get(0).requirement.documentTypeId.equals(t2.id) ? checklist.get(0) : checklist.get(1);

        assertEquals("SUBMITTED", item1.status);
        assertEquals(1, item1.submittedDocuments.size());
        assertEquals("PENDING", item2.status);
        assertEquals(0, item2.submittedDocuments.size());
    }

    // ==========================================
    // STM-06-US-004: Document Verification Tests
    // ==========================================

    @Test
    public void officerCanRetrieveVerificationQueue() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-QUEUE-TEST";
        dt.name = "Queue Test Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Student Submission for Queue";
        up.mimeType = "application/pdf";
        up.sizeBytes = 4096L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        List<VerificationQueueItem> queue = service.getVerificationQueue(adminTenantA, "SUBMITTED");
        assertNotNull(queue);
        boolean found = false;
        for (VerificationQueueItem item : queue) {
            if (item.documentId.equals(uploaded.id)) {
                found = true;
                assertEquals("SUBMITTED", item.status);
                assertEquals("DOC-QUEUE-TEST", item.documentTypeCode);
                assertEquals("Student Submission for Queue", item.title);
                assertEquals(studentTenantA.getUserId(), item.studentId);
            }
        }
        assertTrue("Uploaded document should appear in verification queue", found);
    }

    @Test
    public void officerCanApproveSubmittedDocument() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-VERIFY-APP";
        dt.name = "Verify Approve Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Document To Approve";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";
        dec.verificationCode = "VERIFIED-APP-001";
        dec.verificationNotes = "All seals and signatures verified";

        DocumentVerificationRecord record = service.verifyDocument(adminTenantA, uploaded.id, dec);
        assertNotNull(record);
        assertEquals("VALID", record.result);
        assertEquals("VERIFIED-APP-001", record.verificationCode);
        assertEquals(adminTenantA.getUserId(), record.createdBy);

        StudentDocument updatedDoc = persistence.findStudentDocumentById(adminTenantA, uploaded.id);
        assertNotNull(updatedDoc);
        assertEquals("APPROVED", updatedDoc.status);
    }

    @Test
    public void officerCanRejectSubmittedDocumentWithReason() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-VERIFY-REJ";
        dt.name = "Verify Reject Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Document To Reject";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = "Page 2 missing official institution stamp";

        DocumentVerificationRecord record = service.verifyDocument(registrarTenantA, uploaded.id, dec);
        assertNotNull(record);
        assertEquals("INVALID", record.result);
        assertEquals("Page 2 missing official institution stamp", record.notes);

        StudentDocument updatedDoc = persistence.findStudentDocumentById(registrarTenantA, uploaded.id);
        assertNotNull(updatedDoc);
        assertEquals("REJECTED", updatedDoc.status);
    }

    @Test
    public void officerCannotRejectWithoutReason() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-VERIFY-NOREASON";
        dt.name = "No Reason Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Doc Test No Reason";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = ""; // Missing reason

        try {
            service.verifyDocument(adminTenantA, uploaded.id, dec);
            fail("Expected DocumentException(422) for missing rejection reason");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("REJECTION_REASON_REQUIRED", e.getCode());
        }
    }

    @Test
    public void cannotVerifyNonExistentDocument() {
        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";

        try {
            service.verifyDocument(adminTenantA, UUID.randomUUID(), dec);
            fail("Expected DocumentException(404) for non-existent document ID");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_NOT_FOUND", e.getCode());
        }
    }

    @Test
    public void studentCannotVerifyDocument() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-STUDENT-DENY";
        dt.name = "Student Deny Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Student Self Verify Attempt";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";

        try {
            service.verifyDocument(studentTenantA, uploaded.id, dec);
            fail("Expected DocumentException(403) when student attempts verification");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void checklistReflectsVerifiedStatusWhenApproved() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-CHK-VER";
        dt.name = "Checklist Verified Doc";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "GRADUATION";
        req.documentTypeId = t.id;
        req.isMandatory = true;
        service.defineStageRequirement(adminTenantA, req);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Graduation Clearance Form";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        // Before decision: checklist status is SUBMITTED
        List<StudentChecklistItem> before = service.getStudentChecklist(studentTenantA, studentTenantA.getUserId(), "GRADUATION", null);
        assertEquals(1, before.size());
        assertEquals("SUBMITTED", before.get(0).status);

        // Approve document
        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";
        service.verifyDocument(adminTenantA, uploaded.id, dec);

        // After decision: checklist status is VERIFIED
        List<StudentChecklistItem> after = service.getStudentChecklist(studentTenantA, studentTenantA.getUserId(), "GRADUATION", null);
        assertEquals(1, after.size());
        assertEquals("VERIFIED", after.get(0).status);
    }

    @Test
    public void testResubmitStudentDocument_Success() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-RESUBMIT-1";
        dt.name = "Resubmission Test Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Original Birth Certificate";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        assertEquals(1, uploaded.currentVersionNo);
        assertEquals("SUBMITTED", uploaded.status);

        // Reject it
        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = "Scan is blurry and unreadable";
        service.verifyDocument(adminTenantA, uploaded.id, dec);

        List<StudentDocument> rejectedList = service.listStudentDocuments(studentTenantA, studentTenantA.getUserId(), "REJECTED");
        assertEquals(1, rejectedList.size());
        assertEquals("REJECTED", rejectedList.get(0).status);

        // Student resubmits corrected document (v2)
        ResubmitDocumentRequest resubmitReq = new ResubmitDocumentRequest();
        resubmitReq.fileName = "birth_certificate_hd.pdf";
        resubmitReq.mimeType = "application/pdf";
        resubmitReq.sizeBytes = 4096L;
        resubmitReq.checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        resubmitReq.changeNote = "Re-scanned at 300 DPI high resolution with visible official seal";

        StudentDocument resubmitted = service.resubmitStudentDocument(studentTenantA, uploaded.id, resubmitReq);
        assertNotNull(resubmitted);
        assertEquals(2, resubmitted.currentVersionNo);
        assertEquals("SUBMITTED", resubmitted.status);
        assertEquals(4096L, resubmitted.sizeBytes);

        // Verify version history contains both v1 and v2
        List<DocumentVersion> versions = persistence.listDocumentVersions(studentTenantA, uploaded.id);
        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).versionNo);
        assertEquals(2, versions.get(1).versionNo);
        assertEquals("Re-scanned at 300 DPI high resolution with visible official seal", versions.get(1).changeNote);
    }

    @Test
    public void testResubmitStudentDocument_UnauthorizedCaller_ThrowsForbidden() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-AUTH-CHECK";
        dt.name = "Auth Check Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Student A Document";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = "Needs correction";
        service.verifyDocument(adminTenantA, uploaded.id, dec);

        Actor anotherStudent = new Actor(UUID.fromString("a0000000-0000-0000-0000-000000000001"), UUID.randomUUID(), "STUDENT", "fraud@campx.edu");

        ResubmitDocumentRequest resubmitReq = new ResubmitDocumentRequest();
        resubmitReq.mimeType = "application/pdf";
        resubmitReq.sizeBytes = 2048L;
        resubmitReq.checksumSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        resubmitReq.changeNote = "Fraudulent resubmission attempt";

        try {
            service.resubmitStudentDocument(anotherStudent, uploaded.id, resubmitReq);
            fail("Expected DocumentException(403) when another student attempts resubmission");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void testResubmitStudentDocument_AlreadyApproved_ThrowsConflict() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-APPROVED-RESUBMIT";
        dt.name = "Approved Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Approved Certificate";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";
        service.verifyDocument(adminTenantA, uploaded.id, dec);

        ResubmitDocumentRequest resubmitReq = new ResubmitDocumentRequest();
        resubmitReq.mimeType = "application/pdf";
        resubmitReq.sizeBytes = 2048L;
        resubmitReq.checksumSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        resubmitReq.changeNote = "Attempt to overwrite approved document";

        try {
            service.resubmitStudentDocument(studentTenantA, uploaded.id, resubmitReq);
            fail("Expected DocumentException(409) when attempting to resubmit already approved document");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("DOCUMENT_ALREADY_APPROVED", e.getCode());
        }
    }

    @Test
    public void testResubmitStudentDocument_ValidationErrors() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-VAL-CHECK";
        dt.name = "Validation Check Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Validation Test Document";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        ResubmitDocumentRequest r1 = new ResubmitDocumentRequest();
        r1.mimeType = "application/pdf";
        r1.sizeBytes = 1024L;
        r1.checksumSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        r1.changeNote = "";

        try {
            service.resubmitStudentDocument(studentTenantA, uploaded.id, r1);
            fail("Expected DocumentException(422) for empty change note");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_CHANGE_NOTE", e.getCode());
        }

        ResubmitDocumentRequest r2 = new ResubmitDocumentRequest();
        r2.mimeType = "application/x-msdownload";
        r2.sizeBytes = 1024L;
        r2.checksumSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        r2.changeNote = "Valid note";

        try {
            service.resubmitStudentDocument(studentTenantA, uploaded.id, r2);
            fail("Expected DocumentException(422) for disallowed MIME type");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_MIME_TYPE", e.getCode());
        }
    }

    @Test
    public void testGetLatestVerification_Success() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-VERIF-QUERY";
        dt.name = "Verification Query Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Queryable Document";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = "Signature missing on page 2";
        dec.verificationCode = "REJ-NO-SIG";
        service.verifyDocument(adminTenantA, uploaded.id, dec);

        DocumentVerificationRecord record = service.getLatestVerification(studentTenantA, uploaded.id);
        assertNotNull(record);
        assertEquals("INVALID", record.result);
        assertEquals("REJ-NO-SIG", record.verificationCode);
        assertEquals("Signature missing on page 2", record.notes);
    }

    @Test
    public void testRecordAccessLogAndAuditTrail_Success() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-AUDIT-TEST";
        dt.name = "Audit Test Document";
        dt.retentionPolicyCode = "RET_7_YEARS";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Audit Test Submission";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        // Record access
        DocumentDomainService.DocumentAccessLog log = service.recordAccessLog(studentTenantA, uploaded.id, "DOWNLOAD", "192.168.1.100");
        assertNotNull(log);
        assertEquals("DOWNLOAD", log.channel);
        assertEquals("DOWNLOADED", log.status);
        assertEquals("192.168.1.100", log.clientIp);
        assertEquals(studentTenantA.getUserId(), log.createdBy);

        // Retrieve audit trail
        List<DocumentDomainService.DocumentAccessLog> trail = service.getDocumentAuditTrail(studentTenantA, uploaded.id);
        assertNotNull(trail);
        assertEquals(1, trail.size());
        assertEquals(log.id, trail.get(0).id);
    }

    @Test
    public void testNonOwnerCannotAccessAuditTrail_403() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-FORBIDDEN-TEST";
        dt.name = "Forbidden Test Document";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Student A Document";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        Actor otherStudent = new Actor(tenantA, UUID.randomUUID(), "STUDENT", "other.student@campx.edu");
        try {
            service.getDocumentAuditTrail(otherStudent, uploaded.id);
            fail("Expected DocumentException(403) for non-owner student access to audit trail");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void testRetentionInfoCalculation() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-RETENTION-TEST";
        dt.name = "Retention Test Document";
        dt.retentionPolicyCode = "RET_7_YEARS";
        DocumentType t = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = t.id;
        up.title = "Retention Test Submission";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.RetentionInfo info = service.getRetentionInfo(studentTenantA, uploaded.id);
        assertNotNull(info);
        assertEquals("RET_7_YEARS", info.retentionPolicyCode);
        assertNotNull(info.retentionCutoff);
        assertFalse(info.isExpired);
    }
}
