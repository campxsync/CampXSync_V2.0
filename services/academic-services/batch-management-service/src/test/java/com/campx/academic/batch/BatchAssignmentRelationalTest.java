package com.campx.academic.batch;

import com.campx.academic.batch.exception.BatchForbiddenException;
import com.campx.academic.batch.exception.BatchNotFoundException;
import com.campx.academic.batch.exception.BatchValidationException;
import com.campx.academic.batch.repository.BatchAssignmentEntity;
import com.campx.academic.batch.repository.InMemoryBatchAssignmentRepository;
import com.campx.academic.batch.security.UserSecurityContext;
import com.campx.academic.batch.server.BatchServer;
import com.campx.academic.batch.service.BatchDomainService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Automated test suite for ACD-04 Relational Batch Assignment Persistence.
 * Validates:
 * 1. Valid assignment persists through repository contract.
 * 2. Duplicate assignment follows conflict/idempotency behavior.
 * 3. Student from another tenant cannot be assigned.
 * 4. Batch from another tenant cannot be modified.
 * 5. Unauthorized callers are rejected.
 * 6. Invalid / nonexistent identifiers produce correct response.
 * 7. Database failures are surfaced safely and never reported as success.
 * 8. Audit behavior on successful and removed assignments.
 * 9. HTTP REST endpoint cutover compatibility with UUID payloads.
 */
public class BatchAssignmentRelationalTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COLLEGE_A = UUID.fromString("11111111-c011-0000-0000-000000000001");
    private static final UUID BATCH_A = UUID.fromString("11111111-ba5c-0000-0000-000000000001");
    private static final UUID STUDENT_A1 = UUID.fromString("11111111-5700-0000-0000-000000000001");
    private static final UUID STUDENT_A2 = UUID.fromString("11111111-5700-0000-0000-000000000002");
    private static final UUID STUDENT_B1 = UUID.fromString("22222222-5700-0000-0000-000000000001");
    private static final UUID BATCH_B = UUID.fromString("22222222-ba5c-0000-0000-000000000001");
    private static final UUID ADMIN_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private InMemoryBatchAssignmentRepository repository;
    private BatchDomainService domainService;
    private UserSecurityContext collegeAdminContext;

    private static BatchServer httpTestServer;
    private static final int HTTP_PORT = 8097;

    @BeforeClass
    public static void startHttpServer() throws Exception {
        System.setProperty("campx.internal.auth.enabled", "false");
        InMemoryBatchAssignmentRepository httpRepo = new InMemoryBatchAssignmentRepository();
        BatchDomainService httpService = new BatchDomainService(httpRepo);
        httpTestServer = new BatchServer(HTTP_PORT, httpService);
        httpTestServer.start();
    }

    @AfterClass
    public static void stopHttpServer() {
        if (httpTestServer != null) {
            httpTestServer.stop();
        }
        System.clearProperty("campx.internal.auth.enabled");
    }

    @Before
    public void setUp() {
        repository = new InMemoryBatchAssignmentRepository();
        domainService = new BatchDomainService(repository);
        collegeAdminContext = new UserSecurityContext(ADMIN_USER, TENANT_A, COLLEGE_A, null, "COLLEGE_ADMIN", false);
    }

    @Test
    public void testValidAssignmentPersistsThroughRepositoryContract() {
        BatchAssignmentEntity entity = new BatchAssignmentEntity();
        entity.setTenantId(TENANT_A);
        entity.setBatchId(BATCH_A);
        entity.setStudentId(STUDENT_A1);
        entity.setRollNo("2024-CSE-010");

        BatchAssignmentEntity saved = repository.assignStudent(collegeAdminContext, entity);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("2024-CSE-010", saved.getRollNo());
        assertEquals(TENANT_A, saved.getTenantId());
        assertEquals(BATCH_A, saved.getBatchId());
        assertEquals(STUDENT_A1, saved.getStudentId());
        assertEquals("Computer Science Batch 2024-A", saved.getBatchName());
        assertEquals("Student One", saved.getStudentName());
        assertEquals(1, saved.getRowVersion());

        // Verify retrieval
        Optional<BatchAssignmentEntity> found = repository.findAssignment(collegeAdminContext, BATCH_A, STUDENT_A1);
        assertTrue(found.isPresent());
        assertEquals("ACTIVE", found.get().getStatus());
        assertEquals("2024-CSE-010", found.get().getRollNo());
    }

    @Test
    public void testDuplicateAssignmentFollowsConflictIdempotencyBehavior() {
        BatchAssignmentEntity entity1 = new BatchAssignmentEntity();
        entity1.setTenantId(TENANT_A);
        entity1.setBatchId(BATCH_A);
        entity1.setStudentId(STUDENT_A1);
        entity1.setRollNo("ROLL-ORIGINAL");

        BatchAssignmentEntity saved1 = repository.assignStudent(collegeAdminContext, entity1);
        assertEquals(1, saved1.getRowVersion());
        assertEquals("ROLL-ORIGINAL", saved1.getRollNo());

        // Re-assign same student to same batch with updated roll number
        BatchAssignmentEntity entity2 = new BatchAssignmentEntity();
        entity2.setTenantId(TENANT_A);
        entity2.setBatchId(BATCH_A);
        entity2.setStudentId(STUDENT_A1);
        entity2.setRollNo("ROLL-UPDATED");

        BatchAssignmentEntity saved2 = repository.assignStudent(collegeAdminContext, entity2);

        // Conflict semantics: status stays ACTIVE, row_version increments, roll_no updates
        assertEquals("ACTIVE", saved2.getStatus());
        assertEquals(2, saved2.getRowVersion());
        assertEquals("ROLL-UPDATED", saved2.getRollNo());

        // Confirm only 1 active assignment exists for this batch
        List<BatchAssignmentEntity> list = repository.listAssignmentsByBatch(collegeAdminContext, BATCH_A);
        assertEquals(1, list.size());
        assertEquals(saved1.getId(), list.get(0).getId());
    }

    @Test
    public void testStudentFromAnotherTenantCannotBeAssigned() {
        // Attempt to assign Student B1 (tenant B) using tenant A's context
        BatchAssignmentEntity crossTenantAssignment = new BatchAssignmentEntity();
        crossTenantAssignment.setTenantId(TENANT_A);
        crossTenantAssignment.setBatchId(BATCH_A);
        crossTenantAssignment.setStudentId(STUDENT_B1);

        try {
            repository.assignStudent(collegeAdminContext, crossTenantAssignment);
            fail("Expected BatchNotFoundException for cross-tenant student assignment");
        } catch (BatchNotFoundException e) {
            assertTrue(e.getMessage().contains("Student not found in tenant workspace"));
        }
    }

    @Test
    public void testBatchFromAnotherTenantCannotBeModified() {
        // Attempt to assign Student A1 into Batch B (tenant B) using tenant A's context
        BatchAssignmentEntity crossTenantBatch = new BatchAssignmentEntity();
        crossTenantBatch.setTenantId(TENANT_A);
        crossTenantBatch.setBatchId(BATCH_B);
        crossTenantBatch.setStudentId(STUDENT_A1);

        try {
            repository.assignStudent(collegeAdminContext, crossTenantBatch);
            fail("Expected BatchNotFoundException for cross-tenant batch modification");
        } catch (BatchNotFoundException e) {
            assertTrue(e.getMessage().contains("Batch not found in tenant workspace"));
        }
    }

    @Test
    public void testUnauthorizedCallersAreRejected() {
        UserSecurityContext studentContext = new UserSecurityContext(ADMIN_USER, TENANT_A, COLLEGE_A, null, "STUDENT", false);

        try {
            domainService.assignStudentRelational(studentContext, BATCH_A, STUDENT_A1, "ROLL-1");
            fail("Expected BatchForbiddenException for STUDENT role");
        } catch (BatchForbiddenException e) {
            assertTrue(e.getMessage().contains("is not authorized"));
        }

        UserSecurityContext facultyContext = new UserSecurityContext(ADMIN_USER, TENANT_A, COLLEGE_A, null, "FACULTY", false);
        try {
            domainService.assignStudentRelational(facultyContext, BATCH_A, STUDENT_A1, "ROLL-1");
            fail("Expected BatchForbiddenException for FACULTY role");
        } catch (BatchForbiddenException e) {
            assertTrue(e.getMessage().contains("is not authorized"));
        }
    }

    @Test
    public void testInvalidOrNonexistentIdentifiersProduceCorrectResponse() {
        UUID nonexistentBatch = UUID.randomUUID();
        UUID nonexistentStudent = UUID.randomUUID();

        // Nonexistent batch
        try {
            domainService.assignStudentRelational(collegeAdminContext, nonexistentBatch, STUDENT_A1, "ROLL-1");
            fail("Expected BatchNotFoundException for nonexistent batch");
        } catch (BatchNotFoundException e) {
            assertTrue(e.getMessage().contains("Batch not found"));
        }

        // Nonexistent student
        try {
            domainService.assignStudentRelational(collegeAdminContext, BATCH_A, nonexistentStudent, "ROLL-1");
            fail("Expected BatchNotFoundException for nonexistent student");
        } catch (BatchNotFoundException e) {
            assertTrue(e.getMessage().contains("Student not found"));
        }
    }

    @Test
    public void testDatabaseFailureSurfacedSafelyNeverReportedAsSuccess() {
        repository.setSimulateFailure(true);

        BatchAssignmentEntity entity = new BatchAssignmentEntity();
        entity.setTenantId(TENANT_A);
        entity.setBatchId(BATCH_A);
        entity.setStudentId(STUDENT_A2);
        entity.setRollNo("2024-CSE-002");

        try {
            repository.assignStudent(collegeAdminContext, entity);
            fail("Expected database failure to be thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Simulated database failure"));
        }

        // Verify that NO assignment was saved
        Optional<BatchAssignmentEntity> notFound = repository.findAssignment(collegeAdminContext, BATCH_A, STUDENT_A2);
        assertFalse(notFound.isPresent());

        // Verify that NO audit event was logged for the failed assignment
        assertEquals(0, repository.getAuditLogs().size());
    }

    @Test
    public void testAuditBehaviorOnSuccessfulAssignmentAndRemoval() {
        BatchAssignmentEntity entity = new BatchAssignmentEntity();
        entity.setTenantId(TENANT_A);
        entity.setBatchId(BATCH_A);
        entity.setStudentId(STUDENT_A1);
        entity.setRollNo("2024-CSE-001");

        repository.assignStudent(collegeAdminContext, entity);

        assertEquals(1, repository.getAuditLogs().size());
        InMemoryBatchAssignmentRepository.InMemoryAuditEntry log1 = repository.getAuditLogs().get(0);
        assertEquals("STUDENT_BATCH_ASSIGNED", log1.getAction());
        assertEquals("SUCCESS", log1.getStatus());
        assertEquals(ADMIN_USER, log1.getPrincipalId());
        assertEquals(STUDENT_A1, log1.getResourceId());
        assertTrue(log1.getDetail().contains("2024-CSE-001"));

        // Remove assignment
        repository.removeAssignment(collegeAdminContext, BATCH_A, STUDENT_A1, "Transfer to other college");

        assertEquals(2, repository.getAuditLogs().size());
        InMemoryBatchAssignmentRepository.InMemoryAuditEntry log2 = repository.getAuditLogs().get(1);
        assertEquals("STUDENT_BATCH_REMOVED", log2.getAction());
        assertEquals("SUCCESS", log2.getStatus());
        assertTrue(log2.getDetail().contains("Transfer to other college"));
    }

    @Test
    public void testHttpEndpointWithUuidBatchAndStudent() throws Exception {
        String urlString = "http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-User-Id", ADMIN_USER.toString());
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
        conn.setRequestProperty("X-College-Id", COLLEGE_A.toString());

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        String resp = sb.toString();
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
        assertTrue(resp.contains("\"rollNo\":\"2024-CSE-001\""));
        assertTrue(resp.contains(STUDENT_A1.toString()));
        assertTrue(resp.contains(BATCH_A.toString()));
    }

    @Test
    public void testHttpMissingUserIdReturns401() throws Exception {
        URL url = new URL("http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
        // Omit X-User-Id

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
    }

    @Test
    public void testHttpMalformedUserIdReturns401() throws Exception {
        URL url = new URL("http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", "not-a-valid-uuid");
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
    }

    @Test
    public void testHttpMissingTenantIdReturns403() throws Exception {
        URL url = new URL("http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", ADMIN_USER.toString());
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
        // Omit X-Tenant-Id

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
    }

    @Test
    public void testHttpMalformedTenantIdReturns403() throws Exception {
        URL url = new URL("http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", ADMIN_USER.toString());
        conn.setRequestProperty("X-Tenant-Id", "malformed-tenant-id");
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
    }

    @Test
    public void testHttpUnauthorizedRoleReturns403() throws Exception {
        URL url = new URL("http://localhost:" + HTTP_PORT + "/api/v1/academics/batches/" + BATCH_A + "/students");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", ADMIN_USER.toString());
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Role", "STUDENT");

        String payload = "{\"studentId\":\"" + STUDENT_A1 + "\",\"rollNo\":\"2024-CSE-001\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
    }

    @Test
    public void testExplicitPersistenceModesAndSafeStartup() {
        // 1. Explicit in-memory mode
        System.setProperty("campx.persistence.mode", "in_memory");
        BatchDomainService inMemService = new BatchDomainService();
        assertTrue(inMemService.getAssignmentRepository() instanceof InMemoryBatchAssignmentRepository);
        assertFalse(inMemService.isRelationalMode());

        // 2. Explicit postgres mode
        System.setProperty("campx.persistence.mode", "postgres");
        BatchDomainService pgService = new BatchDomainService();
        assertTrue(pgService.getAssignmentRepository() instanceof com.campx.academic.batch.repository.PostgresBatchAssignmentRepository);
        assertTrue(pgService.isRelationalMode());

        // 3. Explicit relational alias mode
        System.setProperty("campx.persistence.mode", "relational");
        BatchDomainService relService = new BatchDomainService();
        assertTrue(relService.getAssignmentRepository() instanceof com.campx.academic.batch.repository.PostgresBatchAssignmentRepository);
        assertTrue(relService.isRelationalMode());

        // 4. Invalid mode must fail safely with IllegalStateException
        System.setProperty("campx.persistence.mode", "invalid_unknown_mode");
        try {
            new BatchDomainService();
            fail("Expected IllegalStateException for invalid persistence mode");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Unsupported persistence mode"));
        } finally {
            System.clearProperty("campx.persistence.mode");
        }
    }

    @Test
    public void testLegacyIdentifierCannotBypassRelationalPersistenceWhenRequired() throws Exception {
        // Spin up a service explicitly configured with Postgres repository
        com.campx.academic.batch.repository.PostgresBatchAssignmentRepository pgRepo =
                new com.campx.academic.batch.repository.PostgresBatchAssignmentRepository();
        BatchDomainService relDomainService = new BatchDomainService(pgRepo);
        assertTrue(relDomainService.isRelationalMode());

        int port = 8098;
        BatchServer relServer = new BatchServer(port, relDomainService);
        relServer.start();
        try {
            // Attempt to assign using legacy identifier "BATCH-101" while in relational mode
            URL url = new URL("http://localhost:" + port + "/api/v1/academics/batches/BATCH-101/students");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-User-Id", ADMIN_USER.toString());
            conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
            conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");

            String payload = "{\"studentId\":\"STU-1001\",\"rollNo\":\"2024-CSE-001\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            // Must reject with HTTP 422 Unprocessable Entity (BatchValidationException), NOT silently update in-memory
            assertEquals(422, conn.getResponseCode());
        } finally {
            relServer.stop();
        }
    }

    @Test
    public void testSoftDeletedAssignmentReactivationBehavior() {
        // Assign student
        BatchAssignmentEntity entity = new BatchAssignmentEntity();
        entity.setTenantId(TENANT_A);
        entity.setBatchId(BATCH_A);
        entity.setStudentId(STUDENT_A1);
        entity.setRollNo("ROLL-V1");

        BatchAssignmentEntity created = repository.assignStudent(collegeAdminContext, entity);
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(1, created.getRowVersion());

        // Remove assignment (soft-delete)
        BatchAssignmentEntity removed = repository.removeAssignment(collegeAdminContext, BATCH_A, STUDENT_A1, "Graduated early");
        assertEquals("ENDED", removed.getStatus());

        // Verify findAssignment returns empty for active assignments
        Optional<BatchAssignmentEntity> activeSearch = repository.findAssignment(collegeAdminContext, BATCH_A, STUDENT_A1);
        assertFalse(activeSearch.isPresent());

        // Re-assign same student to same batch -> reactivates assignment
        BatchAssignmentEntity reactivatedEntity = new BatchAssignmentEntity();
        reactivatedEntity.setTenantId(TENANT_A);
        reactivatedEntity.setBatchId(BATCH_A);
        reactivatedEntity.setStudentId(STUDENT_A1);
        reactivatedEntity.setRollNo("ROLL-REACTIVATED");

        BatchAssignmentEntity reactivated = repository.assignStudent(collegeAdminContext, reactivatedEntity);
        assertEquals("ACTIVE", reactivated.getStatus());
        assertEquals("ROLL-REACTIVATED", reactivated.getRollNo());
        assertEquals(3, reactivated.getRowVersion());

        // Verify active assignment is found again
        Optional<BatchAssignmentEntity> reactivatedSearch = repository.findAssignment(collegeAdminContext, BATCH_A, STUDENT_A1);
        assertTrue(reactivatedSearch.isPresent());
        assertEquals("ACTIVE", reactivatedSearch.get().getStatus());
    }

    @Test
    public void testPostgresConnectionFailureFailsClosedWithoutInMemoryFallback() {
        // Configure Postgres repository with an unreachable database configuration
        com.campx.academic.batch.config.DatabaseConfig badConfig = new com.campx.academic.batch.config.DatabaseConfig();
        badConfig.setJdbcUrl("jdbc:postgresql://localhost:65432/nonexistent_unreachable_db");
        badConfig.setUsername("unprivileged_user");
        badConfig.setPassword("invalid_pass");
        badConfig.setConnectionTimeoutMs(1000L); // 1 second timeout

        com.campx.academic.batch.config.DatabaseConnectionManager badConnManager =
                new com.campx.academic.batch.config.DatabaseConnectionManager(badConfig);

        com.campx.academic.batch.repository.PostgresBatchAssignmentRepository failRepo =
                new com.campx.academic.batch.repository.PostgresBatchAssignmentRepository(badConnManager);

        BatchAssignmentEntity entity = new BatchAssignmentEntity();
        entity.setTenantId(TENANT_A);
        entity.setBatchId(BATCH_A);
        entity.setStudentId(STUDENT_A1);
        entity.setRollNo("ROLL-PG-FAIL");

        try {
            failRepo.assignStudent(collegeAdminContext, entity);
            fail("Expected database connection failure to throw RuntimeException / fail closed");
        } catch (RuntimeException e) {
            // Verifies connection failure occurred and was surfaced
            assertNotNull(e.getMessage());
        } finally {
            badConnManager.close();
        }
    }
}
