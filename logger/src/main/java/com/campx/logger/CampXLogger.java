package com.campx.logger;

import com.campx.logger.api.ILogger;

/**
 * Primary public logging contract for the CampXSync College ERP enterprise ecosystem.
 * <p>
 * Combines traditional multi-level logging, fluent method chaining,
 * distributed execution flow latency tracking, and regulatory security audit logging.
 *
 * @see CampXLoggerFactory
 * @see ILogger
 * @see com.campx.logger.api.FlowTracker
 * @see com.campx.logger.api.FluentLogBuilder
 * @see com.campx.logger.api.AuditEvent
 */
public interface CampXLogger extends ILogger {
}
