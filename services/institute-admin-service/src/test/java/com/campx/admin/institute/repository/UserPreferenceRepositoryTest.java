package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileNotFoundException;
import com.campx.admin.institute.model.preference.UserPreferences;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.logger.context.LogContext;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.OutboxRepository;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests validating {@link UserPreferenceRepository} query generation,
 * tenant isolation boundaries, transactional audit and outbox execution,
 * and Intelligence Layer contract event publication.
 */
public class UserPreferenceRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private List<String> executedSqls;
    private Map<Integer, Object> lastParameters;
    private boolean commitCalled;
    private boolean rollbackCalled;

    // Mock query state
    private boolean userExists = true;
    private UUID dbTenantId = TENANT_ID;
    private String dbPreferencesJson = "{\"version\":1,\"theme\":\"light\"}";

    private TestAuditLogRepository testAuditRepository;
    private TestOutboxRepository testOutboxRepository;
    private ControlledIntelligenceAdapter testIntelligenceAdapter;

    private UserPreferenceRepository repository;
    private UserSecurityContext callerContext;

    @Before
    public void setUp() {
        LogContext.clear();
        executedSqls = new ArrayList<>();
        lastParameters = new HashMap<>();
        commitCalled = false;
        rollbackCalled = false;

        userExists = true;
        dbTenantId = TENANT_ID;
        dbPreferencesJson = "{\"version\":1,\"theme\":\"light\"}";

        testAuditRepository = new TestAuditLogRepository();
        testOutboxRepository = new TestOutboxRepository();
        testIntelligenceAdapter = new ControlledIntelligenceAdapter();

        DatabaseConnectionManager mockConnManager = createMockConnectionManager();

        repository = new UserPreferenceRepository(
                mockConnManager,
                testAuditRepository,
                new TransactionalOutboxWriter(testOutboxRepository),
                testIntelligenceAdapter
        );

        callerContext = new UserSecurityContext(USER_ID, TENANT_ID);
    }

    @After
    public void tearDown() {
        LogContext.clear();
    }

    @Test
    public void testGetPreferencesSuccess() {
        UserPreferences prefs = repository.getPreferences(callerContext, "college_admin");
        assertNotNull(prefs);
        assertEquals(1, prefs.getVersion());
        assertEquals("light", prefs.getTheme());

        // Verify SQL execution
        assertTrue(executedSqls.stream().anyMatch(sql -> sql.contains("SELECT id, tenant_id, preferences, row_version FROM iam.user_profiles")));
        assertTrue(commitCalled);
        assertFalse(rollbackCalled);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testGetPreferencesRejectsCrossTenantViolation() {
        // Database profile belongs to OTHER_TENANT_ID, while caller context is TENANT_ID
        this.dbTenantId = OTHER_TENANT_ID;
        repository.getPreferences(callerContext, "college_admin");
    }

    @Test(expected = UserProfileNotFoundException.class)
    public void testGetPreferencesThrowsWhenUserNotFound() {
        this.userExists = false;
        repository.getPreferences(callerContext, "college_admin");
    }

    @Test
    public void testUpdatePreferencesSavesAndEmitsAuditOutboxIntelligence() {
        UserPreferences incoming = new UserPreferences();
        incoming.setTheme("dark");
        incoming.setTableDensity("compact");
        incoming.setDefaultDateRangeDays(30);

        UserPreferences updated = repository.updatePreferences(callerContext, incoming, "college_admin");
        assertNotNull(updated);
        assertEquals("dark", updated.getTheme());
        assertEquals("compact", updated.getTableDensity());
        assertEquals(30, updated.getDefaultDateRangeDays());

        // 1. Transaction committed
        assertTrue(commitCalled);
        assertFalse(rollbackCalled);

        // 2. Profile update SQL executed
        assertTrue(executedSqls.stream().anyMatch(sql -> sql.contains("UPDATE iam.user_profiles")));

        // 3. Shared Services Audit Record written
        assertEquals(1, testAuditRepository.savedRecords.size());
        AuditRecord auditRecord = testAuditRepository.savedRecords.get(0);
        assertEquals("USER_PREFERENCE_UPDATED", auditRecord.getAction());
        assertEquals(TENANT_ID, auditRecord.getTenantId());
        assertEquals(USER_ID, auditRecord.getPrincipalId());
        assertEquals("USER_PREFERENCES", auditRecord.getResourceType());

        // 4. Shared Services Transactional Outbox Event written
        assertEquals(1, testOutboxRepository.savedEvents.size());
        com.campx.platform.outbox.OutboxEvent outboxEvent = testOutboxRepository.savedEvents.get(0);
        assertEquals("UserPreferenceUpdated", outboxEvent.getEventType());
        assertEquals(TENANT_ID, outboxEvent.getTenantId());
        assertEquals("institute-admin-service", outboxEvent.getService());
        assertEquals("USER_PROFILE", outboxEvent.getAggregateType());
        assertEquals(USER_ID, outboxEvent.getAggregateId());
        assertTrue(outboxEvent.getPayload().contains("\"theme\":\"dark\""));

        // 5. Intelligence Layer Contract Event published
        assertEquals(1, testIntelligenceAdapter.getCapturedCount());
        IntelligenceContextEvent intelEvent = testIntelligenceAdapter.getPublishedEvents().get(0);
        assertEquals("UserPreferenceUpdated", intelEvent.getEventType());
        assertEquals(TENANT_ID.toString(), intelEvent.getTenantId());
        assertEquals(USER_ID.toString(), intelEvent.getAggregateId());
        assertEquals("institute-admin-service", intelEvent.getSourceService());
        assertTrue(intelEvent.getPayloadJson().contains("\"theme\":\"dark\""));
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testUpdatePreferencesRejectsCrossTenantViolation() {
        // Database profile belongs to OTHER_TENANT_ID, while caller context is TENANT_ID
        this.dbTenantId = OTHER_TENANT_ID;

        UserPreferences incoming = new UserPreferences();
        incoming.setTheme("dark");
        repository.updatePreferences(callerContext, incoming, "college_admin");
    }

    // =========================================================================
    // Mock Helpers
    // =========================================================================

    private static class TestAuditLogRepository implements AuditLogRepository {
        final List<AuditRecord> savedRecords = new ArrayList<>();

        @Override
        public void appendAuditEvent(Connection conn, AuditRecord record) {
            savedRecords.add(record);
        }

        @Override
        public void appendChangeLog(Connection conn, com.campx.platform.audit.AuditChangeLog changeLog) {}

        @Override
        public List<AuditRecord> findAuditEventsByTenant(Connection conn, UUID tenantId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<com.campx.platform.audit.AuditChangeLog> findChangeLogsByRecord(Connection conn, UUID tenantId, String tableSchema, String tableName, UUID recordId) {
            return Collections.emptyList();
        }
    }

    private static class TestOutboxRepository implements OutboxRepository {
        final List<com.campx.platform.outbox.OutboxEvent> savedEvents = new ArrayList<>();

        @Override
        public void save(Connection conn, com.campx.platform.outbox.OutboxEvent event) {
            savedEvents.add(event);
        }

        @Override
        public void saveAll(Connection conn, List<com.campx.platform.outbox.OutboxEvent> events) {
            savedEvents.addAll(events);
        }

        @Override
        public Optional<com.campx.platform.outbox.OutboxEvent> findById(Connection conn, UUID tenantId, UUID eventId) {
            return Optional.empty();
        }

        @Override
        public List<com.campx.platform.outbox.OutboxEvent> findPendingEvents(Connection conn, UUID tenantId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markDispatched(Connection conn, UUID tenantId, UUID eventId, java.time.Instant dispatchedAt) {}

        @Override
        public void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, java.time.Instant nextAttemptAt, String errorDetails) {}

        @Override
        public void moveToDeadLetter(Connection conn, com.campx.platform.outbox.OutboxEvent event, String failureReason) {}

        @Override
        public int deleteDispatchedEvents(Connection conn, UUID tenantId, java.time.Instant olderThan) {
            return 0;
        }
    }

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
                        if ("commit".equals(name)) {
                            commitCalled = true;
                            return null;
                        }
                        if ("rollback".equals(name)) {
                            rollbackCalled = true;
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return false;
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
        };
    }

    private Statement createMockStatement() {
        return (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName()) && args.length > 0) {
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
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
                        if ("close".equals(name)) return null;
                        return null;
                    }
                }
        );
    }

    private ResultSet createMockResultSet(String sql) {
        return (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    private int cursor = -1;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            cursor++;
                            if (sql.contains("SELECT id, tenant_id, preferences, row_version FROM iam.user_profiles")) {
                                return cursor == 0 && userExists;
                            }
                            if (sql.contains("set_config")) {
                                return cursor == 0;
                            }
                            return false;
                        }
                        if ("getObject".equals(name)) {
                            String col = (String) args[0];
                            if ("id".equalsIgnoreCase(col)) return USER_ID;
                            if ("tenant_id".equalsIgnoreCase(col)) return dbTenantId;
                            return null;
                        }
                        if ("getString".equals(name)) {
                            String col = (String) args[0];
                            if ("preferences".equalsIgnoreCase(col)) return dbPreferencesJson;
                            return null;
                        }
                        if ("getInt".equals(name)) {
                            return 1; // row_version
                        }
                        if ("close".equals(name)) return null;
                        return null;
                    }
                }
        );
    }
}
