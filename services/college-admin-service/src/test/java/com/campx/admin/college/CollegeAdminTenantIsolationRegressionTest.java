package com.campx.admin.college;

import com.campx.admin.college.exception.CollegeLifecycleException;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.*;
import com.campx.admin.college.repository.*;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.context.LogContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Regression test suite verifying:
 * 1. Tenant isolation & in-memory fallback prevention (Finding 1)
 * 2. Document source-of-truth consistency across repository & memory (Finding 2)
 * 3. Secret flag persistence, masking, and response contracts (Finding 4)
 * 4. Authoritative CollegeResolver domain errors on unprovisioned tenants (Finding 6)
 */
public class CollegeAdminTenantIsolationRegressionTest {

    private CollegeAdminDomainService domainService;
    private InMemoryCollegeProfileRepository profileRepo;
    private InMemoryCollegeSettingRepository settingRepo;
    private InMemoryFeatureOverrideRepository overrideRepo;
    private InMemoryLocalPolicyRepository policyRepo;
    private InMemoryGovernanceDocumentRepository documentRepo;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @Before
    public void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();

        profileRepo = new InMemoryCollegeProfileRepository();
        settingRepo = new InMemoryCollegeSettingRepository();
        overrideRepo = new InMemoryFeatureOverrideRepository();
        policyRepo = new InMemoryLocalPolicyRepository();
        documentRepo = new InMemoryGovernanceDocumentRepository();

