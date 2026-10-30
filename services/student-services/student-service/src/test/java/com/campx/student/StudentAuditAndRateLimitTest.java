package com.campx.student;

import com.campx.platform.audit.AuditChangeLog;
import com.campx.platform.audit.AuditRecord;
import com.campx.student.controller.StudentController;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryAuditLogRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.RateLimiter;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Targeted tests for STM-01 gap closure:
 * - Profile Change History (STM01-US-025)
 * - Auditing Privileged Profile Actions (STM01-US-026)
 * - Safe Search and Rate Limiting (STM01-US-038)
 * - Accessibility & Locale Preferences (STM01-US-045)
 * - Governed Profile Export / Reference Integration (STM01-US-049)
 * - Soft Deletion Preservation (STM01-US-039)
 * - Failure Handling & Rollback (STM01-US-040)
 */
public class StudentAuditAndRateLimitTest {

    private StudentDomainService service;
    private InMemoryAuditLogRepository auditRepo;
    private StudentController controller;
    private RateLimiter rateLimiter;

    private UserSecurityContext tenantContext;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UserSecurityContext crossTenantContext;

    private UUID tenantId;
    private UUID collegeId;
    private UUID staffId;
    private UUID studentUserId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();

        tenantContext = UserSecurityContext.fromHeaders(staffId.toString(), tenantId.toString(), collegeId.toString(), "college_admin");
        staffContext = tenantContext;
        studentContext = UserSecurityContext.fromHeaders(studentUserId.toString(), tenantId.toString(), collegeId.toString(), "student");
        crossTenantContext = UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "college_admin");

        StudentDomainService.RepositoryBundle bundle = StudentDomainService.createInMemoryBundle();
        this.auditRepo = (InMemoryAuditLogRepository) bundle.auditLogRepository;
        this.service = new StudentDomainService(bundle);
        this.rateLimiter = new RateLimiter(3, 60000); // 3 requests per minute for targeted test
        this.controller = new StudentController(service, rateLimiter);
    }

    private EnrollStudentRequest buildEnrollRequest(String enrollmentNo) {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setCollegeId(collegeId);
        req.setProgramId(UUID.randomUUID());
        req.setFirstName("Rohan");
        req.setLastName("Sharma");
        req.setPrimaryEmail("rohan." + enrollmentNo.toLowerCase() + "@campx.edu");
        req.setEnrollmentNo(enrollmentNo);
        return req;
    }

    @Test
    public void testProfileChangeHistoryRetrievalScopedToStudentAndTenant() {
        // STM01-US-025: View profile change history
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("HIST001"));
        service.linkIamAccount(staffContext, student.getId(), studentUserId);

        // Perform some updates
        UpdateStudentProfileRequest update = new UpdateStudentProfileRequest();
        update.setPreferredName("Rohan S.");
        service.updatePermittedFields(staffContext, student.getId(), update);

        // Student can view their own history
        List<ProfileChangeHistoryDto> myHistory = service.getProfileChangeHistory(studentContext, student.getId());
        assertNotNull("History must not be null", myHistory);
        assertFalse("History should contain recorded changes", myHistory.isEmpty());

        // Cross-student access is denied (403)
        UserSecurityContext otherStudent = UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), tenantId.toString(), collegeId.toString(), "student");
        try {
            service.getProfileChangeHistory(otherStudent, student.getId());
            fail("Cross-student history view must be rejected");
        } catch (StudentSecurityViolationException e) {
            assertTrue(e.getMessage().contains("only view change history for their own profile"));
        }

        // Controller endpoint test for GET /api/v1/students/{id}/history
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-Id", tenantId.toString());
        headers.put("X-User-Id", studentUserId.toString());
        headers.put("X-User-Role", "student");

        StudentController.HttpResponse resp = controller.getProfileChangeHistory(headers, student.getId().toString());
        assertEquals("History endpoint must return 200 OK", 200, resp.getStatusCode());
        assertTrue(resp.getBody().contains("STUDENT_ENROLLED") || resp.getBody().contains("PROFILE_UPDATED"));
    }

    @Test
    public void testPrivilegedActionsEmitAuditRecords() {
        // STM01-US-026: Audit privileged profile actions
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("AUDIT001"));

        // Verify enroll audit record in audit.events and change_log
        List<AuditRecord> records = auditRepo.getAuditRecords();
        assertFalse("Audit records must be captured", records.isEmpty());

        boolean foundEnroll = records.stream().anyMatch(r -> "STUDENT_ENROLLED".equals(r.getAction()) && student.getId().equals(r.getResourceId()));
        assertTrue("STUDENT_ENROLLED audit record must be recorded", foundEnroll);

        // Perform status update and verify STATUS_CHANGED audit
        UpdateStudentStatusRequest statusReq = new UpdateStudentStatusRequest();
        statusReq.setStatus(StudentStatus.SUSPENDED);
        statusReq.setReason("Disciplinary hold");
        service.transitionLifecycle(staffContext, student.getId(), statusReq);

        boolean foundStatus = auditRepo.getAuditRecords().stream().anyMatch(r -> "STATUS_CHANGED".equals(r.getAction()));
        assertTrue("STATUS_CHANGED audit record must be recorded", foundStatus);

        // Perform soft delete and verify STUDENT_DEACTIVATED audit
        service.deactivateStudent(staffContext, student.getId());
        boolean foundDeactivate = auditRepo.getAuditRecords().stream().anyMatch(r -> "STUDENT_DEACTIVATED".equals(r.getAction()));
        assertTrue("STUDENT_DEACTIVATED audit record must be recorded", foundDeactivate);
    }

    @Test
    public void testRosterSearchRateLimiterExhaustionReturns429() {
        // STM01-US-038: Apply safe search and rate limits
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-Id", tenantId.toString());
        headers.put("X-User-Id", staffId.toString());
        headers.put("X-User-Role", "college_admin");

        Map<String, String> params = new HashMap<>();
        params.put("limit", "10");

        // First 3 requests succeed (capacity = 3)
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());

        // 4th request must return 429 Too Many Requests
        StudentController.HttpResponse rateLimited = controller.getRoster(headers, params);
        assertEquals("4th request must be rate limited", 429, rateLimited.getStatusCode());
        assertTrue(rateLimited.getBody().contains("RATE_LIMIT_EXCEEDED"));
        assertNotNull("Retry-After header must be set", rateLimited.getHeaders().get("Retry-After"));
    }

    @Test
    public void testExportProjectionRestrictedToAuthorizedCallersAndSanitized() {
        // STM01-US-044 & STM01-US-049: Governed student profile export / projection
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("EXPORT001"));

        // Staff can export / view downstream projection
        StudentProjectionResponse projection = service.getDownstreamProjection(staffContext, student.getId());
        assertNotNull(projection);
        assertEquals(student.getId(), projection.getStudentId());
        assertEquals("EXPORT001", projection.getEnrollmentNo());

        // Student role is denied access to downstream intelligence export (403)
        try {
            service.getDownstreamProjection(studentContext, student.getId());
            fail("Student role cannot access internal export projection");
        } catch (StudentSecurityViolationException e) {
            assertTrue(e.getMessage().contains("cannot access internal intelligence projections"));
        }

        // Controller endpoint test: GET /api/v1/students/{id}/export
        Map<String, String> staffHeaders = new HashMap<>();
        staffHeaders.put("X-Tenant-Id", tenantId.toString());
        staffHeaders.put("X-User-Id", staffId.toString());
        staffHeaders.put("X-User-Role", "college_admin");

        StudentController.HttpResponse exportResp = controller.getDownstreamProjection(staffHeaders, student.getId().toString());
        assertEquals("Export endpoint must return 200 OK for authorized staff", 200, exportResp.getStatusCode());
        assertTrue(exportResp.getBody().contains("EXPORT001"));
        assertFalse("Export projection must not contain sensitive passwords or raw tokens", exportResp.getBody().contains("password"));
    }

    @Test
    public void testAccessibilityAndLocalePreferencesPersistence() {
        // STM01-US-045: Support accessibility and locale preferences
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("PREF001"));
        service.linkIamAccount(staffContext, student.getId(), studentUserId);

        StudentPreferencesDto prefs = new StudentPreferencesDto();
        prefs.setLocale("ta-IN");
        prefs.setTimezone("Asia/Kolkata");
        prefs.setCommunicationChannels(Arrays.asList("EMAIL", "PUSH"));
        Map<String, Object> access = new HashMap<>();
        access.put("screenReader", true);
        access.put("fontSize", "LARGE");
        access.put("highContrast", true);
        prefs.setAccessibilitySettings(access);

        // Student updates preferences
        StudentProfileResponse updated = service.updatePreferences(studentContext, student.getId(), prefs);
        assertNotNull(updated.getPreferences());
        assertEquals("ta-IN", updated.getPreferences().getLocale());
        assertEquals("Asia/Kolkata", updated.getPreferences().getTimezone());
        assertEquals(true, updated.getPreferences().getAccessibilitySettings().get("screenReader"));

        // Query preferences via dedicated getter
        StudentPreferencesDto retrieved = service.getPreferences(studentContext, student.getId());
        assertNotNull(retrieved);
        assertEquals("ta-IN", retrieved.getLocale());
        assertEquals("LARGE", retrieved.getAccessibilitySettings().get("fontSize"));

        // Controller endpoint test: GET /api/v1/students/{id}/preferences
        Map<String, String> studentHeaders = new HashMap<>();
        studentHeaders.put("X-Tenant-Id", tenantId.toString());
        studentHeaders.put("X-User-Id", studentUserId.toString());
        studentHeaders.put("X-User-Role", "student");

        StudentController.HttpResponse prefResp = controller.getPreferences(studentHeaders, student.getId().toString());
        assertEquals(200, prefResp.getStatusCode());
        assertTrue(prefResp.getBody().contains("ta-IN"));
    }

    @Test
    public void testSoftDeleteAuditedAndExcludedFromRoster() {
        // STM01-US-039: Soft deletion and lifecycle history preservation
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("DEL001"));

        // Active student appears in roster
        StudentRosterQuery query = new StudentRosterQuery();
        query.setSearch("DEL001");
        assertEquals(1, service.getRoster(staffContext, query).getItems().size());

        // Deactivate student (soft delete)
        service.deactivateStudent(staffContext, student.getId());

        // Deleted student must be excluded from active roster queries
        assertEquals(0, service.getRoster(staffContext, query).getItems().size());

        // Direct fetch returns 404
        try {
            service.getStudentById(staffContext, student.getId());
            fail("Deactivated student must not be returned by getStudentById");
        } catch (com.campx.student.exception.StudentNotFoundException e) {
            // Expected
        }
    }

    // =========================================================================
    // Rate Limiter Regression Tests (STM01-US-038)
    // =========================================================================

    @Test
    public void testMultiInstanceSharedRateLimitingPreventsQuotaMultiplication() {
        // STM01-US-038: In-process shared rate limiter coordination across controllers within the same JVM instance.
        // Verifies that multiple controller instances sharing the in-process SharedRateLimitStore do not multiply quotas.
        // Note: This tests intra-JVM coordination. Fleet-wide replica synchronization across separate JVM processes
        // is process-local without an external distributed store like Redis or an API Gateway rate limiter.
        RateLimiter.RateLimitStore sharedStore = new RateLimiter.SharedRateLimitStore();
        sharedStore.reset();

        // Instantiate two completely independent domain services & controllers sharing the in-process store
        StudentDomainService serviceInstance1 = new StudentDomainService(StudentDomainService.createInMemoryBundle());
        StudentDomainService serviceInstance2 = new StudentDomainService(StudentDomainService.createInMemoryBundle());

        RateLimiter limiter1 = new RateLimiter(sharedStore, 3, 60000); // 3 requests per minute quota
        RateLimiter limiter2 = new RateLimiter(sharedStore, 3, 60000); // Same shared store

        StudentController controllerInstance1 = new StudentController(serviceInstance1, limiter1);
        StudentController controllerInstance2 = new StudentController(serviceInstance2, limiter2);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-Id", tenantId.toString());
        headers.put("X-User-Id", staffId.toString());
        headers.put("X-User-Role", "college_admin");

        Map<String, String> params = new HashMap<>();
        params.put("limit", "10");

        // Request 1 via Instance 1: Allowed (remaining = 2)
        assertEquals(200, controllerInstance1.getRoster(headers, params).getStatusCode());

        // Request 2 via Instance 2: Allowed (remaining = 1)
        assertEquals(200, controllerInstance2.getRoster(headers, params).getStatusCode());

        // Request 3 via Instance 1: Allowed (remaining = 0)
        assertEquals(200, controllerInstance1.getRoster(headers, params).getStatusCode());

        // Request 4 via Instance 2: MUST BE RATE LIMITED (429) across instances sharing this store!
        // Sharing the store prevents quota multiplication across controllers in the same JVM.
        StudentController.HttpResponse resp4 = controllerInstance2.getRoster(headers, params);
        assertEquals("In-process shared limit must reject request 4 with 429", 429, resp4.getStatusCode());
        assertTrue(resp4.getBody().contains("RATE_LIMIT_EXCEEDED"));
        assertNotNull("Retry-After header must be present", resp4.getHeaders().get("Retry-After"));

        // Request 5 via Instance 1: Also rate limited
        StudentController.HttpResponse resp5 = controllerInstance1.getRoster(headers, params);
        assertEquals("Instance 1 must also be exhausted", 429, resp5.getStatusCode());
    }

    @Test
    public void testProcessLocalStoresAreIndependentPerJvmInstance() {
        // STM01-US-038: Documents and verifies process-local reality.
        // Independent JVM instances (simulated here by independent ProcessLocalRateLimitStore instances)
        // enforce process-local quotas without cross-JVM coordination.
        RateLimiter.RateLimitStore process1Store = new RateLimiter.ProcessLocalRateLimitStore();
        RateLimiter.RateLimitStore process2Store = new RateLimiter.ProcessLocalRateLimitStore();

        RateLimiter limiterProcess1 = new RateLimiter(process1Store, 2, 60000);
        RateLimiter limiterProcess2 = new RateLimiter(process2Store, 2, 60000);

        String clientKey = "ROSTER_SEARCH:" + tenantId + ":" + staffId;

        // Process 1 consumes both tokens
        assertTrue(limiterProcess1.tryAcquire(clientKey).isAllowed());
        assertTrue(limiterProcess1.tryAcquire(clientKey).isAllowed());
        assertFalse("Process 1 is exhausted", limiterProcess1.tryAcquire(clientKey).isAllowed());

        // Process 2 has its own process-local tokens (confirming process-local isolation, not distributed coordination)
        assertTrue("Process 2 operates independently with its own local token bucket", limiterProcess2.tryAcquire(clientKey).isAllowed());
        assertTrue(limiterProcess2.tryAcquire(clientKey).isAllowed());
        assertFalse("Process 2 is now exhausted locally", limiterProcess2.tryAcquire(clientKey).isAllowed());
    }

    @Test
    public void testConcurrentRequestsCannotBypassLimit() throws InterruptedException {
        // STM01-US-038: Atomic counter updates prevent concurrency race conditions
        final int capacity = 5;
        final RateLimiter concurrentLimiter = new RateLimiter(capacity, 60000);
        final String clientKey = "ROSTER_SEARCH:" + tenantId + ":" + staffId;

        final int totalThreads = 20;
        final java.util.concurrent.atomic.AtomicInteger allowedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger blockedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(totalThreads);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(totalThreads);
        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimiter.RateLimitResult res = concurrentLimiter.tryAcquire(clientKey);
                    if (res.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        blockedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("Exactly capacity requests must be allowed under concurrent bursts", capacity, allowedCount.get());
        assertEquals("Remaining concurrent requests must be rejected", totalThreads - capacity, blockedCount.get());
    }

    @Test
    public void testDifferentTenantsDoNotShareRateLimitQuotas() {
        // STM01-US-038: Quotas are strictly scoped per-tenant
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        Map<String, String> headersTenantA = new HashMap<>();
        headersTenantA.put("X-Tenant-Id", tenantA.toString());
        headersTenantA.put("X-User-Id", callerId.toString());
        headersTenantA.put("X-User-Role", "college_admin");

        Map<String, String> headersTenantB = new HashMap<>();
        headersTenantB.put("X-Tenant-Id", tenantB.toString());
        headersTenantB.put("X-User-Id", callerId.toString());
        headersTenantB.put("X-User-Role", "college_admin");

        Map<String, String> params = new HashMap<>();

        // Tenant A exhausts its quota (capacity = 3)
        assertEquals(200, controller.getRoster(headersTenantA, params).getStatusCode());
        assertEquals(200, controller.getRoster(headersTenantA, params).getStatusCode());
        assertEquals(200, controller.getRoster(headersTenantA, params).getStatusCode());
        assertEquals(429, controller.getRoster(headersTenantA, params).getStatusCode());

        // Tenant B with the same user ID has its own independent quota!
        assertEquals("Tenant B must succeed despite Tenant A exhaustion", 200, controller.getRoster(headersTenantB, params).getStatusCode());
    }

    @Test
    public void testDistinctOperationsHaveIndependentQuotas() {
        // STM01-US-038: Search and Export have independent operation-scoped quotas
        StudentProfileResponse student = service.enrollStudent(staffContext, buildEnrollRequest("OP001"));

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-Id", tenantId.toString());
        headers.put("X-User-Id", staffId.toString());
        headers.put("X-User-Role", "college_admin");

        Map<String, String> params = new HashMap<>();

        // Exhaust ROSTER_SEARCH quota (capacity = 3)
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());
        assertEquals(200, controller.getRoster(headers, params).getStatusCode());
        assertEquals(429, controller.getRoster(headers, params).getStatusCode());

        // STUDENT_EXPORT operation for same caller has its own independent quota
        StudentController.HttpResponse exportResp = controller.getDownstreamProjection(headers, student.getId().toString());
        assertEquals("Export operation must not be blocked by search quota exhaustion", 200, exportResp.getStatusCode());
    }
}
