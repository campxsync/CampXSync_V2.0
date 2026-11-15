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
import com.campx.document.DocumentDomainService.UpdateDocumentMetadataRequest;
import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.InMemoryDocumentPersistence;
import com.campx.document.security.ControlledMalwareScanAdapter;
import com.campx.document.security.FakeDelegationPort;
import com.campx.document.security.SignedDownloadTokenService;
import com.campx.document.storage.DocumentStorageConfig;
import com.campx.document.storage.FakeDocumentObjectStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
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
    private FakeDelegationPort delegations;

    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    /** Matches default collegeId assigned on upload when request omits collegeId. */
    private final UUID campusA = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private final UUID campusB = UUID.fromString("b0000000-0000-0000-0000-000000000099");

    private final Actor adminTenantA = new Actor(tenantA, UUID.randomUUID(), campusA, "COLLEGE_ADMIN", "admin.a@campx.edu", null);
    private Actor registrarTenantA;
    private Actor registrarR31TenantA;
    private final Actor studentTenantA = new Actor(tenantA, UUID.randomUUID(), campusA, "STUDENT", "student.a@campx.edu", null);
    private final Actor adminTenantB = new Actor(tenantB, UUID.randomUUID(), campusB, "COLLEGE_ADMIN", "admin.b@campx.edu", null);

    @Before
    public void setUp() {
        System.clearProperty("campx.stm.legacyRegistrarStandin");
        persistence = new InMemoryDocumentPersistence();
        intelligenceService = new DocumentIntelligenceService();
        delegations = new FakeDelegationPort();
        UUID registrarUser = UUID.randomUUID();
        UUID registrarR31User = UUID.randomUUID();
        registrarTenantA = new Actor(tenantA, registrarUser, campusA, "REGISTRAR", "registrar.a@campx.edu", null);
        registrarR31TenantA = new Actor(tenantA, registrarR31User, campusA, "R31", "registrar.r31@campx.edu", null);
        delegations.grant(tenantA, registrarUser, DocumentDomainService.DELEGATION_APPROVE_KEY);
        delegations.grant(tenantA, registrarR31User, DocumentDomainService.DELEGATION_APPROVE_KEY);
        service = new DocumentDomainService(persistence, intelligenceService, new ControlledMalwareScanAdapter(),
                new SignedDownloadTokenService(null),
                new FakeDocumentObjectStorage(DocumentStorageConfig.DEFAULT_BUCKET, false),
                delegations);
    }

    @After
    public void tearDown() {
        System.clearProperty("campx.stm.legacyRegistrarStandin");
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
    public void registrarCannotConfigureDocumentType_d12d() {
        // D12d: catalogue/config remains College Admin; Registrar does not inherit admin configure rights.
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-NATIONAL-ID";
        req.name = "Government Issued National ID / Passport";
        req.category = "IDENTIFICATION";
        req.defaultClassification = "RESTRICTED";
        req.requiresSignature = false;
        try {
            service.configureDocumentType(registrarTenantA, req);
            fail("Registrar must not configure document types");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
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
    public void registrarCannotDefineStageRequirement_d12d() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-MIGRATION";
        dtReq.name = "Migration Certificate";
        dtReq.category = "ACADEMIC";
        DocumentType dt = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ENROLLMENT";
        req.documentTypeId = dt.id;
        req.isMandatory = true;
        try {
            service.defineStageRequirement(registrarTenantA, req);
            fail("Registrar must not define stage requirements (College Admin catalogue)");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
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

        List<VerificationQueueItem> queue = service.getVerificationQueue(registrarTenantA, "SUBMITTED");
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

        DocumentVerificationRecord record = service.verifyDocument(registrarTenantA, uploaded.id, dec);
        assertNotNull(record);
        assertEquals("VALID", record.result);
        assertEquals("VERIFIED-APP-001", record.verificationCode);
        assertEquals(registrarTenantA.getUserId(), record.createdBy);

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
            service.verifyDocument(registrarTenantA, uploaded.id, dec);
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
            service.verifyDocument(registrarTenantA, UUID.randomUUID(), dec);
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
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
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
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

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
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

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
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

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
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

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
            assertEquals("UNSUPPORTED_MIME_TYPE", e.getCode());
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
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

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

    // --- STM-06 story-scoped coverage (US-001…016 partials) ---

    private DocumentType stm06Type(String code) {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = code;
        dt.name = "Story " + code;
        dt.retentionPolicyCode = "RET_1_YEAR";
        return service.configureDocumentType(adminTenantA, dt);
    }

    @Test
    public void stm06Us001_002_configureTypeAndStageRequirement() {
        DocumentType type = stm06Type("DOC-STORY-001");
        assertEquals("DOC-STORY-001", type.code);
        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = type.id;
        req.isMandatory = true;
        req.minCount = 1;
        req.maxCount = 1;
        StageDocumentRequirement stage = service.defineStageRequirement(adminTenantA, req);
        assertTrue(stage.isMandatory);
        assertEquals("ADMISSION", stage.academicStage);
    }

    @Test
    public void stm06Us003_004_005_checklistUploadAndStatus() {
        DocumentType type = stm06Type("DOC-STORY-003");
        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ADMISSION";
        req.documentTypeId = type.id;
        req.isMandatory = true;
        req.minCount = 1;
        req.maxCount = 1;
        StageDocumentRequirement stage = service.defineStageRequirement(adminTenantA, req);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.stageRequirementId = stage.id;
        up.title = "Admission ID";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        assertNotNull(uploaded.id);
        assertEquals("SUBMITTED", uploaded.status);

        List<StudentChecklistItem> checklist = service.getStudentChecklist(
                studentTenantA, studentTenantA.getUserId(), "ADMISSION", null);
        assertTrue(checklist.size() >= 1);
        List<StudentDocument> docs = service.listStudentDocuments(studentTenantA, studentTenantA.getUserId(), null);
        assertEquals(1, docs.size());
    }

    @Test
    public void stm06Us007_008_009_011_resubmitReviewAndVerificationHistory() {
        DocumentType type = stm06Type("DOC-STORY-007");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Proof";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest reject = new VerificationDecisionRequest();
        reject.decision = "REJECT";
        reject.rejectionReason = "Please rescan";
        service.verifyDocument(registrarTenantA, uploaded.id, reject);

        ResubmitDocumentRequest resubmit = new ResubmitDocumentRequest();
        resubmit.fileName = "proof-v2.pdf";
        resubmit.mimeType = "application/pdf";
        resubmit.sizeBytes = 2048L;
        resubmit.checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        resubmit.changeNote = "Clearer scan";
        StudentDocument replaced = service.resubmitStudentDocument(studentTenantA, uploaded.id, resubmit);
        assertEquals("SUBMITTED", replaced.status);

        List<VerificationQueueItem> queue = service.getVerificationQueue(registrarTenantA, null);
        assertTrue(queue.size() >= 1);

        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        approve.verificationCode = "VERIFIED-STORY-011";
        approve.verificationNotes = "OK";
        service.verifyDocument(registrarTenantA, uploaded.id, approve);

        DocumentVerificationRecord latest = service.getLatestVerification(studentTenantA, uploaded.id);
        assertNotNull(latest);
        // Must be the APPROVE row even if reject/approve share the same Instant ms (tie-break fix).
        assertEquals("VALID", latest.result);
        assertEquals("VERIFIED-STORY-011", latest.verificationCode);
    }

    @Test
    public void stm06Us014_016_configureExpiryAndEnforceRetentionRead() {
        ConfigureDocumentTypeRequest dt = new ConfigureDocumentTypeRequest();
        dt.code = "DOC-STORY-EXP";
        dt.name = "Expiring Proof";
        dt.retentionPolicyCode = "RET_1_YEAR";
        DocumentType type = service.configureDocumentType(adminTenantA, dt);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Expiring upload";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        up.expiresAt = Instant.parse("2026-12-31T00:00:00Z");
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.RetentionInfo info = service.getRetentionInfo(studentTenantA, uploaded.id);
        assertEquals("RET_1_YEAR", info.retentionPolicyCode);
        assertNotNull(info.retentionCutoff);
        assertFalse(info.isExpired);
        assertNotNull(info.expiresAt);
    }

    // --- STM-06-EN-001 Document metadata persistence / CRUD enablement ---

    @Test
    public void stm06En001_configureDocumentTypePersistsMetadata() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN001-" + UUID.randomUUID().toString().substring(0, 8);
        req.name = "EN-001 Metadata Type";
        req.retentionPolicyCode = "RET_1_YEAR";
        DocumentType created = service.configureDocumentType(adminTenantA, req);
        assertNotNull(created.id);
        assertEquals(req.code.toUpperCase(), created.code);
        assertEquals("EN-001 Metadata Type", created.name);
        List<DocumentType> listed = service.listDocumentTypes(adminTenantA, null, null);
        assertTrue(listed.stream().anyMatch(t -> created.id.equals(t.id)));
    }

    @Test
    public void stm06En001_studentCannotConfigureDocumentType() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN001-FORBIDDEN";
        req.name = "Should Fail";
        try {
            service.configureDocumentType(studentTenantA, req);
            fail("Students must not configure document types");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    // --- STM-06-EN-004 RLS / tenant isolation / document-level authorization ---

    @Test
    public void stm06En004_crossTenantDocumentTypeHidden() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN004-ISO-" + UUID.randomUUID().toString().substring(0, 8);
        req.name = "Tenant A Isolation Type";
        DocumentType created = service.configureDocumentType(adminTenantA, req);

        try {
            service.getDocumentType(adminTenantB, created.id);
            fail("Cross-tenant document type must not be readable");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
        }

        List<DocumentType> tenantBTypes = service.listDocumentTypes(adminTenantB, null, null);
        assertFalse(tenantBTypes.stream().anyMatch(t -> created.id.equals(t.id)));
    }

    @Test
    public void stm06En004_documentLevelAuthBlocksPeerStudent() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN004-AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        req.name = "Owner-Only Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, req);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Private submission";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        Actor peer = new Actor(tenantA, UUID.randomUUID(), "STUDENT", "peer.student@campx.edu");
        try {
            service.listStudentDocuments(peer, studentTenantA.getUserId(), null);
            fail("Peer student must not list another student's documents");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }

        try {
            service.getDocumentAuditTrail(peer, uploaded.id);
            fail("Peer student must not read document audit trail");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06En004_crossTenantUploadDenied() {
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN004-XT-" + UUID.randomUUID().toString().substring(0, 8);
        req.name = "Tenant A Type";
        DocumentType type = service.configureDocumentType(adminTenantA, req);

        Actor studentB = new Actor(tenantB, UUID.randomUUID(), "STUDENT", "student.b@campx.edu");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Cross-tenant attempt";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        try {
            service.uploadStudentDocument(studentB, up);
            fail("Tenant B student must not upload against Tenant A document type");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_TYPE_NOT_FOUND", e.getCode());
        }
    }

    // --- STM-06-US-012 document access permissions ---

    @Test
    public void stm06Us012_grantAndRevokeDocumentAccess() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-US012-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Access Grant Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Access controlled";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.GrantDocumentAccessRequest grant = new DocumentDomainService.GrantDocumentAccessRequest();
        grant.granteeId = UUID.randomUUID();
        grant.granteeType = "STAFF";
        grant.purpose = "VERIFICATION";
        DocumentDomainService.DocumentLink granted = service.grantDocumentAccess(registrarTenantA, uploaded.id, grant);
        assertEquals("ACCESS_GRANTED", granted.relation);
        assertEquals("STAFF", granted.entityType);

        List<DocumentDomainService.DocumentLink> grants = service.listDocumentAccessGrants(registrarTenantA, uploaded.id);
        assertTrue(grants.stream().anyMatch(g -> "ACCESS_GRANTED".equals(g.relation)));

        DocumentDomainService.DocumentLink revoked =
                service.revokeDocumentAccess(registrarTenantA, uploaded.id, grant.granteeId, "STAFF");
        assertEquals("ACCESS_REVOKED", revoked.relation);
    }

    @Test
    public void stm06Us012_studentCannotGrantAccess() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-US012-FORBID-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Forbidden Grant Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Owned";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.GrantDocumentAccessRequest grant = new DocumentDomainService.GrantDocumentAccessRequest();
        grant.granteeId = UUID.randomUUID();
        grant.granteeType = "EXTERNAL_AGENCY";
        try {
            service.grantDocumentAccess(studentTenantA, uploaded.id, grant);
            fail("Students must not grant document access");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    // --- STM-06-US-013 sharing consent ---

    @Test
    public void stm06Us013_recordAndWithdrawSharingConsent() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-US013-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Consent Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Consent controlled";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.SharingConsentRequest consent = new DocumentDomainService.SharingConsentRequest();
        consent.purpose = "TRANSCRIPT_SHARE";
        DocumentDomainService.DocumentLink recorded = service.recordSharingConsent(studentTenantA, uploaded.id, consent);
        assertEquals("SHARE_CONSENT", recorded.relation);
        assertTrue(recorded.entityType.startsWith("PURPOSE:"));

        DocumentDomainService.DocumentLink withdrawn =
                service.withdrawSharingConsent(studentTenantA, uploaded.id, "TRANSCRIPT_SHARE");
        assertEquals("SHARE_CONSENT_WITHDRAWN", withdrawn.relation);

        List<DocumentDomainService.DocumentLink> consents = service.listSharingConsents(studentTenantA, uploaded.id);
        assertTrue(consents.stream().anyMatch(c -> "SHARE_CONSENT_WITHDRAWN".equals(c.relation)));
    }

    @Test
    public void stm06Us013_peerStudentCannotConsentOnOthersDocument() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-US013-PEER-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Peer Consent Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Owner only";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        Actor peer = new Actor(tenantA, UUID.randomUUID(), "STUDENT", "peer@campx.edu");
        DocumentDomainService.SharingConsentRequest consent = new DocumentDomainService.SharingConsentRequest();
        consent.purpose = "EMPLOYMENT";
        try {
            service.recordSharingConsent(peer, uploaded.id, consent);
            fail("Peer student must not consent on another student's document");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    // --- STM-06-EN-005 cross-STM stage / checklist integration (STM-02/03; STM-09/10 via intel adapter) ---

    @Test
    public void stm06En005_admissionAndEnrollmentStageContracts() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN005-STAGE-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Cross-STM Stage Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest admission = new DefineStageRequirementRequest();
        admission.academicStage = "ADMISSION";
        admission.documentTypeId = type.id;
        admission.isMandatory = true;
        StageDocumentRequirement adm = service.defineStageRequirement(adminTenantA, admission);
        assertEquals("ADMISSION", adm.academicStage);

        DefineStageRequirementRequest enrollment = new DefineStageRequirementRequest();
        enrollment.academicStage = "ENROLLMENT";
        enrollment.documentTypeId = type.id;
        enrollment.isMandatory = false;
        StageDocumentRequirement enl = service.defineStageRequirement(adminTenantA, enrollment);
        assertEquals("ENROLLMENT", enl.academicStage);

        List<StageDocumentRequirement> admissionList =
                service.listStageRequirements(adminTenantA, null, "ADMISSION", null);
        assertTrue(admissionList.stream().anyMatch(r -> adm.id.equals(r.id)));

        List<StageDocumentRequirement> enrollmentList =
                service.listStageRequirements(adminTenantA, null, "ENROLLMENT", null);
        assertTrue(enrollmentList.stream().anyMatch(r -> enl.id.equals(r.id)));
    }

    @Test
    public void stm06En005_checklistBridgesEnrollmentStage() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN005-CHK-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Enrollment Checklist Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        DefineStageRequirementRequest req = new DefineStageRequirementRequest();
        req.academicStage = "ENROLLMENT";
        req.documentTypeId = type.id;
        req.isMandatory = true;
        service.defineStageRequirement(adminTenantA, req);

        List<StudentChecklistItem> checklist =
                service.getStudentChecklist(studentTenantA, studentTenantA.getUserId(), "ENROLLMENT", null);
        assertNotNull(checklist);
        assertTrue(checklist.stream().anyMatch(i -> type.id.equals(i.requirement.documentTypeId)));
    }

    // --- STM-06-EN-006 ops readiness / audit / frontend dual path ---

    @Test
    public void stm06En006_auditTrailSupportsOperationalReadiness() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN006-AUD-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Ops Audit Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Ops readiness upload";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        DocumentDomainService.DocumentAccessLog log =
                service.recordAccessLog(studentTenantA, uploaded.id, "VIEW", "127.0.0.1");
        assertNotNull(log.id);

        List<DocumentDomainService.DocumentAccessLog> trail =
                service.getDocumentAuditTrail(studentTenantA, uploaded.id);
        assertTrue(trail.stream().anyMatch(a -> log.id.equals(a.id)));
    }

    @Test
    public void stm06En006_unauthenticatedRoleCannotConfigureTypes() {
        Actor anon = new Actor(tenantA, UUID.randomUUID(), "UNKNOWN", "anon@campx.edu");
        ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
        req.code = "DOC-EN006-FORBIDDEN";
        req.name = "Should Fail";
        try {
            service.configureDocumentType(anon, req);
            fail("Unknown role must not configure document types");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    // --- STM-06-EN-003 secure upload/download, validation, malware, signed URL ---

    @Test
    public void stm06En003_cleanUploadRecordsMalwareScanPassedAndIssuesSignedUrl() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN003-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Secure Upload Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Clean PDF";
        up.mimeType = "application/pdf";
        up.sizeBytes = 2048L;
        up.fileName = "clean_record.pdf";
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        assertNotNull(uploaded.storagePath);

        List<DocumentDomainService.DocumentLink> scans = service.listSecurityScans(studentTenantA, uploaded.id);
        assertTrue(scans.stream().anyMatch(s -> "MALWARE_SCAN_PASSED".equals(s.relation)));

        DocumentDomainService.SignedDownloadUrl signed =
                service.issueSignedDownloadUrl(studentTenantA, uploaded.id);
        assertNotNull(signed.token);
        assertEquals("MALWARE_SCAN_PASSED", signed.scanStatus);
        assertFalse(signed.malwareLiveEndpoint);
        assertTrue(signed.downloadPath.contains("/api/v1/documents/download"));

        DocumentDomainService.SignedDownloadUrl resolved =
                service.resolveSignedDownload(studentTenantA, signed.token);
        assertEquals(uploaded.id, resolved.documentId);
        assertEquals(uploaded.storagePath, resolved.storagePath);
    }

    @Test
    public void stm06En003_malwareMarkerIsQuarantinedBeforePersist() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN003-MAL-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Malware Gate Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Eicar Probe";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        up.fileName = "eicar_test.pdf";
        try {
            service.uploadStudentDocument(studentTenantA, up);
            fail("EICAR-named upload must be quarantined");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("MALWARE_DETECTED", e.getCode());
        }
    }

    @Test
    public void stm06En003_blockedExtensionAndMimeMismatchRejected() {
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN003-EXT-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Extension Gate Doc";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest exe = new UploadStudentDocumentRequest();
        exe.documentTypeId = type.id;
        exe.title = "Bad Ext";
        exe.mimeType = "application/pdf";
        exe.sizeBytes = 100L;
        exe.fileName = "payload.exe";
        try {
            service.uploadStudentDocument(studentTenantA, exe);
            fail("Executable extension must be blocked");
        } catch (DocumentException e) {
            assertEquals("BLOCKED_FILE_EXTENSION", e.getCode());
        }

        UploadStudentDocumentRequest mismatch = new UploadStudentDocumentRequest();
        mismatch.documentTypeId = type.id;
        mismatch.title = "Mime Mismatch";
        mismatch.mimeType = "application/pdf";
        mismatch.sizeBytes = 100L;
        mismatch.fileName = "photo.png";
        try {
            service.uploadStudentDocument(studentTenantA, mismatch);
            fail("MIME/extension mismatch must be rejected");
        } catch (DocumentException e) {
            assertEquals("MIME_EXTENSION_MISMATCH", e.getCode());
        }
    }

    // --- STM-06-EN-002 object storage bytes (fake client; no live Supabase) ---

    @Test
    public void stm06En002_configuredStorageRequiresContentAndStoresBytes() throws Exception {
        FakeDocumentObjectStorage storage = new FakeDocumentObjectStorage(DocumentStorageConfig.DEFAULT_BUCKET, true);
        DocumentDomainService storageService = new DocumentDomainService(
                persistence, intelligenceService, new ControlledMalwareScanAdapter(),
                new SignedDownloadTokenService("test-secret"), storage);

        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN002-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Storage Doc";
        DocumentType type = storageService.configureDocumentType(adminTenantA, dtReq);

        UploadStudentDocumentRequest missing = new UploadStudentDocumentRequest();
        missing.documentTypeId = type.id;
        missing.title = "Needs Bytes";
        missing.mimeType = "application/pdf";
        missing.sizeBytes = 12L;
        missing.fileName = "needs.pdf";
        try {
            storageService.uploadStudentDocument(studentTenantA, missing);
            fail("Configured storage must require contentBase64");
        } catch (DocumentException e) {
            assertEquals("CONTENT_REQUIRED", e.getCode());
        }

        byte[] pdf = ("%PDF-1.4 test").getBytes(StandardCharsets.US_ASCII);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Stored PDF";
        up.mimeType = "application/pdf";
        up.sizeBytes = (long) pdf.length;
        up.fileName = "stored.pdf";
        up.checksumSha256 = sha256Hex(pdf);
        up.contentBase64 = Base64.getEncoder().encodeToString(pdf);
        StudentDocument uploaded = storageService.uploadStudentDocument(studentTenantA, up);
        assertEquals(1, storage.objectCount());
        assertTrue(uploaded.storagePath.startsWith("tenants/" + tenantA + "/"));

        DocumentDomainService.SignedDownloadUrl signed =
                storageService.issueSignedDownloadUrl(studentTenantA, uploaded.id);
        DocumentDomainService.DocumentDownloadPayload payload =
                storageService.downloadDocumentBytes(studentTenantA, signed.token);
        assertEquals(pdf.length, payload.bytes.length);
        assertEquals("application/pdf", payload.mimeType);
    }

    @Test
    public void stm06En002_negativeAuthTenantAndChecksumAndStorageFailure() throws Exception {
        FakeDocumentObjectStorage storage = new FakeDocumentObjectStorage(DocumentStorageConfig.DEFAULT_BUCKET, true);
        DocumentDomainService storageService = new DocumentDomainService(
                persistence, intelligenceService, new ControlledMalwareScanAdapter(),
                new SignedDownloadTokenService("test-secret"), storage);

        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN002-NEG-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Storage Neg";
        DocumentType type = storageService.configureDocumentType(adminTenantA, dtReq);

        try {
            storageService.uploadStudentDocument(null, new UploadStudentDocumentRequest());
            fail("Unauthenticated must fail");
        } catch (DocumentException e) {
            assertEquals(401, e.getStatusCode());
        }

        byte[] pdf = ("%PDF-1.4 aaa").getBytes(StandardCharsets.US_ASCII);
        UploadStudentDocumentRequest badChecksum = new UploadStudentDocumentRequest();
        badChecksum.documentTypeId = type.id;
        badChecksum.title = "Bad checksum";
        badChecksum.mimeType = "application/pdf";
        badChecksum.sizeBytes = (long) pdf.length;
        badChecksum.fileName = "bad.pdf";
        badChecksum.checksumSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        badChecksum.contentBase64 = Base64.getEncoder().encodeToString(pdf);
        try {
            storageService.uploadStudentDocument(studentTenantA, badChecksum);
            fail("Checksum mismatch must fail");
        } catch (DocumentException e) {
            assertEquals("CHECKSUM_MISMATCH", e.getCode());
        }

        UploadStudentDocumentRequest badSig = new UploadStudentDocumentRequest();
        badSig.documentTypeId = type.id;
        badSig.title = "Bad signature";
        badSig.mimeType = "application/pdf";
        byte[] notPdf = "NOT_A_PDF_FILE".getBytes(StandardCharsets.US_ASCII);
        badSig.sizeBytes = (long) notPdf.length;
        badSig.fileName = "nosig.pdf";
        badSig.checksumSha256 = sha256Hex(notPdf);
        badSig.contentBase64 = Base64.getEncoder().encodeToString(notPdf);
        try {
            storageService.uploadStudentDocument(studentTenantA, badSig);
            fail("MIME signature mismatch must fail");
        } catch (DocumentException e) {
            assertEquals("MIME_SIGNATURE_MISMATCH", e.getCode());
        }

        Actor peer = new Actor(tenantA, UUID.randomUUID(), "STUDENT", "peer@campx.edu");
        UploadStudentDocumentRequest ok = new UploadStudentDocumentRequest();
        ok.documentTypeId = type.id;
        ok.title = "Owner doc";
        ok.mimeType = "application/pdf";
        ok.sizeBytes = (long) pdf.length;
        ok.fileName = "owner.pdf";
        ok.checksumSha256 = sha256Hex(pdf);
        ok.contentBase64 = Base64.getEncoder().encodeToString(pdf);
        StudentDocument uploaded = storageService.uploadStudentDocument(studentTenantA, ok);
        DocumentDomainService.SignedDownloadUrl signed =
                storageService.issueSignedDownloadUrl(studentTenantA, uploaded.id);
        try {
            storageService.downloadDocumentBytes(peer, signed.token);
            fail("Peer student must not use owner signed token (actor mismatch / forbidden)");
        } catch (DocumentException e) {
            assertTrue(e.getStatusCode() == 403);
        }

        Actor crossTenant = new Actor(tenantB, UUID.randomUUID(), "STUDENT", "b@campx.edu");
        try {
            storageService.downloadDocumentBytes(crossTenant, signed.token);
            fail("Cross-tenant download must fail");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
        }

        storage.failNextOperation();
        UploadStudentDocumentRequest failPut = new UploadStudentDocumentRequest();
        failPut.documentTypeId = type.id;
        failPut.title = "Storage fail";
        failPut.mimeType = "application/pdf";
        failPut.sizeBytes = (long) pdf.length;
        failPut.fileName = "fail.pdf";
        failPut.checksumSha256 = sha256Hex(pdf);
        failPut.contentBase64 = Base64.getEncoder().encodeToString(pdf);
        try {
            storageService.uploadStudentDocument(studentTenantA, failPut);
            fail("Storage backend failure must surface");
        } catch (DocumentException e) {
            assertEquals(503, e.getStatusCode());
            assertEquals("STORAGE_UNAVAILABLE", e.getCode());
        }
    }

    @Test
    public void stm06En002_metadataOnlyAllowedWhenStorageNotConfigured() {
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        ConfigureDocumentTypeRequest dtReq = new ConfigureDocumentTypeRequest();
        dtReq.code = "DOC-EN002-META-" + UUID.randomUUID().toString().substring(0, 8);
        dtReq.name = "Meta Only";
        DocumentType type = service.configureDocumentType(adminTenantA, dtReq);
        up.documentTypeId = type.id;
        up.title = "Metadata only";
        up.mimeType = "application/pdf";
        up.sizeBytes = 128L;
        up.fileName = "meta.pdf";
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        assertNotNull(uploaded.id);
        try {
            service.downloadDocumentBytes(studentTenantA,
                    service.issueSignedDownloadUrl(studentTenantA, uploaded.id).token);
            fail("Unconfigured storage must not stream bytes");
        } catch (DocumentException e) {
            assertEquals("STORAGE_NOT_CONFIGURED", e.getCode());
        }
    }

    // --- R31 Registrar Phase A (D10 / D12a-d) ---

    @Test
    public void stm06R31_registrarR31CanApproveWithDelegationAndCampus() {
        DocumentType type = stm06Type("DOC-R31-OK");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "R31 Doc";
        up.mimeType = "application/pdf";
        up.sizeBytes = 100L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        approve.verificationCode = "R31-OK";
        DocumentVerificationRecord record = service.verifyDocument(registrarR31TenantA, uploaded.id, approve);
        assertEquals("VALID", record.result);
    }

    @Test
    public void stm06R31_collegeAdminDeniedAsRegistrarWhenFlagOff() {
        DocumentType type = stm06Type("DOC-R31-ADMIN");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Admin Denied";
        up.mimeType = "application/pdf";
        up.sizeBytes = 100L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        try {
            service.verifyDocument(adminTenantA, uploaded.id, approve);
            fail("COLLEGE_ADMIN must not act as Registrar when legacy flag is OFF");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06R31_collegeAdminAllowedAsRegistrarWhenLegacyFlagOn() {
        System.setProperty("campx.stm.legacyRegistrarStandin", "true");
        delegations.grant(tenantA, adminTenantA.getUserId(), DocumentDomainService.DELEGATION_APPROVE_KEY);
        DocumentType type = stm06Type("DOC-R31-LEGACY");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Legacy Admin";
        up.mimeType = "application/pdf";
        up.sizeBytes = 100L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);
        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        DocumentVerificationRecord record = service.verifyDocument(adminTenantA, uploaded.id, approve);
        assertEquals("VALID", record.result);
    }

    @Test
    public void stm06Us010_reverificationFromRejectedSuccess() {
        DocumentType type = stm06Type("DOC-RVR-010-REJ");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Reverify After Reject";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest reject = new VerificationDecisionRequest();
        reject.decision = "REJECT";
        reject.rejectionReason = "Expired seal";
        service.verifyDocument(registrarTenantA, uploaded.id, reject);

        StudentDocument fromRejected = service.requestReverification(studentTenantA, uploaded.id,
                "Registrar stamp updated on original");
        assertEquals("SUBMITTED", fromRejected.status);
        assertEquals(1, fromRejected.currentVersionNo);
    }

    @Test
    public void stm06Us010_reverificationFromApprovedSuccess() {
        DocumentType type = stm06Type("DOC-RVR-010-APP");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Reverify After Approve";
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        service.verifyDocument(registrarTenantA, uploaded.id, approve);

        StudentDocument fromApproved = service.requestReverification(studentTenantA, uploaded.id,
                "Need fresh verification for graduation desk");
        assertEquals("SUBMITTED", fromApproved.status);
        assertEquals(1, fromApproved.currentVersionNo);
    }

    @Test
    public void stm06Us010_unauthenticatedAndWrongRoleDenied() {
        DocumentType type = stm06Type("DOC-RVR-NEG-ROLE");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Role Negatives";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        try {
            service.requestReverification(null, uploaded.id, "Valid reason");
            fail("Unauthenticated must be denied");
        } catch (DocumentException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals("UNAUTHENTICATED", e.getCode());
        }

        Actor faculty = new Actor(tenantA, UUID.randomUUID(), campusA, "FACULTY", "f@campx.edu", null);
        try {
            service.requestReverification(faculty, uploaded.id, "Faculty attempt");
            fail("Faculty must be denied");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }

        try {
            service.requestReverification(registrarTenantA, uploaded.id, "Registrar attempt");
            fail("Registrar must not request student re-verification");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06Us010_wrongTenantOtherStudentAndAlreadySubmittedDenied() {
        DocumentType type = stm06Type("DOC-RVR-NEG-OWN");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Ownership Negatives";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        Actor otherStudent = new Actor(tenantA, UUID.randomUUID(), campusA, "STUDENT", "other@campx.edu", null);
        try {
            service.requestReverification(otherStudent, uploaded.id, "Not my document");
            fail("Other student must be forbidden");
        } catch (DocumentException e) {
            assertEquals(403, e.getStatusCode());
            assertEquals("FORBIDDEN", e.getCode());
        }

        Actor crossTenant = new Actor(tenantB, studentTenantA.getUserId(), campusB, "STUDENT", "xt@campx.edu", null);
        try {
            service.requestReverification(crossTenant, uploaded.id, "Cross tenant");
            fail("Cross-tenant must not find document");
        } catch (DocumentException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("DOCUMENT_NOT_FOUND", e.getCode());
        }

        try {
            service.requestReverification(studentTenantA, uploaded.id, "Already in queue");
            fail("SUBMITTED status must conflict");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("ALREADY_AWAITING_REVIEW", e.getCode());
        }
    }

    @Test
    public void stm06Us010_reasonValidationDenied() {
        DocumentType type = stm06Type("DOC-RVR-NEG-REASON");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Reason Negatives";
        up.mimeType = "application/pdf";
        up.sizeBytes = 512L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";
        service.verifyDocument(registrarTenantA, uploaded.id, dec);

        try {
            service.requestReverification(studentTenantA, uploaded.id, null);
            fail("Null reason must be rejected");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("MISSING_REASON", e.getCode());
        }

        try {
            service.requestReverification(studentTenantA, uploaded.id, "  ");
            fail("Blank reason must be rejected");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("MISSING_REASON", e.getCode());
        }

        try {
            service.requestReverification(studentTenantA, uploaded.id, "ab");
            fail("Short reason must be rejected");
        } catch (DocumentException e) {
            assertEquals(422, e.getStatusCode());
            assertEquals("INVALID_REASON", e.getCode());
        }
    }

    @Test
    public void stm06R31_deniedWithoutDelegationOutsideCampusSameActorAndWrongRole() {
        DocumentType type = stm06Type("DOC-R31-NEG");
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Negatives";
        up.mimeType = "application/pdf";
        up.sizeBytes = 100L;
        StudentDocument uploaded = service.uploadStudentDocument(studentTenantA, up);

        FakeDelegationPort empty = new FakeDelegationPort();
        DocumentDomainService noDeleg = new DocumentDomainService(persistence, intelligenceService,
                new ControlledMalwareScanAdapter(), new SignedDownloadTokenService(null),
                new FakeDocumentObjectStorage(DocumentStorageConfig.DEFAULT_BUCKET, false), empty);
        VerificationDecisionRequest approve = new VerificationDecisionRequest();
        approve.decision = "APPROVE";
        try {
            noDeleg.verifyDocument(registrarTenantA, uploaded.id, approve);
            fail("Missing delegation must fail closed");
        } catch (DocumentException e) {
            assertEquals("DELEGATION_REQUIRED", e.getCode());
        }

        Actor otherCampus = new Actor(tenantA, registrarTenantA.getUserId(), campusB, "REGISTRAR", "x@campx.edu", null);
        delegations.grant(tenantA, otherCampus.getUserId(), DocumentDomainService.DELEGATION_APPROVE_KEY);
        try {
            service.verifyDocument(otherCampus, uploaded.id, approve);
            fail("Outside campus must be denied");
        } catch (DocumentException e) {
            assertEquals("CAMPUS_SCOPE_DENIED", e.getCode());
        }

        Actor crossTenant = new Actor(tenantB, UUID.randomUUID(), campusA, "REGISTRAR", "xt@campx.edu", null);
        delegations.grant(tenantB, crossTenant.getUserId(), DocumentDomainService.DELEGATION_APPROVE_KEY);
        try {
            service.verifyDocument(crossTenant, uploaded.id, approve);
            fail("Cross-tenant must be denied");
        } catch (DocumentException e) {
            assertTrue(e.getStatusCode() == 403 || e.getStatusCode() == 404);
        }

        VerificationDecisionRequest reject = new VerificationDecisionRequest();
        reject.decision = "REJECT";
        reject.rejectionReason = "Need clearer scan";
        service.verifyDocument(registrarTenantA, uploaded.id, reject);
        try {
            service.verifyDocument(registrarTenantA, uploaded.id, approve);
            fail("Same actor verify then approve without resubmit must be denied");
        } catch (DocumentException e) {
            assertEquals("SAME_ACTOR_VERIFY_APPROVE", e.getCode());
        }

        Actor faculty = new Actor(tenantA, UUID.randomUUID(), campusA, "FACULTY", "f@campx.edu", null);
        try {
            service.verifyDocument(faculty, uploaded.id, approve);
            fail("Wrong role must be denied");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }

        try {
            service.verifyDocument(null, uploaded.id, approve);
            fail("Unauthenticated must be denied");
        } catch (DocumentException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    // --- STM-06-US-015 Document expiry in-app notifications ---

    private StudentDocument uploadExpiringDocument(String code, Instant expiresAt, UUID collegeId) {
        DocumentType type = stm06Type(code);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = "Expiring " + code;
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        up.expiresAt = expiresAt;
        if (collegeId != null) {
            up.collegeId = collegeId;
        }
        return service.uploadStudentDocument(studentTenantA, up);
    }

    @Test
    public void stm06Us015_registrarSeesExpiringAndInAppNotifications() {
        Instant soon = Instant.now().plus(14, ChronoUnit.DAYS);
        StudentDocument expiring = uploadExpiringDocument("DOC-EXP-015", soon, campusA);

        List<StudentDocument> expiringList = service.listExpiringDocuments(registrarTenantA, 30);
        assertTrue(expiringList.stream().anyMatch(d -> d.id.equals(expiring.id)));

        List<DocumentDomainService.DocumentExpiryNotification> notifications =
                service.listInAppNotifications(registrarTenantA);
        assertFalse(notifications.isEmpty());
        assertTrue(notifications.stream().anyMatch(n -> expiring.id.equals(n.documentId)
                && DocumentDomainService.NOTIFICATION_DOCUMENT_EXPIRING.equals(n.type)
                && !n.read));
    }

    @Test
    public void stm06Us015_studentDenied() {
        try {
            service.listExpiringDocuments(studentTenantA, 30);
            fail("Student must not list expiring documents");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }
        try {
            service.listInAppNotifications(studentTenantA);
            fail("Student must not list expiry notifications");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06Us015_facultyDenied() {
        Actor faculty = new Actor(tenantA, UUID.randomUUID(), campusA, "FACULTY", "faculty@campx.edu", null);
        try {
            service.listInAppNotifications(faculty);
            fail("Faculty must not list expiry notifications");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06Us015_wrongTenantEmpty() {
        Instant soon = Instant.now().plus(7, ChronoUnit.DAYS);
        uploadExpiringDocument("DOC-EXP-TENANT", soon, campusA);

        Actor registrarTenantB = new Actor(tenantB, UUID.randomUUID(), campusB, "REGISTRAR", "r.b@campx.edu", null);
        List<StudentDocument> expiring = service.listExpiringDocuments(registrarTenantB, 30);
        assertTrue(expiring.isEmpty());
        List<DocumentDomainService.DocumentExpiryNotification> notifications =
                service.listInAppNotifications(registrarTenantB);
        assertTrue(notifications.isEmpty());
    }

    @Test
    public void stm06Us015_otherCampusExcludedFromExpiringList() {
        Instant soon = Instant.now().plus(10, ChronoUnit.DAYS);
        uploadExpiringDocument("DOC-EXP-CAMPUS-A", soon, campusA);
        uploadExpiringDocument("DOC-EXP-CAMPUS-B", soon, campusB);

        List<StudentDocument> campusAOnly = service.listExpiringDocuments(registrarTenantA, 30);
        assertEquals(1, campusAOnly.size());
        assertEquals(campusA, campusAOnly.get(0).collegeId);
    }

    @Test
    public void stm06Us015_markNotificationRead() {
        Instant soon = Instant.now().plus(5, ChronoUnit.DAYS);
        StudentDocument doc = uploadExpiringDocument("DOC-EXP-READ", soon, campusA);
        service.listExpiringDocuments(registrarTenantA, 30);
        List<DocumentDomainService.DocumentExpiryNotification> notifications =
                service.listInAppNotifications(registrarTenantA);
        DocumentDomainService.DocumentExpiryNotification target = null;
        for (DocumentDomainService.DocumentExpiryNotification n : notifications) {
            if (doc.id.equals(n.documentId)) {
                target = n;
                break;
            }
        }
        assertNotNull(target);
        assertFalse(target.read);

        DocumentDomainService.DocumentExpiryNotification marked =
                service.markNotificationRead(registrarTenantA, target.id);
        assertTrue(marked.read);

        Actor otherRegistrar = new Actor(tenantA, UUID.randomUUID(), campusA, "R31", "other.r31@campx.edu", null);
        try {
            service.markNotificationRead(otherRegistrar, target.id);
            fail("Other registrar must not mark another user's notification");
        } catch (DocumentException e) {
            assertEquals("NOTIFICATION_NOT_FOUND", e.getCode());
        }
    }

    // --- STM-06-US-006 Update document metadata ---

    private StudentDocument uploadMetadataDoc(String code, String title) {
        DocumentType type = stm06Type(code);
        UploadStudentDocumentRequest up = new UploadStudentDocumentRequest();
        up.documentTypeId = type.id;
        up.title = title;
        up.mimeType = "application/pdf";
        up.sizeBytes = 1024L;
        return service.uploadStudentDocument(studentTenantA, up);
    }

    private UpdateDocumentMetadataRequest metadataReq(String note, String title, String classification) {
        UpdateDocumentMetadataRequest req = new UpdateDocumentMetadataRequest();
        req.correctionNote = note;
        req.title = title;
        req.classification = classification;
        return req;
    }

    @Test
    public void stm06Us006_registrarUpdatesSubmittedDocumentMetadata() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-SUB", "Original Submitted Title");
        StudentDocument updated = service.updateDocumentMetadata(registrarTenantA, doc.id,
                metadataReq("Corrected typo in title", "Corrected Submitted Title", null));
        assertEquals("Corrected Submitted Title", updated.title);
        assertEquals("SUBMITTED", updated.status);
        assertEquals(registrarTenantA.getUserId(), updated.updatedBy);
        assertTrue(updated.rowVersion > doc.rowVersion);
    }

    @Test
    public void stm06Us006_registrarUpdatesRejectedDocumentClassification() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-REJ", "Rejected Doc");
        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "REJECT";
        dec.rejectionReason = "Stamp missing on page 1";
        service.verifyDocument(registrarTenantA, doc.id, dec);

        UpdateDocumentMetadataRequest req = metadataReq("Reclassified after review", null, "RESTRICTED");
        StudentDocument updated = service.updateDocumentMetadata(registrarR31TenantA, doc.id, req);
        assertEquals("RESTRICTED", updated.classification);
        assertEquals("REJECTED", updated.status);
    }

    @Test
    public void stm06Us006_deniesApprovedDocument() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-APP", "Approved Doc");
        VerificationDecisionRequest dec = new VerificationDecisionRequest();
        dec.decision = "APPROVE";
        dec.verificationCode = "OK-001";
        service.verifyDocument(registrarTenantA, doc.id, dec);

        try {
            service.updateDocumentMetadata(registrarTenantA, doc.id,
                    metadataReq("Should not apply", "New Title", null));
            fail("Approved document must not allow metadata correction");
        } catch (DocumentException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("DOCUMENT_ALREADY_VERIFIED", e.getCode());
        }
    }

    @Test
    public void stm06Us006_studentAndFacultyDenied() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-ROLE", "Role Test");
        UpdateDocumentMetadataRequest req = metadataReq("Illegal attempt", "Hacked", null);

        try {
            service.updateDocumentMetadata(studentTenantA, doc.id, req);
            fail("Student must not correct metadata");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }

        Actor faculty = new Actor(tenantA, UUID.randomUUID(), campusA, "FACULTY", "faculty@campx.edu", null);
        try {
            service.updateDocumentMetadata(faculty, doc.id, req);
            fail("Faculty must not correct metadata");
        } catch (DocumentException e) {
            assertEquals("REGISTRAR_FORBIDDEN", e.getCode());
        }
    }

    @Test
    public void stm06Us006_crossTenantAndWrongCampusDenied() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-TENANT", "Tenant Scoped");
        UpdateDocumentMetadataRequest req = metadataReq("Cross tenant attempt", "X", null);

        Actor registrarTenantB = new Actor(tenantB, UUID.randomUUID(), campusB, "REGISTRAR", "r.b@campx.edu", null);
        try {
            service.updateDocumentMetadata(registrarTenantB, doc.id, req);
            fail("Cross-tenant metadata update must fail");
        } catch (DocumentException e) {
            assertEquals("DOCUMENT_NOT_FOUND", e.getCode());
        }

        Actor registrarOtherCampus = new Actor(tenantA, UUID.randomUUID(), campusB, "REGISTRAR", "r.c@campx.edu", null);
        try {
            service.updateDocumentMetadata(registrarOtherCampus, doc.id, req);
            fail("Wrong campus must be denied");
        } catch (DocumentException e) {
            assertEquals("CAMPUS_SCOPE_DENIED", e.getCode());
        }
    }

    @Test
    public void stm06Us006_nullActorBlankNoteInvalidClassification() {
        StudentDocument doc = uploadMetadataDoc("DOC-META-VAL", "Validation Doc");

        try {
            service.updateDocumentMetadata(null, doc.id, metadataReq("ok", "New", null));
            fail("Null actor must be denied");
        } catch (DocumentException e) {
            assertEquals(401, e.getStatusCode());
        }

        try {
            UpdateDocumentMetadataRequest shortNote = metadataReq("no", "New Title", null);
            service.updateDocumentMetadata(registrarTenantA, doc.id, shortNote);
            fail("Short correction note must be rejected");
        } catch (DocumentException e) {
            assertEquals("CORRECTION_NOTE_REQUIRED", e.getCode());
        }

        try {
            UpdateDocumentMetadataRequest badClass = metadataReq("Fix classification", null, "TOP_SECRET");
            service.updateDocumentMetadata(registrarTenantA, doc.id, badClass);
            fail("Invalid classification must be rejected");
        } catch (DocumentException e) {
            assertEquals("INVALID_CLASSIFICATION", e.getCode());
        }
    }

    // --- STM-CROSS-EN-010 discoverability (STM-06) ---

    @Test
    public void stmCrossEn010_crossTenantMutationDenied() {
        stm06En004_crossTenantUploadDenied();
    }

    @Test
    public void stmCrossEn010_wrongRoleDenied() {
        stm06Us010_unauthenticatedAndWrongRoleDenied();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