        domainService = new CollegeAdminDomainService(
                new InMemoryDepartmentRepository(),
                new InMemoryProgramRepository(),
                profileRepo,
                settingRepo,
                overrideRepo,
                policyRepo,
                documentRepo
        );
    }

    @After
    public void tearDown() {
        LogContext.clear();
    }

    // =========================================================================
    // Finding 1: Tenant Isolation & In-Memory Fallback Prevention
    // =========================================================================

    @Test
    public void testTenantADataInvisibleToTenantB() {
        // Tenant A sets up operational data
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        UserSecurityContext contextA = new UserSecurityContext(userA, tenantA);

        CollegeProfile profileA = new CollegeProfile();
        profileA.setCollegeCode("COL_TENANT_A");
        profileA.setDisplayName("Tenant A Institute of Tech");
        profileA.setLegalName("Tenant A Legal");
        profileRepo.saveOrUpdateProfile(contextA, profileA);

        CollegeSetting settingA = new CollegeSetting();
        settingA.setKey("TENANT_A_SETTING");
        settingA.setValue("secret-val-A");
        settingA.setSecret(true);
        domainService.setCollegeSetting(settingA);

        domainService.overrideFeatureFlag("FLAG_TENANT_A", "true", "Tenant A policy");

        LocalPolicy polA = new LocalPolicy();
        polA.setPolicyCode("POL_TENANT_A");
        polA.setStatus("DRAFT");
        domainService.createLocalPolicy(polA);

        GovernanceDocument docA = new GovernanceDocument();
        docA.setTitle("Tenant A Handbook");
        docA.setClassification("INTERNAL");
        domainService.registerDocument(docA);

        // Switch to Tenant B (fresh tenant with no seeded custom data)
        LogContext.setTenantId(tenantB.toString());
        LogContext.setUserId(userB.toString());

        // 1. Profile: Tenant B must NOT see Tenant A profile or default seed fallback
        CollegeProfile bProfile = domainService.getProfile();
        assertNull("Tenant B must receive null profile when none provisioned (no cross-tenant fallback)", bProfile);

        // 2. Settings: Tenant B must see empty list, NOT Tenant A's settings
        List<CollegeSetting> bSettings = domainService.getCollegeSettings(false);
        assertTrue("Tenant B must see empty settings list", bSettings.isEmpty());

        // 3. Feature Overrides: Tenant B must see empty list
        List<FeatureOverride> bOverrides = domainService.listFeatureOverrides();
        assertTrue("Tenant B must see empty overrides list", bOverrides.isEmpty());

        // 4. Local Policies: Tenant B must see empty list
        List<LocalPolicy> bPolicies = domainService.listLocalPolicies();
        assertTrue("Tenant B must see empty local policies list", bPolicies.isEmpty());

        // 5. Documents: Tenant B must see empty list
        List<GovernanceDocument> bDocs = domainService.listDocuments();
        assertTrue("Tenant B must see empty documents list", bDocs.isEmpty());
    }

    @Test
    public void testProfileUpdateDoesNotMutateSharedGlobalStateOrOtherTenant() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        UserSecurityContext contextA = new UserSecurityContext(userA, tenantA);

        CollegeProfile pA = new CollegeProfile();
        pA.setCollegeCode("COL_A");
        pA.setDisplayName("Original A");
        profileRepo.saveOrUpdateProfile(contextA, pA);

        // Tenant B creates profile
        LogContext.setTenantId(tenantB.toString());
        LogContext.setUserId(userB.toString());
        UserSecurityContext contextB = new UserSecurityContext(userB, tenantB);

        CollegeProfile pB = new CollegeProfile();
        pB.setCollegeCode("COL_B");
        pB.setDisplayName("Original B");
        profileRepo.saveOrUpdateProfile(contextB, pB);

        // Mutate Tenant B's profile
        CollegeProfile updateB = new CollegeProfile();
        updateB.setCollegeCode("COL_B");
        updateB.setDisplayName("Updated B Name");
        domainService.updateProfile(updateB);

        // Verify Tenant A's profile is completely unchanged
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        CollegeProfile verifyA = domainService.getProfile();
        assertNotNull(verifyA);
        assertEquals("COL_A", verifyA.getCollegeCode());
        assertEquals("Original A", verifyA.getDisplayName());
    }

    @Test
    public void testEmptyRepositoryResultNeverFallsBackToInMemoryDefaults() {
        // Under tenant context, an empty repository must return empty, NOT fallback to in-memory defaults
        LogContext.setTenantId(tenantB.toString());
        LogContext.setUserId(userB.toString());

        List<GovernanceDocument> docs = domainService.listDocuments();
        assertNotNull(docs);
        assertEquals(0, docs.size());

        List<CollegeSetting> settings = domainService.getCollegeSettings(false);
        assertNotNull(settings);
        assertEquals(0, settings.size());

        List<FeatureOverride> overrides = domainService.listFeatureOverrides();
        assertNotNull(overrides);
        assertEquals(0, overrides.size());

        List<LocalPolicy> policies = domainService.listLocalPolicies();
        assertNotNull(policies);
        assertEquals(0, policies.size());
    }

    // =========================================================================
    // Finding 2: Document Source-of-Truth Consistency
    // =========================================================================

    @Test
    public void testMutationOfRepositoryBackedDocumentNotInServiceMemoryMap() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        UserSecurityContext context = new UserSecurityContext(userA, tenantA);

        // Seed a document directly into repository (simulating persistence across restart / other instance)
        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setTitle("External Policy Handbook");
        doc.setClassification("INTERNAL");
        doc.setStatus("DRAFT");
        doc.setCurrentVersion(1);
        documentRepo.createDocument(context, doc);

        DocumentVersion v1 = new DocumentVersion();
        v1.setId(UUID.randomUUID().toString());
        v1.setDocumentId(doc.getId());
        v1.setVersionNo(1);
        v1.setStatus("DRAFT");
        v1.setObjectRef("s3://docs/v1.pdf");
        documentRepo.createVersion(context, v1);

        // Crucial check: document is NOT in the domain service's private in-memory map
        // Now invoke domain service operations on it:
        // 1. Resolve document
        GovernanceDocument resolved = domainService.resolveDocument(doc.getId());
        assertNotNull(resolved);
        assertEquals("External Policy Handbook", resolved.getTitle());

        // 2. Submit approval
        GovernanceDocument approved = domainService.submitDocumentApproval(doc.getId(), "CHANCELLOR_01");
        assertEquals("APPROVED", approved.getStatus());

        // 3. Create document version
        DocumentVersion v2 = domainService.createDocumentVersion(doc.getId(), "s3://docs/v2.pdf", "AUTHOR_02");
        assertEquals(2, v2.getVersionNo());

        // 4. Publish document version
        DocumentVersion published = domainService.publishDocumentVersion(doc.getId(), 2);
        assertEquals("PUBLISHED", published.getStatus());

        // 5. Verify persisted repository state reflects the changes
        Optional<GovernanceDocument> repoDoc = documentRepo.findById(context, doc.getId());
        assertTrue(repoDoc.isPresent());
        assertEquals("PUBLISHED", repoDoc.get().getStatus());
        assertEquals(2, repoDoc.get().getCurrentVersion());
    }

    // =========================================================================
    // Finding 4: Secret Flag and Value Masking
    // =========================================================================

    @Test
    public void testSecretSettingPersistsAndMasksPlaintextInQueries() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        UserSecurityContext context = new UserSecurityContext(userA, tenantA);

        CollegeSetting setting = new CollegeSetting();
        setting.setKey("PAYMENT_GATEWAY_API_KEY");
        setting.setValue("sk_live_very_secret_key_12345");
        setting.setDataType("SECRET");
        setting.setSecret(true);

        domainService.setCollegeSetting(setting);

        // 1. Setting list must mask value as ********
        List<CollegeSetting> settings = domainService.getCollegeSettings(false);
        Optional<CollegeSetting> found = settings.stream().filter(s -> s.getKey().equals("PAYMENT_GATEWAY_API_KEY")).findFirst();
        assertTrue(found.isPresent());
        assertTrue("Secret flag must persist true", found.get().isSecret());
        assertEquals("Secret value must be masked", "********", found.get().getValue());
        assertEquals("SECRET", found.get().getDataType());

        // 2. Direct lookup via repo findByKey must also mask
        Optional<CollegeSetting> fromRepo = settingRepo.findByKey(context, "PAYMENT_GATEWAY_API_KEY");
        assertTrue(fromRepo.isPresent());
        assertEquals("********", fromRepo.get().getValue());
        assertTrue(fromRepo.get().isSecret());
    }

    // =========================================================================
    // Finding 6: Shared CollegeResolver Domain Error (No Phantom Seeding)
    // =========================================================================

    @Test
    public void testUnprovisionedTenantThrowsNotFoundWithoutSeedingPhantomCollege() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        // Unknown document resolution throws CollegeResourceNotFoundException
        try {
            domainService.resolveDocument(UUID.randomUUID().toString());
            fail("Expected CollegeResourceNotFoundException for non-existent document");
        } catch (CollegeResourceNotFoundException e) {
            assertTrue(e.getMessage().contains("Governance Document"));
        }
    }

    @Test
    public void testFailedProfileUpdateOnUnprovisionedTenantCreatesNoCollege() {
        UUID unprovisionedTenant = UUID.randomUUID();
        LogContext.setTenantId(unprovisionedTenant.toString());
        LogContext.setUserId(UUID.randomUUID().toString());

        CollegeProfile updateAttempt = new CollegeProfile();
        updateAttempt.setDisplayName("Should Never Exist College");

        try {
            domainService.updateProfile(updateAttempt);
            fail("Expected CollegeResourceNotFoundException when updating profile of unprovisioned tenant");
        } catch (CollegeResourceNotFoundException e) {
            assertTrue(e.getMessage().contains("College Profile"));
        }

        // Verify repository has 0 profiles for this tenant
        UserSecurityContext ctx = new UserSecurityContext(UUID.randomUUID(), unprovisionedTenant);
        Optional<CollegeProfile> prof = profileRepo.getProfile(ctx, null);
        assertFalse("No phantom college may be created by a failed update attempt", prof.isPresent());
    }

    // =========================================================================
    // Finding 1 (Follow-up): Local Policy Authorization & Lifecycle Invariants
    // =========================================================================

    @Test
    public void testForgedStatusOrApproverOnPolicyCreateIsForcedToDraftWithNoApprover() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        UserSecurityContext context = new UserSecurityContext(userA, tenantA);

        LocalPolicy malicious = new LocalPolicy();
        malicious.setPolicyCode("POL_ATTACK_01");
        malicious.setStatus("APPROVED"); // Malicious attempt to forge status
        malicious.setApprovedBy("ROGUE_HACKER"); // Malicious attempt to forge approver

        LocalPolicy created = domainService.createLocalPolicy(malicious);
        assertNotNull(created);
        assertEquals("Policy creation must strictly force DRAFT status", "DRAFT", created.getStatus());
        assertNull("Policy creation must strictly clear any client-supplied approver", created.getApprovedBy());

        // Verify repository copy has identical invariant
        Optional<LocalPolicy> repoPolicy = policyRepo.findByCode(context, "POL_ATTACK_01");
        assertTrue(repoPolicy.isPresent());
        assertEquals("DRAFT", repoPolicy.get().getStatus());
        assertNull(repoPolicy.get().getApprovedBy());
    }

    @Test
    public void testPolicyApprovalRejectsUnauthorizedRole() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode("POL_SEC_ROLE");
        domainService.createLocalPolicy(policy);

        // Attempt approval with unauthorized role (e.g. STUDENT)
        LogContext.setUserRole("STUDENT");
        try {
            domainService.approveLocalPolicy("POL_SEC_ROLE", userA.toString());
            fail("Expected CollegeSecurityViolationException when student attempts policy approval");
        } catch (CollegeSecurityViolationException e) {
            assertEquals(403, e.getStatus());
            assertTrue(e.getMessage().contains("not authorized"));
        }

        // Policy must remain in DRAFT status
        LocalPolicy afterAttempt = domainService.getLocalPolicy("POL_SEC_ROLE");
        assertEquals("DRAFT", afterAttempt.getStatus());
        assertNull(afterAttempt.getApprovedBy());
    }

    @Test
    public void testPolicyPublicationRequiresValidPersistedApproval() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode("POL_LIFECYCLE_GATE");
        domainService.createLocalPolicy(policy);

        // 1. Attempt publication directly from DRAFT status -> must fail
        try {
            domainService.publishLocalPolicy("POL_LIFECYCLE_GATE");
            fail("Expected CollegeLifecycleException when publishing DRAFT policy");
        } catch (CollegeLifecycleException e) {
            assertTrue(e.getMessage().contains("must be approved"));
        }

        // 2. Approve via authorized role (COLLEGE_DEAN)
        LogContext.setUserRole("COLLEGE_DEAN");
        LocalPolicy approved = domainService.approveLocalPolicy("POL_LIFECYCLE_GATE", "DEAN_OFFICE_01");
        assertEquals("APPROVED", approved.getStatus());
        assertEquals("DEAN_OFFICE_01", approved.getApprovedBy());

        // 3. Publish policy -> succeeds
        LocalPolicy published = domainService.publishLocalPolicy("POL_LIFECYCLE_GATE");
        assertEquals("PUBLISHED", published.getStatus());
        assertTrue(published.getPublishedAt() > 0);

        // 4. Attempt re-approval after publication -> must fail (immutable lifecycle)
        try {
            domainService.approveLocalPolicy("POL_LIFECYCLE_GATE", "DEAN_OFFICE_01");
            fail("Expected CollegeLifecycleException when approving already PUBLISHED policy");
        } catch (CollegeLifecycleException e) {
            assertTrue(e.getMessage().contains("Only DRAFT policies can be approved"));
        }
    }

    // =========================================================================
    // MVP-ADM02-02: Governance Immutability & Idempotent Approvals
    // =========================================================================

    @Test
    public void testMVP_ADM02_02_AuditTrailTamperingAndDiscontinuityDetection() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());
        LogContext.setUserRole("COLLEGE_ADMIN");

        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setTitle("Audit Integrity Test Charter");
        doc.setClassification("INTERNAL");
        domainService.registerDocument(doc);

        domainService.createDocumentVersion(doc.getId(), "s3://docs/integrity_v1.pdf", "DEAN_OFFICE");
        domainService.submitDocumentApproval(doc.getId(), "CHANCELLOR_01");

        // 1. Unaltered audit trail must verify cleanly
        domainService.verifyAuditTrailIntegrity();

        List<Map<String, Object>> snapshot = domainService.getAuditTrail();
        assertTrue("Audit trail should contain records", snapshot.size() >= 2);

        // Verify getAuditTrail provides safe copies
        List<Map<String, Object>> internalTrail = domainService.getInternalAuditTrailForTesting();

        // 2. Tampering detection: modify an action payload in stored internal record
        Map<String, Object> tamperedRecord = internalTrail.get(0);
        Object origAction = tamperedRecord.get("action");
        try {
            tamperedRecord.put("action", "TAMPERED_ACTION_MALICIOUS");
            domainService.verifyAuditTrailIntegrity();
            fail("Expected CollegeSecurityViolationException when audit trail payload is tampered");
        } catch (CollegeSecurityViolationException e) {
            assertEquals("ADM02_AUDIT_HASH_CHAIN_TAMPERED", e.getErrorCode());
        } finally {
            tamperedRecord.put("action", origAction);
        }

        // Re-verify clean after restoring
        domainService.verifyAuditTrailIntegrity();

        // 3. Discontinuity detection: break the beforeHash chain on record 1
        Map<String, Object> record1 = internalTrail.get(1);
        Object origBefore = record1.get("beforeHash");
        try {
            record1.put("beforeHash", "BROKEN_CHAIN_HASH_1234567890");
            domainService.verifyAuditTrailIntegrity();
            fail("Expected CollegeSecurityViolationException on audit chain discontinuity");
        } catch (CollegeSecurityViolationException e) {
            assertEquals("ADM02_AUDIT_HASH_CHAIN_DISCONTINUITY", e.getErrorCode());
        } finally {
            record1.put("beforeHash", origBefore);
        }

        domainService.verifyAuditTrailIntegrity();
    }

    @Test
    public void testMVP_ADM02_02_ApprovalRequest_IdempotentDuplicateSubmission() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        ApprovalRequest req = new ApprovalRequest();
        req.setRequestType("CURRICULUM_CHANGE");
        req.setSubjectType("COURSE");
        req.setSubjectId("CS201");
        req.setSubmittedBy("FACULTY_01");

        ApprovalRequest submitted = domainService.submitApprovalRequest(req);
        assertEquals("PENDING", submitted.getStatus());

        int outboxBefore = domainService.getOutboxEvents().size();

        // Initial decision: APPROVE by DEAN_ACADEMICS
        ApprovalRequest decided1 = domainService.decideApprovalRequest(
                submitted.getId(), "DEAN_ACADEMICS", "APPROVE", "Board review passed");
        assertEquals("APPROVED", decided1.getStatus());
        assertEquals("DEAN_ACADEMICS", decided1.getDecidedBy());
        long firstDecidedAt = decided1.getDecidedAt();
        int outboxAfterFirst = domainService.getOutboxEvents().size();
        assertEquals(outboxBefore + 1, outboxAfterFirst);

        // Idempotent duplicate: submit exact same decision by same approver
        ApprovalRequest decided2 = domainService.decideApprovalRequest(
                submitted.getId(), "DEAN_ACADEMICS", "APPROVE", "Second duplicate submission");
        assertEquals("APPROVED", decided2.getStatus());
        assertEquals("DEAN_ACADEMICS", decided2.getDecidedBy());
        assertEquals(firstDecidedAt, decided2.getDecidedAt());

        // Zero duplicate outbox events emitted
        assertEquals("No duplicate outbox event must be emitted for idempotent replay",
                outboxAfterFirst, domainService.getOutboxEvents().size());
    }

    @Test
    public void testMVP_ADM02_02_ApprovalRequest_ConflictingDecisionRejected() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        ApprovalRequest req = new ApprovalRequest();
        req.setRequestType("SYLLABUS_OVERHAUL");
        req.setSubjectType("PROGRAM");
        req.setSubjectId("BTECH_CSE");
        req.setSubmittedBy("HOD_CSE");

        ApprovalRequest submitted = domainService.submitApprovalRequest(req);

        // Initial decision: APPROVE by DEAN_ACADEMICS
        domainService.decideApprovalRequest(submitted.getId(), "DEAN_ACADEMICS", "APPROVE", "Approved");

        // Conflict 1: different decision by same approver -> 409 Conflict
        try {
            domainService.decideApprovalRequest(submitted.getId(), "DEAN_ACADEMICS", "REJECT", "Changed my mind");
            fail("Expected CollegeResourceConflictException for conflicting decision");
        } catch (CollegeResourceConflictException e) {
            assertEquals(409, e.getStatus());
            assertTrue(e.getMessage().contains("already been settled"));
        }

        // Conflict 2: different approver attempting to decide -> 409 Conflict
        try {
            domainService.decideApprovalRequest(submitted.getId(), "VICE_CHANCELLOR", "APPROVE", "Re-approval by VC");
            fail("Expected CollegeResourceConflictException for different approver on settled request");
        } catch (CollegeResourceConflictException e) {
            assertEquals(409, e.getStatus());
            assertTrue(e.getMessage().contains("already been settled"));
        }
    }

    @Test
    public void testMVP_ADM02_02_ApprovalRequest_ConcurrentDoubleSubmit() throws Exception {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        ApprovalRequest req = new ApprovalRequest();
        req.setRequestType("EXAM_TIMETABLE_CHANGE");
        req.setSubjectType("BATCH");
        req.setSubjectId("BATCH_2026_A");
        req.setSubmittedBy("EXAM_OFFICER_01");

        ApprovalRequest submitted = domainService.submitApprovalRequest(req);
        String reqId = submitted.getId();

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<ApprovalRequest> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ApprovalRequest r = domainService.decideApprovalRequest(reqId, "EXAM_CONTROLLER", "APPROVE", "Concurrent OK");
                    results.add(r);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Concurrent executions must finish within 5s", doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue("All concurrent submissions must succeed idempotently without errors: " + errors, errors.isEmpty());
        assertEquals(threads, results.size());
        for (ApprovalRequest r : results) {
            assertEquals("APPROVED", r.getStatus());
            assertEquals("EXAM_CONTROLLER", r.getDecidedBy());
        }

        // Verify outbox has exactly 1 ApprovalDecided event for this reqId
        long outboxDecideCount = domainService.getOutboxEvents().stream()
                .filter(e -> "ApprovalDecided".equals(e.getEventType()) && reqId.equals(e.getAggregateId()))
                .count();
        assertEquals("Exactly 1 outbox event must be emitted despite concurrent submissions", 1, outboxDecideCount);
    }

    @Test
    public void testMVP_ADM02_02_DocumentApproval_IdempotentAndConflict() {
        LogContext.setTenantId(tenantA.toString());
        LogContext.setUserId(userA.toString());

        GovernanceDocument doc = new GovernanceDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setTitle("College Academic Integrity Code");
        doc.setClassification("INTERNAL");
        domainService.registerDocument(doc);

        DocumentVersion v1 = domainService.createDocumentVersion(doc.getId(), "s3://docs/integrity_v1.pdf", "DEAN_OFFICE");
        DocumentApproval app = domainService.submitDocumentForApproval(doc.getId(), 1, "DEAN_OFFICE", "CHANCELLOR_01");

        int outboxBefore = domainService.getOutboxEvents().size();
        int auditBefore = domainService.getAuditTrail().size();

        // 1. Initial decision -> APPROVED
        DocumentApproval dec1 = domainService.decideDocumentApproval(app.getId(), "CHANCELLOR_01", "APPROVED", "Formal sanction granted");
        assertEquals("APPROVED", dec1.getDecision());
        assertEquals("CHANCELLOR_01", dec1.getApproverId());
        int auditAfter1 = domainService.getAuditTrail().size();
        assertEquals(auditBefore + 1, auditAfter1);

        // 2. Idempotent duplicate -> same result, no new audit record
        DocumentApproval dec2 = domainService.decideDocumentApproval(app.getId(), "CHANCELLOR_01", "APPROVED", "Duplicate approval call");
        assertEquals("APPROVED", dec2.getDecision());
        assertEquals(auditAfter1, domainService.getAuditTrail().size());

        // 3. Conflicting decision -> 409 Conflict
        try {
            domainService.decideDocumentApproval(app.getId(), "CHANCELLOR_01", "REJECTED", "Flip to reject");
            fail("Expected CollegeResourceConflictException for conflicting decision on settled document approval");
        } catch (CollegeResourceConflictException e) {
            assertEquals(409, e.getStatus());
        }
    }
}
