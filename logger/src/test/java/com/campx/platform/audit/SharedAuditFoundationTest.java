package com.campx.platform.audit;

import com.campx.logger.api.AuditEvent;
import com.campx.platform.outbox.OutboxEvent;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;

public class SharedAuditFoundationTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COLLEGE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRINCIPAL_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private String lastExecutedSql;
    private final Map<Integer, Object> boundParameters = new HashMap<>();

    @Before
    public void setUp() {
        lastExecutedSql = null;
        boundParameters.clear();
    }

    @Test
    public void testAuditEventBackwardCompatibilityAndEnhancements() {
        // Test legacy constructor works without any changes
        Map<String, String> meta = new HashMap<>();
        meta.put("source", "web");
        AuditEvent legacy = new AuditEvent(
                "USER_LOGIN",
                "admin-1",
                "SESSION",
                "sess-99",
                "User successfully logged in",
                meta
        );
        assertEquals("USER_LOGIN", legacy.getAction());
        assertEquals("admin-1", legacy.getPrincipalId());
        assertNull(legacy.getTenantId());

        // Test enriched constructor
        AuditEvent enriched = new AuditEvent(
                TENANT_ID.toString(),
                COLLEGE_ID.toString(),
                "STUDENT_ENROLLED",
                PRINCIPAL_ID.toString(),
                "FACULTY",
                "STUDENT",
                "stu-001",
                "SUCCESS",
                "INFO",
                "corr-xyz-123",
                "Enrolled student into CSE program",
                meta
        );
        assertEquals(TENANT_ID.toString(), enriched.getTenantId());
        assertEquals(COLLEGE_ID.toString(), enriched.getCollegeId());
        assertEquals("STUDENT_ENROLLED", enriched.getAction());
        assertEquals("corr-xyz-123", enriched.getCorrelationId());
        assertEquals("FACULTY", enriched.getPrincipalType());
    }

    @Test
    public void testAuditRecordFromAuditEvent() {
        AuditEvent event = new AuditEvent(
                TENANT_ID.toString(),
                COLLEGE_ID.toString(),
                "ATTENDANCE_CORRECTED",
                PRINCIPAL_ID.toString(),
                "COLLEGE_ADMIN",
                "ATTENDANCE_RECORD",
                UUID.randomUUID().toString(),
                "SUCCESS",
                "INFO",
                "corr-abc-999",
                "Corrected attendance status to PRESENT",
                Collections.emptyMap()
        );

        AuditRecord record = AuditRecord.fromAuditEvent(event);

        assertNotNull(record.getId());
        assertEquals(TENANT_ID, record.getTenantId());
        assertEquals(COLLEGE_ID, record.getCollegeId());
        assertEquals("ATTENDANCE_CORRECTED", record.getAction());
        assertEquals(PRINCIPAL_ID, record.getPrincipalId());
        assertEquals("COLLEGE_ADMIN", record.getPrincipalType());
        assertEquals("corr-abc-999", record.getCorrelationId());
        assertTrue(record.getDetailJson().contains("Corrected attendance status to PRESENT"));
    }

    @Test(expected = SecurityException.class)
    public void testAuditRecordRejectsUnmaskedSecrets() {
        AuditRecord.builder()
                .tenantId(TENANT_ID)
                .action("SECURITY_BREACH_TEST")
                .detailJson("{\"adminPassword\":\"SuperSecret123!\"}") // Sensitive credential rejected
                .build();
    }

    @Test(expected = SecurityException.class)
    public void testAuditChangeLogRejectsUnmaskedSecrets() {
        AuditChangeLog.builder()
                .tenantId(TENANT_ID)
                .tableSchema("iam")
                .tableName("user_profiles")
                .recordId(UUID.randomUUID())
                .action("U")
                .oldDataJson("{\"apiKey\":\"live_secret_key_9999999999999\"}") // API key rejected
                .build();
    }

    @Test
    public void testAuditToOutboxBridgeForAuditRecord() {
        AuditRecord record = AuditRecord.builder()
                .tenantId(TENANT_ID)
                .collegeId(COLLEGE_ID)
                .action("FACULTY_PROVISIONED")
                .principalId(PRINCIPAL_ID)
                .principalType("PLATFORM_ADMIN")
                .correlationId("corr-fac-888")
                .detailJson("{\"department\":\"ECE\"}")
                .build();

        OutboxEvent outboxEvent = AuditToOutboxBridge.toOutboxEvent(record, "faculty-service");

        assertNotNull(outboxEvent);
        assertEquals(TENANT_ID, outboxEvent.getTenantId());
        assertEquals("faculty-service", outboxEvent.getService());
        assertEquals("AuditEvent", outboxEvent.getAggregateType());
        assertEquals("AuditEvent.FACULTY_PROVISIONED", outboxEvent.getEventType());
        assertEquals("corr-fac-888", outboxEvent.getHeaders().getCorrelationId());
        assertEquals("1.0.0", outboxEvent.getHeaders().getSchemaVersion());
        assertTrue(outboxEvent.getPayload().contains("\"action\":\"FACULTY_PROVISIONED\""));
        assertTrue(outboxEvent.getPayload().contains("\"tenantId\":\"" + TENANT_ID + "\""));
    }

    @Test
    public void testAuditToOutboxBridgeForChangeLog() {
        UUID recordId = UUID.randomUUID();
        AuditChangeLog changeLog = AuditChangeLog.builder()
                .tenantId(TENANT_ID)
                .tableSchema("acd")
                .tableName("attendance_records")
                .recordId(recordId)
                .action("U")
                .changedFields(Arrays.asList("status", "remarks", "row_version"))
                .oldDataJson("{\"status\":\"ABSENT\",\"row_version\":1}")
                .newDataJson("{\"status\":\"PRESENT\",\"row_version\":2}")
                .actorId(PRINCIPAL_ID)
                .requestId("req-corr-456")
                .build();

        OutboxEvent outboxEvent = AuditToOutboxBridge.toOutboxEvent(changeLog, "attendance-service");

        assertNotNull(outboxEvent);
        assertEquals(TENANT_ID, outboxEvent.getTenantId());
        assertEquals("attendance-service", outboxEvent.getService());
        assertEquals("acd.attendance_records", outboxEvent.getAggregateType());
        assertEquals("EntityMutation.U", outboxEvent.getEventType());
        assertEquals("1.0.0", outboxEvent.getHeaders().getSchemaVersion());
        assertTrue(outboxEvent.getPayload().contains("\"tableSchema\":\"acd\""));
        assertTrue(outboxEvent.getPayload().contains("\"tableName\":\"attendance_records\""));
        assertTrue(outboxEvent.getPayload().contains("\"status\":\"PRESENT\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAuditToOutboxBridgeRejectsNullTenant() {
        AuditRecord recordWithoutTenant = AuditRecord.builder()
                .action("SYSTEM_STARTUP")
                .detailJson("{\"info\":\"boot\"}")
                .build();

        AuditToOutboxBridge.toOutboxEvent(recordWithoutTenant, "system");
    }

    @Test
    public void testPostgresAuditRepositoryEnforcesTenantBoundary() throws SQLException {
        PostgresAuditRepository repo = new PostgresAuditRepository();
        Connection mockConn = createInterceptingConnection();

        AuditRecord record = AuditRecord.builder()
                .tenantId(TENANT_ID)
                .action("ROLE_ASSIGNED")
                .principalId(PRINCIPAL_ID)
                .detailJson("{\"role\":\"FACULTY\"}")
                .build();

        repo.appendAuditEvent(mockConn, record);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.startsWith("INSERT INTO audit.events"));
        assertEquals(TENANT_ID, boundParameters.get(2)); // tenant_id parameter strictly set

        // Test querying audit events
        repo.findAuditEventsByTenant(mockConn, TENANT_ID, 20);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ?"));
        assertEquals(TENANT_ID, boundParameters.get(1));
    }

    @Test
    public void testPostgresAuditRepositoryChangeLogBindsAllColumnsIncludingCreatedBy() throws SQLException {
        PostgresAuditRepository repo = new PostgresAuditRepository();
        Connection mockConn = createInterceptingConnection();

        UUID recordId = UUID.randomUUID();
        AuditChangeLog changeLog = AuditChangeLog.builder()
                .tenantId(TENANT_ID)
                .tableSchema("stu")
                .tableName("students")
                .recordId(recordId)
                .action("I")
                .changedFields(Collections.singletonList("status"))
                .oldDataJson("{}")
                .newDataJson("{\"status\":\"ACTIVE\"}")
                .actorId(PRINCIPAL_ID)
                .requestId("req-trace-100")
                .createdBy(PRINCIPAL_ID)
                .build();

        repo.appendChangeLog(mockConn, changeLog);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.startsWith("INSERT INTO audit.change_log"));
        assertEquals(TENANT_ID, boundParameters.get(2)); // tenant_id parameter
        assertEquals("stu", boundParameters.get(3));
        assertEquals("students", boundParameters.get(4));
        assertEquals(recordId, boundParameters.get(5));
        assertEquals("I", boundParameters.get(6));
        assertEquals(PRINCIPAL_ID, boundParameters.get(10)); // actor_id
        assertEquals("req-trace-100", boundParameters.get(11));
        assertEquals(PRINCIPAL_ID, boundParameters.get(13)); // created_by parameter strictly set

        // Test querying change logs
        repo.findChangeLogsByRecord(mockConn, TENANT_ID, "stu", "students", recordId);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ?"));
        assertEquals(TENANT_ID, boundParameters.get(1));
    }

    private Connection createInterceptingConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("prepareStatement".equals(method.getName())) {
                            lastExecutedSql = (String) args[0];
                            return createInterceptingPreparedStatement();
                        }
                        if ("isClosed".equals(method.getName())) return false;
                        return null;
                    }
                });
    }

    private PreparedStatement createInterceptingPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if (name.startsWith("set") && args != null && args.length >= 2) {
                            if (args[0] instanceof Integer) {
                                boundParameters.put((Integer) args[0], args[1]);
                            }
                        }
                        if ("executeUpdate".equals(name) || "executeBatch".equals(name)) return 1;
                        if ("executeQuery".equals(name)) return createEmptyResultSet();
                        if ("close".equals(name)) return null;
                        return null;
                    }
                });
    }

    private ResultSet createEmptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("next".equals(method.getName())) return false;
                        if ("close".equals(method.getName())) return null;
                        return null;
                    }
                });
    }
}
