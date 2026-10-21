package com.campx.platform.intelligence;

import com.campx.logger.context.SecurityMasker;

import java.util.UUID;

/**
 * Validates invariant contract rules for publishing events to the CampXSync Intelligence Layer.
 * Fails fast if tenant context is missing, correlation is absent, or unmasked credentials leak.
 *
 * @author CampX Platform Engineering Team
 * @version 1.0.0
 * @since 2.0.0
 */
public final class IntelligenceContractValidator {

    private IntelligenceContractValidator() {}

    /**
     * Validates that the event satisfies the governed Intelligence Layer contract.
     *
     * @param event the event to inspect
     * @throws IllegalArgumentException if contract requirements are violated
     * @throws SecurityException        if least privilege is violated
     */
    public static void validate(IntelligenceContextEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("IntelligenceContextEvent cannot be null");
        }

        // 1. Tenant boundary invariant
        if (event.getTenantId() == null || event.getTenantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Contract violation: tenantId is strictly required to preserve multi-tenant isolation");
        }
        try {
            UUID.fromString(event.getTenantId().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Contract violation: tenantId must be a valid UUID string: " + event.getTenantId());
        }

        // 2. Correlation and causation invariant
        if (event.getCorrelationId() == null || event.getCorrelationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Contract violation: correlationId is required for distributed tracing and audit correlation");
        }

        // 3. Schema versioning invariant
        if (event.getSchemaVersion() == null || !IntelligenceContextEvent.SCHEMA_VERSION.equals(event.getSchemaVersion().trim())) {
            throw new IllegalArgumentException("Contract violation: unsupported schemaVersion: " + event.getSchemaVersion()
                    + " (expected " + IntelligenceContextEvent.SCHEMA_VERSION + ")");
        }

        // 4. Idempotency key invariant
        if (event.getIdempotencyKey() == null || event.getIdempotencyKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Contract violation: idempotencyKey is required for deduplication");
        }

        // 5. Least-privilege data minimization: payload must not contain unmasked credentials
        if (event.getPayloadJson() != null && !event.getPayloadJson().trim().isEmpty()) {
            SecurityMasker.assertLeastPrivilege(event.getPayloadJson());
        }
    }
}
