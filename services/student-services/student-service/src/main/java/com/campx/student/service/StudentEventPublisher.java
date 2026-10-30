package com.campx.student.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.security.UserSecurityContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event publisher interface for transactional outbox events in the Student Domain.
 * Decouples event publication for pure in-memory testing while supporting JDBC transactional outbox in production.
 */
public interface StudentEventPublisher {

    void publish(UserSecurityContext context, OutboxEvent event);

    List<OutboxEvent> getPublishedEvents();

    void clear();

    /**
     * In-memory implementation capturing events in a thread-safe list for unit and contract verification.
     */
    class InMemory implements StudentEventPublisher {
        private final List<OutboxEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(UserSecurityContext context, OutboxEvent event) {
            if (event != null) {
                events.add(event);
            }
        }

        @Override
        public List<OutboxEvent> getPublishedEvents() {
            return new ArrayList<>(events);
        }

        @Override
        public void clear() {
            events.clear();
        }
    }

    /**
     * PostgreSQL JDBC implementation persisting events into {@code sys.outbox_events}
     * within the authenticated tenant transaction context.
     */
    class PostgresOutboxPublisher implements StudentEventPublisher {
        private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresOutboxPublisher.class);
        private final DatabaseConnectionManager connectionManager;
        private final TransactionalOutboxWriter writer;
        private final List<OutboxEvent> inMemoryEcho = Collections.synchronizedList(new ArrayList<>());

        public PostgresOutboxPublisher() {
            this(DatabaseConnectionManager.getInstance(), new TransactionalOutboxWriter());
        }

        public PostgresOutboxPublisher(DatabaseConnectionManager connectionManager) {
            this(connectionManager, new TransactionalOutboxWriter());
        }

        public PostgresOutboxPublisher(DatabaseConnectionManager connectionManager, TransactionalOutboxWriter writer) {
            this.connectionManager = connectionManager != null ? connectionManager : DatabaseConnectionManager.getInstance();
            this.writer = writer != null ? writer : new TransactionalOutboxWriter();
        }

        @Override
        public void publish(UserSecurityContext context, OutboxEvent event) {
            if (event == null) return;
            inMemoryEcho.add(event);
            if (context != null) {
                context.executeInTransaction(connectionManager, conn -> {
                    try {
                        writer.write(conn, event);
                    } catch (SQLException e) {
                        logger.error("Failed to write transactional outbox event {}: {}", event.getEventType(), e.getMessage(), e);
                        throw new RuntimeException("Transactional outbox persistence failed: " + e.getMessage(), e);
                    }
                    return null;
                });
            }
        }

        @Override
        public List<OutboxEvent> getPublishedEvents() {
            return new ArrayList<>(inMemoryEcho);
        }

        @Override
        public void clear() {
            inMemoryEcho.clear();
        }
    }
}
