package com.campx.student.repository;

import com.campx.platform.audit.AuditChangeLog;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxRepository;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import com.campx.student.config.DatabaseConfig;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
import com.campx.student.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Focused repository test suite for STU-01 Student PostgreSQL Persistence.
 * <p>
 * Tier 1: Mock JDBC PreparedStatement & Proxy Connection Verification:
 * <ul>
 *   <li>SQL parameter binding and schema contract compliance (stu.students)</li>
 *   <li>Atomic transactional outbox insertion (sys.outbox_events) on autoCommit == false</li>
 *   <li>Transaction rollback semantics on database failure</li>
 *   <li>Strict tenant boundary isolation (WHERE tenant_id = ?)</li>
 *   <li>Missing security context rejection fail-closed</li>
 *   <li>Context hygiene and connection pool leak prevention (RESET ROLE + claims clearing)</li>
 * </ul>
 * </p>
 */
public class PostgresStudentRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COLLEGE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PROGRAM_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private List<String> executedSqls;
    private Map<Integer, Object> lastParameters;
    private boolean commitCalled;
    private boolean rollbackCalled;
    private boolean roleResetExecuted;
    private boolean claimsResetExecuted;

    private TestAuditLogRepository testAuditRepository;
    private TestOutboxRepository testOutboxRepository;
    private ControlledIntelligenceAdapter testIntelligenceAdapter;

    private PostgresStudentRepository studentRepository;
    private UserSecurityContext callerContext;

    @Before
    public void setUp() {
        executedSqls = new ArrayList<>();
        lastParameters = new HashMap<>();
        commitCalled = false;
        rollbackCalled = false;
        roleResetExecuted = false;
        claimsResetExecuted = false;

        testAuditRepository = new TestAuditLogRepository();
        testOutboxRepository = new TestOutboxRepository();
        testIntelligenceAdapter = new ControlledIntelligenceAdapter();

        DatabaseConnectionManager mockConnManager = createMockConnectionManager();

        studentRepository = new PostgresStudentRepository(
                mockConnManager,
                testAuditRepository,
                new TransactionalOutboxWriter(testOutboxRepository),
                testIntelligenceAdapter
        );

        callerContext = new UserSecurityContext(USER_ID, TENANT_ID, COLLEGE_ID, null, "college_admin", false);
    }

    @Test
    public void testSaveStudentInsertsCorrectSqlAndBindsParameters() {
        StudentEntity student = new StudentEntity();
        student.setPersonId(UUID.randomUUID());
        student.setCollegeId(COLLEGE_ID);
        student.setProgramId(PROGRAM_ID);
        student.setEnrollmentNo("STU2026001");
        student.setAdmissionNo("ADM2026001");
        student.setAdmissionDate(LocalDate.of(2026, 8, 1));
        student.setStatus(StudentStatus.ACTIVE);

        StudentEntity saved = studentRepository.save(callerContext, student);
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals(TENANT_ID, saved.getTenantId());
        assertTrue("Transaction should be committed", commitCalled);
        assertFalse("Transaction should not be rolled back", rollbackCalled);

        // Verify SQL sequence: SET LOCAL ROLE, set_config claims, INSERT stu.students, RESET ROLE, claims cleanup
        boolean foundInsert = false;
        for (String sql : executedSqls) {
            if (sql.contains("INSERT INTO stu.students")) {
                foundInsert = true;
                assertTrue(sql.contains("tenant_id"));
                assertTrue(sql.contains("person_id"));
                assertTrue(sql.contains("enrollment_no"));
            }
        }
        assertTrue("INSERT INTO stu.students statement must be executed", foundInsert);

        // Verify least-privilege role switch was executed
        assertTrue(executedSqls.stream().anyMatch(s -> s.contains("SET LOCAL ROLE authenticated")));
        // Verify context cleanup was executed in finally block to prevent connection pool leakage
        assertTrue("Role reset must be executed before returning connection", roleResetExecuted);
        assertTrue("Claims reset must be executed before returning connection", claimsResetExecuted);
    }

    @Test
    public void testSaveStudentEnqueuesTransactionalOutboxEventAtomically() {
        StudentEntity student = new StudentEntity();
        student.setPersonId(UUID.randomUUID());
        student.setCollegeId(COLLEGE_ID);
        student.setProgramId(PROGRAM_ID);
        student.setEnrollmentNo("STU_OUTBOX_01");
        student.setStatus(StudentStatus.ACTIVE);

        studentRepository.save(callerContext, student);

        // Verify transactional outbox event
        List<OutboxEvent> outboxEvents = testOutboxRepository.getSavedEvents();
        assertEquals(1, outboxEvents.size());

        OutboxEvent event = outboxEvents.get(0);
        assertEquals(TENANT_ID, event.getTenantId());
        assertEquals("student-service", event.getService());
        assertEquals("STUDENT", event.getAggregateType());
        assertEquals("student.enrolled.v1", event.getEventType());
        assertTrue(event.getPayload().contains("STU_OUTBOX_01"));

        // Verify audit event was also appended
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        assertEquals("STUDENT_ENROLLED", testAuditRepository.getAppendedEvents().get(0).getAction());
    }

    @Test
    public void testFindRosterGeneratesTenantIsolatedQueryWithJoins() {
        StudentRosterQuery query = new StudentRosterQuery();
        query.setCollegeId(COLLEGE_ID);
        query.setProgramId(PROGRAM_ID);
        query.setSearch("kumar");
        query.setLimit(25);

        CursorPagedResult<StudentRosterItem> result = studentRepository.findRoster(callerContext, query);
        assertNotNull(result);

        // Verify SELECT SQL has mandatory tenant_id filter and schema joins
        boolean foundRosterQuery = false;
        for (String sql : executedSqls) {
            if (sql.contains("FROM stu.students s")) {
                foundRosterQuery = true;
                assertTrue(sql.contains("LEFT JOIN core.persons p"));
                assertTrue(sql.contains("LEFT JOIN acd.programs pr"));
                assertTrue(sql.contains("LEFT JOIN acd.batch_students bs"));
                assertTrue(sql.contains("LEFT JOIN acd.batches b"));
                assertTrue(sql.contains("WHERE s.tenant_id = ? AND s.deleted_at IS NULL"));
                assertTrue(sql.contains("ORDER BY s.enrollment_no ASC LIMIT ?"));
            }
        }
        assertTrue("findRoster query must be executed", foundRosterQuery);
        // Verify tenant isolation parameter binding (first param is tenant_id)
        assertEquals(TENANT_ID, lastParameters.get(1));
    }

    @Test
    public void testFindByIdEnforcesTenantIsolation() {
        UUID studentId = UUID.randomUUID();
        Optional<StudentEntity> opt = studentRepository.findById(callerContext, studentId);
        assertNotNull(opt);

        boolean foundSelect = false;
        for (String sql : executedSqls) {
            if (sql.contains("FROM stu.students WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                foundSelect = true;
            }
        }
        assertTrue("SELECT must include id and tenant_id filters", foundSelect);
        assertEquals(studentId, lastParameters.get(1));
        assertEquals(TENANT_ID, lastParameters.get(2));
    }

    @Test
    public void testDeletePerformsAuditedSoftDeleteAndEmitsOutbox() {
        UUID studentId = UUID.randomUUID();
        studentRepository.delete(callerContext, studentId);

        boolean foundDelete = false;
        for (String sql : executedSqls) {
            if (sql.contains("UPDATE stu.students SET deleted_at = now(), status = 'WITHDRAWN'")) {
                foundDelete = true;
                assertTrue(sql.contains("WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL"));
            }
        }
        assertTrue("Soft delete SQL must be executed", foundDelete);

        // Verify outbox event for deactivation
        List<OutboxEvent> outboxEvents = testOutboxRepository.getSavedEvents();
        assertEquals(1, outboxEvents.size());
        assertEquals("student.deactivated.v1", outboxEvents.get(0).getEventType());
    }

    @Test
    public void testMissingTenantContextThrowsSecurityViolation() {
        UserSecurityContext invalidContext = null;
        try {
            studentRepository.save(invalidContext, new StudentEntity());
            fail("Should throw StudentSecurityViolationException");
        } catch (StudentSecurityViolationException e) {
            assertTrue(e.getMessage().contains("tenant context is strictly required"));
        }
    }

    @Test
    public void testTransactionRollbackOnError() {
        // Create repository with mock manager that fails during query execution
        DatabaseConnectionManager failingManager = createFailingConnectionManager();
        PostgresStudentRepository failingRepo = new PostgresStudentRepository(
                failingManager,
                testAuditRepository,
                new TransactionalOutboxWriter(testOutboxRepository),
                testIntelligenceAdapter
        );

        StudentEntity student = new StudentEntity();
        student.setEnrollmentNo("FAIL_TEST");

        try {
            failingRepo.save(callerContext, student);
            fail("Should throw exception on failure");
        } catch (RuntimeException e) {
            // Expected
        }

        assertTrue("Rollback should be called on error", rollbackCalled);
        assertEquals("No outbox events should be saved if domain write fails", 0, testOutboxRepository.getSavedEvents().size());
    }

    // =========================================================================
    // Dynamic Proxy Mock JDBC Plumbing
    // =========================================================================

    private DatabaseConnectionManager createMockConnectionManager() {
        Connection mockConn = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setAutoCommit".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return false;
                    if ("commit".equals(name)) { commitCalled = true; return null; }
                    if ("rollback".equals(name)) { rollbackCalled = true; return null; }
                    if ("isClosed".equals(name)) return false;
                    if ("close".equals(name)) return null;
                    if ("createStatement".equals(name)) return createMockStatement();
                    if ("prepareStatement".equals(name)) {
                        String sql = (String) args[0];
                        executedSqls.add(sql);
                        return createMockPreparedStatement(sql);
                    }
                    return null;
                }
        );

        return new DatabaseConnectionManager() {
            @Override
            public Connection getConnection() {
                return mockConn;
            }
        };
    }

    private DatabaseConnectionManager createFailingConnectionManager() {
        Connection mockConn = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setAutoCommit".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return false;
                    if ("rollback".equals(name)) { rollbackCalled = true; return null; }
                    if ("isClosed".equals(name)) return false;
                    if ("close".equals(name)) return null;
                    if ("createStatement".equals(name)) return createMockStatement();
                    if ("prepareStatement".equals(name)) {
                        String sql = (String) args[0];
                        if (sql.contains("INSERT INTO stu.students")) {
                            throw new SQLException("Simulated database disk failure");
                        }
                        return createMockPreparedStatement(sql);
                    }
                    return null;
                }
        );

        return new DatabaseConnectionManager() {
            @Override
            public Connection getConnection() {
                return mockConn;
            }
        };
    }

    private Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeUpdate".equals(name)) {
                        String sql = (String) args[0];
                        executedSqls.add(sql);
                        return 1;
                    }
                    if ("execute".equals(name)) {
                        String sql = (String) args[0];
                        executedSqls.add(sql);
                        if (sql.contains("RESET ROLE")) {
                            roleResetExecuted = true;
                        }
                        return true;
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );
    }

    private PreparedStatement createMockPreparedStatement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                        lastParameters.put((Integer) args[0], args[1]);
                        return null;
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("execute".equals(name)) {
                        if (sql.contains("set_config('request.jwt.claims', '', false)")) {
                            claimsResetExecuted = true;
                        }
                        return true;
                    }
                    if ("executeQuery".equals(name)) {
                        return createMockResultSet(sql);
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );
    }

    private ResultSet createMockResultSet(String sql) {
        return (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    private int cursor = 0;
                    private final int maxRows = sql.contains("LIMIT") ? 2 : 1;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            cursor++;
                            return cursor <= maxRows;
                        }
                        if ("close".equals(name)) return null;
                        if ("getString".equals(name)) {
                            String col = (String) args[0];
                            if ("enrollment_no".equals(col)) return "STU2026001";
                            if ("admission_no".equals(col)) return "ADM2026001";
                            if ("status".equals(col)) return "ACTIVE";
                            if ("display_name".equals(col)) return "Test Student";
                            if ("first_name".equals(col)) return "Test";
                            if ("last_name".equals(col)) return "Student";
                            if ("primary_email".equals(col)) return "test.student@campx.edu";
                            if ("program_name".equals(col)) return "Computer Science";
                            if ("program_code".equals(col)) return "CS101";
                            if ("batch_name".equals(col)) return "2024-2028";
                            if ("batch_code".equals(col)) return "B24";
                            if ("roll_no".equals(col)) return "01";
                            return "VAL_" + col;
                        }
                        if ("getInt".equals(name)) {
                            return 1; // row_version
                        }
                        if ("getDate".equals(name)) {
                            return java.sql.Date.valueOf("2026-08-01");
                        }
                        if ("getTimestamp".equals(name)) {
                            return new Timestamp(System.currentTimeMillis());
                        }
                        if ("getObject".equals(name)) {
                            String col = (String) args[0];
                            if ("id".equals(col)) return UUID.randomUUID();
                            if ("tenant_id".equals(col)) return TENANT_ID;
                            if ("person_id".equals(col)) return UUID.randomUUID();
                            if ("college_id".equals(col)) return COLLEGE_ID;
                            if ("program_id".equals(col)) return PROGRAM_ID;
                            if ("batch_id".equals(col)) return UUID.randomUUID();
                            return UUID.randomUUID();
                        }
                        return null;
                    }
                }
        );
    }

    // =========================================================================
    // Test Doubles for Shared Services
    // =========================================================================

    private static class TestAuditLogRepository implements AuditLogRepository {
        final List<AuditRecord> savedRecords = new ArrayList<>();

        public List<AuditRecord> getAppendedEvents() {
            return Collections.unmodifiableList(savedRecords);
        }

        @Override
        public void appendAuditEvent(Connection conn, AuditRecord record) {
            savedRecords.add(record);
        }

        @Override
        public void appendChangeLog(Connection conn, AuditChangeLog changeLog) {}

        @Override
        public List<AuditRecord> findAuditEventsByTenant(Connection conn, UUID tenantId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<AuditChangeLog> findChangeLogsByRecord(Connection conn, UUID tenantId, String tableSchema, String tableName, UUID recordId) {
            return Collections.emptyList();
        }
    }

    private static class TestOutboxRepository implements OutboxRepository {
        final List<OutboxEvent> savedEvents = new ArrayList<>();

        public List<OutboxEvent> getSavedEvents() {
            return Collections.unmodifiableList(savedEvents);
        }

        @Override
        public void save(Connection conn, OutboxEvent event) {
            savedEvents.add(event);
        }

        @Override
        public void saveAll(Connection conn, List<OutboxEvent> events) {
            savedEvents.addAll(events);
        }

        @Override
        public Optional<OutboxEvent> findById(Connection conn, UUID tenantId, UUID eventId) {
            return Optional.empty();
        }

        @Override
        public List<OutboxEvent> findPendingEvents(Connection conn, UUID tenantId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markDispatched(Connection conn, UUID tenantId, UUID eventId, Instant dispatchedAt) {}

        @Override
        public void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, Instant nextAttemptAt, String errorDetails) {}

        @Override
        public void moveToDeadLetter(Connection conn, OutboxEvent event, String failureReason) {}

        @Override
        public int deleteDispatchedEvents(Connection conn, UUID tenantId, Instant olderThan) {
            return 0;
        }
    }
}
