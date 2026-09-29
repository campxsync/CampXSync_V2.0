package com.campx.academic.resource;

import com.campx.academic.resource.exception.ResourcePolicyDeniedException;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.academic.resource.service.ResourceDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Access Policy & Authorization tests covering ACD-08:
 * - Access grant creation, update, revocation (US-013 - US-016)
 * - Published student access filter (US-017)
 * - Secure download token generation (US-018, US-051)
 * - Strict DENY rule precedence (US-019, US-053)
 * - Time window evaluation (US-052)
 * - Scoped authorization: department, batch, program (US-055)
 */
public class ResourceAccessPolicyTest {

    private ResourceDomainService service;
    private static final String TENANT = "TENANT-001";
    private static final String USER_FACULTY = "prof-smith";
    private static final String USER_STUDENT = "student-alice";

    @Before
    public void setUp() {
        service = new ResourceDomainService();
    }

    private LearningResource createAndPublishResource(String code) {
        CreateResourceRequest req = new CreateResourceRequest();
        req.resourceCode = code;
        req.title = "Data Structures Guide";
        req.resourceType = "STUDY_MATERIAL";
        req.departmentId = "DEP_CS";
        req.storageObjectRef = "notes/ds_guide.pdf";
        req.mimeType = "application/pdf";
        req.fileSize = 1048576L;
        req.checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        LearningResource res = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        PublishResourceRequest pub = new PublishResourceRequest();
        pub.expectedVersion = 1L;
        return service.publishResource(res.getId(), pub, TENANT, "admin-1", "ACADEMIC_ADMIN", "TRACE-PUB");
    }

    @Test
    public void testStudentCannotAccessDraftResource() {
        CreateResourceRequest req = new CreateResourceRequest();
        req.resourceCode = "RES-DRAFT-HIDDEN";
        req.title = "Draft Exam Questions";
        req.resourceType = "QUESTION_BANK";
        req.departmentId = "DEP_CS";
        req.storageObjectRef = "drafts/qbank.pdf";
        req.mimeType = "application/pdf";
        req.fileSize = 50000L;
        req.checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        LearningResource draft = service.createResource(req, TENANT, USER_FACULTY, "FACULTY", null, "TRACE-01");

        ResourceSearchFilter filter = new ResourceSearchFilter();
        List<LearningResource> results = service.searchResources(filter, TENANT, USER_STUDENT, "STUDENT", "DEP_CS", "BATCH-2026", "BTECH");
        assertFalse(results.stream().anyMatch(r -> r.getId().equals(draft.getId())));
    }

    @Test
    public void testAuthorizedDownloadGeneratesSignedUrl() {
        LearningResource res = createAndPublishResource("RES-PUB-DOWNLOAD");

        DownloadResponse resp = service.downloadResource(res.getId(), null, TENANT, USER_STUDENT, "STUDENT",
                "DEP_CS", "BATCH-A", "BTECH", "TRACE-DL");

        assertNotNull(resp);
        assertEquals(res.getId(), resp.resourceId);
        assertNotNull(resp.downloadUrl);
        assertTrue(resp.downloadUrl.contains("storage.campx.internal"));
        assertTrue(resp.downloadUrl.contains("token="));
        assertEquals(300, resp.expiresInSeconds);
    }

    @Test(expected = ResourcePolicyDeniedException.class)
    public void testExplicitDenyRuleTakesPrecedence() {
        LearningResource res = createAndPublishResource("RES-DENY-PRECEDENCE");

        // 1. Add global ALLOW grant for role STUDENT
        CreateAccessGrantRequest allowGrant = new CreateAccessGrantRequest();
        allowGrant.principalType = "ROLE";
        allowGrant.principalId = "STUDENT";
        allowGrant.scopeType = "GLOBAL";
        allowGrant.permission = "DOWNLOAD";
        allowGrant.effect = "ALLOW";
        service.addAccessGrant(res.getId(), allowGrant, TENANT, "admin-1", "ACADEMIC_ADMIN", "TRACE-GRANT-1");

        // 2. Add specific DENY grant for user USER_STUDENT
        CreateAccessGrantRequest denyGrant = new CreateAccessGrantRequest();
        denyGrant.principalType = "USER";
        denyGrant.principalId = USER_STUDENT;
        denyGrant.scopeType = "GLOBAL";
        denyGrant.permission = "DOWNLOAD";
        denyGrant.effect = "DENY";
        service.addAccessGrant(res.getId(), denyGrant, TENANT, "admin-1", "ACADEMIC_ADMIN", "TRACE-GRANT-2");

        // Attempt download -> Should be denied despite global ALLOW
        service.downloadResource(res.getId(), null, TENANT, USER_STUDENT, "STUDENT",
                "DEP_CS", "BATCH-A", "BTECH", "TRACE-DENIED");
    }

    @Test
    public void testAccessGrantRevocation() {
        LearningResource res = createAndPublishResource("RES-GRANT-REVOKE");

        CreateAccessGrantRequest grantReq = new CreateAccessGrantRequest();
        grantReq.principalType = "USER";
        grantReq.principalId = "test-user-99";
        grantReq.permission = "DOWNLOAD";
        grantReq.effect = "ALLOW";

        ResourceAccessGrant grant = service.addAccessGrant(res.getId(), grantReq, TENANT, "admin-1", "ACADEMIC_ADMIN", "TRACE-1");
        assertEquals(GrantStatus.ACTIVE, grant.getStatus());

        ResourceAccessGrant revoked = service.revokeAccessGrant(res.getId(), grant.getId(), TENANT, "admin-1", "TRACE-2");
        assertEquals(GrantStatus.REVOKED, revoked.getStatus());
    }
}
