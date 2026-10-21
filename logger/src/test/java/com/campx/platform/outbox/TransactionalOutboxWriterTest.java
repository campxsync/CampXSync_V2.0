package com.campx.platform.outbox;

import com.campx.logger.context.LogContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;

public class TransactionalOutboxWriterTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FakeOutboxRepository fakeRepository;
    private TransactionalOutboxWriter writer;

    @Before
    public void setUp() {
        LogContext.clear();
        fakeRepository = new FakeOutboxRepository();
        writer = new TransactionalOutboxWriter(fakeRepository);
    }

    @After
    public void tearDown() {
        LogContext.clear();
    }

    @Test(expected = IllegalStateException.class)
    public void testRejectsWhenAutoCommitIsTrue() throws SQLException {
        Connection conn = createMockConnection(true); // AutoCommit is true (not in transaction)

        OutboxEvent event = OutboxEvent.builder()
                .tenantId(TENANT_A)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{\"status\":\"active\"}")
                .build();

        writer.write(conn, event);
    }

    @Test
    public void testWritesSuccessfullyWhenInTransaction() throws SQLException {
        Connection conn = createMockConnection(false); // AutoCommit is false (active transaction)

        LogContext.setTraceId("trace-xyz-777");
        LogContext.setUserId("user-faculty-01");

        OutboxEvent event = OutboxEvent.builder()
                .tenantId(TENANT_A)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{\"status\":\"active\"}")
                .build();

        OutboxEvent persisted = writer.write(conn, event);

        assertEquals(1, fakeRepository.savedEvents.size());
        assertEquals(TENANT_A, persisted.getTenantId());
        // Enriched from LogContext
        assertEquals("trace-xyz-777", persisted.getHeaders().getTraceId());
        assertEquals("user-faculty-01", persisted.getHeaders().getActorId());
    }

    @Test(expected = SecurityException.class)
    public void testRejectsCrossTenantContextMismatch() throws SQLException {
        Connection conn = createMockConnection(false);

        // Active thread context is Tenant A
        LogContext.setTenantId(TENANT_A.toString());

        // Event payload targets Tenant B (cross-tenant violation)
        OutboxEvent crossTenantEvent = OutboxEvent.builder()
                .tenantId(TENANT_B)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{\"status\":\"active\"}")
                .build();

        writer.write(conn, crossTenantEvent);
    }

    @Test(expected = SecurityException.class)
    public void testWriteAllInTransactionRejectsCrossTenantMismatch() throws SQLException {
        Connection conn = createMockConnection(false);
        LogContext.setTenantId(TENANT_A.toString());

        OutboxEvent validEvent = OutboxEvent.builder()
                .tenantId(TENANT_A)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{}")
                .build();

        OutboxEvent crossTenantEvent = OutboxEvent.builder()
                .tenantId(TENANT_B)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{}")
                .build();

        writer.writeAllInTransaction(conn, Arrays.asList(validEvent, crossTenantEvent));
    }

    @Test
    public void testWriteEventEnforcesContextTenantAndPersists() throws SQLException {
        Connection conn = createMockConnection(false);
        LogContext.setTenantId(TENANT_A.toString());
        LogContext.setTraceId("trace-writer-123");
        LogContext.setUserId("user-faculty-02");

        OutboxEvent written = writer.writeEvent(conn, TENANT_A, "attendance-service", "Attendance",
                UUID.randomUUID(), "AttendanceMarked", "{\"count\": 1}");

        assertEquals(1, fakeRepository.savedEvents.size());
        assertEquals(TENANT_A, written.getTenantId());
        assertEquals("trace-writer-123", written.getHeaders().getTraceId());
        assertEquals("user-faculty-02", written.getHeaders().getActorId());
    }

    private Connection createMockConnection(final boolean autoCommitValue) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("getAutoCommit".equals(method.getName())) {
                            return autoCommitValue;
                        }
                        if ("isClosed".equals(method.getName())) {
                            return false;
                        }
                        if ("toString".equals(method.getName())) {
                            return "MockConnection[autoCommit=" + autoCommitValue + "]";
                        }
                        return null;
                    }
                });
    }

    private static class FakeOutboxRepository implements OutboxRepository {
        final List<OutboxEvent> savedEvents = new ArrayList<>();

        @Override
        public void save(Connection conn, OutboxEvent event) throws SQLException {
            savedEvents.add(event);
        }

        @Override
        public void saveAll(Connection conn, List<OutboxEvent> events) throws SQLException {
            savedEvents.addAll(events);
        }

        @Override
        public Optional<OutboxEvent> findById(Connection conn, UUID tenantId, UUID eventId) throws SQLException {
            for (OutboxEvent e : savedEvents) {
                if (e.getTenantId().equals(tenantId) && e.getId().equals(eventId)) {
                    return Optional.of(e);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<OutboxEvent> findPendingEvents(Connection conn, UUID tenantId, int limit) throws SQLException {
            List<OutboxEvent> list = new ArrayList<>();
            for (OutboxEvent e : savedEvents) {
                if (e.getTenantId().equals(tenantId) && e.getStatus() == OutboxStatus.PENDING) {
                    list.add(e);
                }
            }
            return list;
        }

        @Override
        public void markDispatched(Connection conn, UUID tenantId, UUID eventId, java.time.Instant dispatchedAt) throws SQLException {}

        @Override
        public void recordFailure(Connection conn, UUID tenantId, UUID eventId, int attempt, java.time.Instant nextAttemptAt, String errorDetails) throws SQLException {}

        @Override
        public void moveToDeadLetter(Connection conn, OutboxEvent event, String reason) throws SQLException {}

        @Override
        public int deleteDispatchedEvents(Connection conn, UUID tenantId, java.time.Instant olderThan) throws SQLException {
            return 0;
        }
    }
}
