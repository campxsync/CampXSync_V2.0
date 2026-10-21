package com.campx.platform.outbox;

/**
 * Status enumeration for transactional outbox events aligned with
 * the {@code sys.outbox_events} table check constraint:
 * {@code check (status in ('PENDING', 'DISPATCHED', 'FAILED'))}.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public enum OutboxStatus {
    PENDING,
    DISPATCHED,
    FAILED
}
