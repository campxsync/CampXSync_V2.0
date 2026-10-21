package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.platform.audit.AuditChangeLog;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxRepository;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests validating Department and Program repositories:
 * <ul>
 *   <li>SQL generation and parameter binding under PostgreSQL kernel constraints</li>
 *   <li>Tenant isolation enforcement on read, write, update, and lifecycle retirement</li>
 *   <li>Shared Services compliance: atomic writing to {@code audit.events}</li>
 *   <li>Shared Services reliability: atomic enqueueing to {@code sys.outbox_events}</li>
 *   <li>Governed Intelligence Layer contract emission: {@link IntelligenceContextEvent} v1.0.0</li>
 * </ul>
 */
public class DepartmentProgramSharedServicesTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COLLEGE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private List<String> executedSqls;
    private Map<Integer, Object> lastParameters;
    private boolean commitCalled;
    private boolean rollbackCalled;

    private TestAuditLogRepository testAuditRepository;
    private TestOutboxRepository testOutboxRepository;
    private ControlledIntelligenceAdapter testIntelligenceAdapter;

    private PostgresDepartmentRepository departmentRepository;
    private PostgresProgramRepository programRepository;
    private UserSecurityContext callerContext;

    @Before
    public void setUp() {
        executedSqls = new ArrayList<>();
        lastParameters = new HashMap<>();
        commitCalled = false;
        rollbackCalled = false;

        testAuditRepository = new TestAuditLogRepository();
        testOutboxRepository = new TestOutboxRepository();
        testIntelligenceAdapter = new ControlledIntelligenceAdapter();

        DatabaseConnectionManager mockConnManager = createMockConnectionManager();

        departmentRepository = new PostgresDepartmentRepository(
                mockConnManager,
                testAuditRepository,
                new TransactionalOutboxWriter(testOutboxRepository),
                testIntelligenceAdapter
        );

        programRepository = new PostgresProgramRepository(
                mockConnManager,
                testAuditRepository,
                new TransactionalOutboxWriter(testOutboxRepository),
                testIntelligenceAdapter
        );

        callerContext = new UserSecurityContext(USER_ID, TENANT_ID);
    }

    // =========================================================================
    // Department Repository Tests
    // =========================================================================

    @Test
    public void testCreateDepartmentSharedServicesIntegration() {
        Department dep = new Department();
        dep.setDepartmentCode("CSE");
        dep.setName("Computer Science and Engineering");
        dep.setCollegeId(COLLEGE_ID.toString());

        Department created = departmentRepository.createDepartment(callerContext, dep);

        assertNotNull(created);
        assertEquals("CSE", created.getDepartmentCode());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(TENANT_ID.toString(), created.getTenantId());
        assertEquals(COLLEGE_ID.toString(), created.getCollegeId());

        // Verify transaction committed
        assertTrue("Transaction must commit", commitCalled);
        assertFalse("Transaction must not rollback", rollbackCalled);

        // Verify SQL execution
        assertTrue("SQL must include core.departments INSERT",
                executedSqls.stream().anyMatch(sql -> sql.contains("INSERT INTO core.departments")));

        // Verify Shared Services Audit (audit.events)
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("DEPARTMENT_CREATED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());
        assertEquals(USER_ID, audit.getPrincipalId());
        assertEquals("DEPARTMENT", audit.getResourceType());
        assertEquals(created.getId(), audit.getResourceId().toString());
        assertEquals("SUCCESS", audit.getStatus());

        // Verify Shared Services Outbox (sys.outbox_events)
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("DepartmentCreated", outbox.getEventType());
        assertEquals("DEPARTMENT", outbox.getAggregateType());
        assertEquals("college-admin-service", outbox.getService());
        assertEquals(TENANT_ID, outbox.getTenantId());
        assertEquals(created.getId(), outbox.getAggregateId().toString());

        // Verify Intelligence Layer publication (v1.0.0)
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("DepartmentCreated", intel.getEventType());
        assertEquals("DEPARTMENT", intel.getAggregateType());
        assertEquals("1.0.0", intel.getSchemaVersion());
        assertEquals("college-admin-service", intel.getSourceService());
        assertEquals(TENANT_ID.toString(), intel.getTenantId());
        assertEquals(created.getId(), intel.getAggregateId());
    }

    @Test
    public void testUpdateDepartmentSharedServicesIntegration() {
        Department dep = new Department();
        UUID depId = UUID.randomUUID();
        dep.setId(depId.toString());
        dep.setName("CS & Engineering Updated");
        dep.setStatus("ACTIVE");

        Department updated = departmentRepository.updateDepartment(callerContext, dep);

        assertNotNull(updated);
        assertTrue(commitCalled);

        // Verify SQL execution
        assertTrue("SQL must include core.departments UPDATE",
                executedSqls.stream().anyMatch(sql -> sql.contains("UPDATE core.departments SET")));

        // Verify Audit Event
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("DEPARTMENT_UPDATED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());
        assertEquals(depId, audit.getResourceId());

        // Verify Outbox Event
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("DepartmentUpdated", outbox.getEventType());
        assertEquals(depId, outbox.getAggregateId());

        // Verify Intelligence Event
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("DepartmentUpdated", intel.getEventType());
        assertEquals(depId.toString(), intel.getAggregateId());
    }

    @Test
    public void testRetireDepartmentSharedServicesIntegration() {
        String depId = UUID.randomUUID().toString();

        departmentRepository.retireDepartment(callerContext, depId);

        assertTrue(commitCalled);

        // Verify SQL execution
        assertTrue("SQL must include status = 'RETIRED'",
                executedSqls.stream().anyMatch(sql -> sql.contains("status = 'RETIRED'")));

        // Verify Audit Event
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("DEPARTMENT_RETIRED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());

        // Verify Outbox Event
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("DepartmentRetired", outbox.getEventType());

        // Verify Intelligence Event
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("DepartmentRetired", intel.getEventType());
        assertEquals(depId, intel.getAggregateId());
    }

    @Test(expected = CollegeSecurityViolationException.class)
    public void testDepartmentSecurityViolationOnMissingContext() {
        Department dep = new Department();
        dep.setDepartmentCode("EE");
        dep.setName("Electrical Engineering");
        departmentRepository.createDepartment(null, dep);
    }

    @Test
    public void testDepartmentFindByIdTenantScoping() {
        String testId = UUID.randomUUID().toString();
        Optional<Department> result = departmentRepository.findById(callerContext, testId);

        assertTrue(result.isPresent());
        assertTrue(executedSqls.stream().anyMatch(sql ->
                sql.contains("WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")));
    }

    // =========================================================================
    // Program Repository Tests
    // =========================================================================

    @Test
    public void testCreateProgramSharedServicesIntegration() {
        Program prog = new Program();
        prog.setProgramCode("BTECH_CS");
        prog.setName("Bachelor of Technology in Computer Science");
        prog.setDepartmentId(UUID.randomUUID().toString());
        prog.setCollegeId(COLLEGE_ID.toString());
        prog.setDurationYears(4);
        prog.setLevel("UG");

        Program created = programRepository.createProgram(callerContext, prog);

        assertNotNull(created);
        assertEquals("BTECH_CS", created.getProgramCode());
        assertEquals(4, created.getDurationYears());
        assertEquals("ACTIVE", created.getStatus());

        // Verify transaction committed
        assertTrue("Transaction must commit", commitCalled);
        assertFalse("Transaction must not rollback", rollbackCalled);

        // Verify SQL execution
        assertTrue("SQL must include acd.programs INSERT",
                executedSqls.stream().anyMatch(sql -> sql.contains("INSERT INTO acd.programs")));

        // Verify Shared Services Audit (audit.events)
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("PROGRAM_CREATED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());
        assertEquals(USER_ID, audit.getPrincipalId());
        assertEquals("PROGRAM", audit.getResourceType());
        assertEquals(created.getId(), audit.getResourceId().toString());

        // Verify Shared Services Outbox (sys.outbox_events)
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("ProgramCreated", outbox.getEventType());
        assertEquals("PROGRAM", outbox.getAggregateType());
        assertEquals("college-admin-service", outbox.getService());
        assertEquals(TENANT_ID, outbox.getTenantId());
        assertEquals(created.getId(), outbox.getAggregateId().toString());

        // Verify Intelligence Layer publication (v1.0.0)
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("ProgramCreated", intel.getEventType());
        assertEquals("PROGRAM", intel.getAggregateType());
        assertEquals("1.0.0", intel.getSchemaVersion());
        assertEquals("college-admin-service", intel.getSourceService());
        assertEquals(TENANT_ID.toString(), intel.getTenantId());
        assertEquals(created.getId(), intel.getAggregateId());
    }

    @Test
    public void testUpdateProgramSharedServicesIntegration() {
        Program prog = new Program();
        UUID progId = UUID.randomUUID();
        prog.setId(progId.toString());
        prog.setName("B.Tech in CS and Engineering (Hons)");
        prog.setDurationYears(4);
        prog.setPublished(true);

        Program updated = programRepository.updateProgram(callerContext, prog);

        assertNotNull(updated);
        assertTrue(commitCalled);

        // Verify SQL execution
        assertTrue("SQL must include acd.programs UPDATE",
                executedSqls.stream().anyMatch(sql -> sql.contains("UPDATE acd.programs SET")));

        // Verify Audit Event
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("PROGRAM_UPDATED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());
        assertEquals(progId, audit.getResourceId());

        // Verify Outbox Event
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("ProgramUpdated", outbox.getEventType());
        assertEquals(progId, outbox.getAggregateId());

        // Verify Intelligence Event
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("ProgramUpdated", intel.getEventType());
        assertEquals(progId.toString(), intel.getAggregateId());
    }

    @Test
    public void testDeleteProgramSharedServicesIntegration() {
        String progId = UUID.randomUUID().toString();

        programRepository.deleteProgram(callerContext, progId);

        assertTrue(commitCalled);

        // Verify SQL execution
        assertTrue("SQL must include status = 'DISCONTINUED'",
                executedSqls.stream().anyMatch(sql -> sql.contains("status = 'DISCONTINUED'")));

        // Verify Audit Event
        assertEquals(1, testAuditRepository.getAppendedEvents().size());
        AuditRecord audit = testAuditRepository.getAppendedEvents().get(0);
        assertEquals("PROGRAM_DISCONTINUED", audit.getAction());
        assertEquals(TENANT_ID, audit.getTenantId());

        // Verify Outbox Event
        assertEquals(1, testOutboxRepository.getSavedEvents().size());
        OutboxEvent outbox = testOutboxRepository.getSavedEvents().get(0);
        assertEquals("ProgramDiscontinued", outbox.getEventType());

        // Verify Intelligence Event
        assertEquals(1, testIntelligenceAdapter.getPublishedEvents().size());
        IntelligenceContextEvent intel = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("ProgramDiscontinued", intel.getEventType());
        assertEquals(progId, intel.getAggregateId());
    }

    @Test(expected = CollegeSecurityViolationException.class)
    public void testProgramSecurityViolationOnMissingContext() {
        Program prog = new Program();
        prog.setProgramCode("BTECH_ME");
        prog.setName("Mechanical Engineering");
        programRepository.createProgram(null, prog);
    }

    @Test(expected = CollegeMalformedPayloadException.class)
    public void testCreateProgramRequiresPositiveDuration() {
        Program prog = new Program();
        prog.setProgramCode("BTECH_INVALID");
        prog.setName("Invalid Duration");
        prog.setDepartmentId(UUID.randomUUID().toString());
        prog.setDurationYears(0);
        programRepository.createProgram(callerContext, prog);
    }

    @Test
    public void testProgramGetByIdTenantScoping() {
        String testId = UUID.randomUUID().toString();
        Program result = programRepository.getProgramById(callerContext, testId);

        assertNotNull(result);
        assertTrue(executedSqls.stream().anyMatch(sql ->
                sql.contains("FROM acd.programs WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")));
    }

    // =========================================================================
    // Mock Helpers
    // =========================================================================

    private DatabaseConnectionManager createMockConnectionManager() {
        Connection proxyConn = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    private boolean autoCommit = true;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("setAutoCommit".equals(name)) {
                            autoCommit = (Boolean) args[0];
                            return null;
                        }
                        if ("getAutoCommit".equals(name)) {
                            return autoCommit;
                        }
                        if ("isClosed".equals(name)) {
                            return false;
                        }
                        if ("commit".equals(name)) {
                            commitCalled = true;
                            return null;
                        }
                        if ("rollback".equals(name)) {
                            rollbackCalled = true;
                            return null;
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        if ("createStatement".equals(name)) {
                            return createMockStatement();
                        }
                        if ("prepareStatement".equals(name)) {
                            String sql = (String) args[0];
                            executedSqls.add(sql);
                            return createMockPreparedStatement(sql);
                        }
                        return null;
                    }
                }
        );

        return new DatabaseConnectionManager() {
            @Override
            public Connection getConnection() {
                return proxyConn;
            }

            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public boolean isInitialized() {
                return true;
            }
        };
    }

    private Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName()) && args != null && args.length > 0) {
                        executedSqls.add((String) args[0]);
                        return true;
                    }
                    if ("close".equals(method.getName())) return null;
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
                    if (name.startsWith("set") && args != null && args.length >= 2) {
                        if (args[0] instanceof Integer) {
                            lastParameters.put((Integer) args[0], args[1]);
                        }
                        return null;
                    }
                    if ("executeQuery".equals(name)) {
                        return createMockResultSet(sql);
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("execute".equals(name)) {
                        return true;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
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

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            if (sql.contains("SELECT id FROM core.departments WHERE tenant_id = ? AND college_id = ? AND code = ?")
                                    || sql.contains("SELECT id FROM acd.programs WHERE tenant_id = ? AND code = ?")) {
                                return false; // Not a duplicate
                            }
                            cursor++;
                            return cursor == 1;
                        }
                        if ("wasNull".equals(name)) {
                            return false;
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        if ("getString".equals(name)) {
                            String col = (String) args[0];
                            if ("id".equals(col)) {
                                if (lastParameters.containsKey(1) && lastParameters.get(1) != null) {
                                    return lastParameters.get(1).toString();
                                }
                                return UUID.randomUUID().toString();
                            }
                            if ("tenant_id".equals(col)) return TENANT_ID.toString();
                            if ("college_id".equals(col)) return COLLEGE_ID.toString();
                            if ("department_id".equals(col)) return UUID.randomUUID().toString();
                            if ("code".equals(col)) {
                                if (sql.contains("acd.programs")) {
                                    if (lastParameters.containsKey(5) && lastParameters.get(5) != null) {
                                        return lastParameters.get(5).toString();
                                    }
                                    return "BTECH_CS";
                                }
                                if (lastParameters.containsKey(4) && lastParameters.get(4) != null) {
                                    return lastParameters.get(4).toString();
                                }
                                return "CSE";
                            }
                            if ("name".equals(col)) {
                                if (sql.contains("acd.programs")) {
                                    if (lastParameters.containsKey(6) && lastParameters.get(6) != null) {
                                        return lastParameters.get(6).toString();
                                    }
                                    return "Bachelor of Technology in Computer Science";
                                }
                                if (lastParameters.containsKey(5) && lastParameters.get(5) != null) {
                                    return lastParameters.get(5).toString();
                                }
                                return "Computer Science";
                            }
                            if ("status".equals(col)) return "ACTIVE";
                            if ("level".equals(col)) return "UG";
                            return "VAL_" + col;
                        }
                        if ("getInt".equals(name)) {
                            String col = (String) args[0];
                            if ("row_version".equals(col)) return 1;
                            if ("total_terms".equals(col)) return 8;
                            return 1;
                        }
                        if ("getBigDecimal".equals(name)) {
                            return BigDecimal.valueOf(4);
                        }
                        if ("getBoolean".equals(name)) {
                            return true;
                        }
                        if ("getTimestamp".equals(name)) {
                            return new Timestamp(System.currentTimeMillis());
                        }
                        if ("getObject".equals(name)) {
                            String col = (String) args[0];
                            if ("id".equals(col)) {
                                if (lastParameters.containsKey(1) && lastParameters.get(1) instanceof UUID) {
                                    return lastParameters.get(1);
                                } else if (lastParameters.containsKey(1) && lastParameters.get(1) != null) {
                                    return UUID.fromString(lastParameters.get(1).toString());
                                }
                                return UUID.randomUUID();
                            }
                            if ("tenant_id".equals(col)) return TENANT_ID;
                            if ("college_id".equals(col)) return COLLEGE_ID;
                            if ("head_employee_id".equals(col)) return USER_ID;
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
