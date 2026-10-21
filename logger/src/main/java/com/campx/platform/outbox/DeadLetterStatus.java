package com.campx.platform.outbox;

/**
 * Status enumeration for dead-letter queue records aligned with
 * {@code sys.dead_letter_events.status} check constraint:
 * {@code check (status in ('FAILED', 'REPLAYED', 'DISCARDED'))}.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public enum DeadLetterStatus {
    FAILED,
    REPLAYED,
    DISCARDED
}
