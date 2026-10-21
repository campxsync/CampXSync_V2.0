package com.campx.platform.outbox;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

public class PostgresOutboxRepositoryTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private PostgresOutboxRepository repository;

    private String lastExecutedSql;
    private final Map<Integer, Object> boundParameters = new HashMap<>();

    @Before
    public void setUp() {
        repository = new PostgresOutboxRepository();
        lastExecutedSql = null;
        boundParameters.clear();
    }

    @Test
    public void testSaveBindsAllColumnsIncludingTenantId() throws SQLException {
        Connection mockConn = createInterceptingConnection();

        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .tenantId(TENANT_ID)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(aggregateId)
                .eventType("StudentCreated")
                .payload("{\"enrollmentNo\":\"STU-001\"}")
                .build();

        repository.save(mockConn, event);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.startsWith("INSERT INTO sys.outbox_events"));
        assertEquals(eventId, boundParameters.get(1));
        assertEquals(TENANT_ID, boundParameters.get(2)); // Tenant ID strictly bound at param 2
        assertEquals("student-service", boundParameters.get(3));
        assertEquals("Student", boundParameters.get(4));
        assertEquals(aggregateId, boundParameters.get(5));
        assertEquals("StudentCreated", boundParameters.get(6));
        assertEquals("{\"enrollmentNo\":\"STU-001\"}", boundParameters.get(7));
        assertEquals("PENDING", boundParameters.get(9));
    }

    @Test
    public void testFindByIdEnforcesTenantPartitioning() throws SQLException {
        Connection mockConn = createInterceptingConnection();
        UUID eventId = UUID.randomUUID();

        repository.findById(mockConn, TENANT_ID, eventId);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ? AND id = ?"));
        assertEquals(TENANT_ID, boundParameters.get(1)); // Param 1 must be tenant_id
        assertEquals(eventId, boundParameters.get(2));
    }

    @Test
    public void testFindPendingEventsEnforcesTenantPartitioning() throws SQLException {
        Connection mockConn = createInterceptingConnection();

        repository.findPendingEvents(mockConn, TENANT_ID, 25);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ? AND status = 'PENDING'"));
        assertEquals(TENANT_ID, boundParameters.get(1));
        assertEquals(25, boundParameters.get(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindPendingEventsRejectsNullTenant() throws SQLException {
        Connection mockConn = createInterceptingConnection();
        repository.findPendingEvents(mockConn, null, 10);
    }

    @Test
    public void testMarkDispatchedEnforcesTenantPartitioning() throws SQLException {
        Connection mockConn = createInterceptingConnection();
        UUID eventId = UUID.randomUUID();

        repository.markDispatched(mockConn, TENANT_ID, eventId);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ? AND id = ?"));
        assertEquals(TENANT_ID, boundParameters.get(2));
        assertEquals(eventId, boundParameters.get(3));
    }

    @Test
    public void testRecordFailureBindsAttemptAndTenant() throws SQLException {
        Connection mockConn = createInterceptingConnection();
        UUID eventId = UUID.randomUUID();
        Instant nextAttempt = Instant.now().plusSeconds(30);

        repository.recordFailure(mockConn, TENANT_ID, eventId, 2, nextAttempt);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ? AND id = ?"));
        assertEquals(2, boundParameters.get(1));
        assertEquals(TENANT_ID, boundParameters.get(3));
        assertEquals(eventId, boundParameters.get(4));
    }

    @Test
    public void testDeleteDispatchedEventsEnforcesTenantBoundary() throws SQLException {
        Connection mockConn = createInterceptingConnection();
        Instant cutoff = Instant.now().minusSeconds(86400);

        repository.deleteDispatchedEvents(mockConn, TENANT_ID, cutoff);

        assertNotNull(lastExecutedSql);
        assertTrue(lastExecutedSql.contains("WHERE tenant_id = ? AND status = 'DISPATCHED'"));
        assertEquals(TENANT_ID, boundParameters.get(1));
    }

    private ResultSet customResultSet;

    @Test
    public void testFindByIdPreservesHeadersAndCorrelationContext() throws SQLException {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String headersJson = "{\"correlationId\":\"corr-123\",\"traceId\":\"trace-abc\",\"actorId\":\"user-admin\",\"schemaVersion\":\"1.0.0\",\"idempotencyKey\":\"idemp-999\",\"X-Custom\":\"value\"}";

        Map<String, Object> row = new HashMap<>();
        row.put("id", eventId);
        row.put("tenant_id", TENANT_ID);
        row.put("service", "attendance-service");
        row.put("aggregate_type", "Attendance");
        row.put("aggregate_id", aggregateId);
        row.put("event_type", "AttendanceMarked");
        row.put("payload", "{\"count\": 1}");
        row.put("headers", headersJson);
        row.put("status", "PENDING");
        row.put("attempts", 0);
        row.put("created_at", java.sql.Timestamp.from(Instant.now()));
        row.put("updated_at", java.sql.Timestamp.from(Instant.now()));

        customResultSet = createSingleRowResultSet(row);
        Connection mockConn = createInterceptingConnection();

        Optional<OutboxEvent> opt = repository.findById(mockConn, TENANT_ID, eventId);
        assertTrue(opt.isPresent());
        OutboxEvent event = opt.get();
        assertEquals(eventId, event.getId());
        assertEquals(TENANT_ID, event.getTenantId());
        assertEquals("attendance-service", event.getService());

        // Assert headers were preserved and NOT overwritten by defaults
        OutboxHeaders headers = event.getHeaders();
        assertNotNull(headers);
        assertEquals("corr-123", headers.getCorrelationId());
        assertEquals("trace-abc", headers.getTraceId());
        assertEquals("user-admin", headers.getActorId());
        assertEquals("idemp-999", headers.getIdempotencyKey());
        assertEquals("value", headers.getCustomHeaders().get("X-Custom"));
    }

    @Test
    public void testFindByIdHandlesStringUuidSafely() throws SQLException {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        Map<String, Object> row = new HashMap<>();
        // Drivers or poolers returning Strings instead of UUID objects
        row.put("id", eventId.toString());
        row.put("tenant_id", TENANT_ID.toString());
        row.put("service", "core-service");
        row.put("aggregate_type", "Course");
        row.put("aggregate_id", aggregateId.toString());
        row.put("event_type", "CourseUpdated");
        row.put("payload", "{}");
        row.put("headers", "{}");
        row.put("status", "PENDING");
        row.put("attempts", 0);

        customResultSet = createSingleRowResultSet(row);
        Connection mockConn = createInterceptingConnection();

        Optional<OutboxEvent> opt = repository.findById(mockConn, TENANT_ID, eventId);
        assertTrue(opt.isPresent());
        assertEquals(eventId, opt.get().getId());
        assertEquals(TENANT_ID, opt.get().getTenantId());
        assertEquals(aggregateId, opt.get().getAggregateId());
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
                        if ("setNull".equals(name) && args != null && args.length >= 2) {
                            if (args[0] instanceof Integer) {
                                boundParameters.put((Integer) args[0], null);
                            }
                        }
                        if ("executeUpdate".equals(name) || "executeBatch".equals(name)) {
                            return 1;
                        }
                        if ("executeQuery".equals(name)) {
                            return customResultSet != null ? customResultSet : createEmptyResultSet();
                        }
                        if ("close".equals(name)) return null;
                        return null;
                    }
                });
    }

    private ResultSet createSingleRowResultSet(final Map<String, Object> data) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                new InvocationHandler() {
                    private boolean returned = false;
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            if (!returned) {
                                returned = true;
                                return true;
                            }
                            return false;
                        }
                        if ("getObject".equals(name) || "getString".equals(name) || "getTimestamp".equals(name)) {
                            String col = (String) args[0];
                            return data.get(col);
                        }
                        if ("getInt".equals(name)) {
                            String col = (String) args[0];
                            Object val = data.get(col);
                            return val instanceof Number ? ((Number) val).intValue() : 0;
                        }
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
