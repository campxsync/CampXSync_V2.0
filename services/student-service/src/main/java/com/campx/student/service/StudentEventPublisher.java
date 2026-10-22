package com.campx.student.service;

import com.campx.platform.outbox.OutboxEvent;
import com.campx.student.security.UserSecurityContext;

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
}
