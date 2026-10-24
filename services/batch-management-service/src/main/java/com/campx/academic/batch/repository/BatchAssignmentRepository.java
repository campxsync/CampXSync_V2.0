package com.campx.academic.batch.repository;

import com.campx.academic.batch.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for ACD-04 Batch Management Service relational operations.
 */
public interface BatchAssignmentRepository {

    /**
     * Persists or updates a student assignment to an academic batch in {@code acd.batch_students}.
     * Enforces tenant boundary, duplicate idempotency, and audit logging.
     *
     * @param ctx Authenticated user security context containing tenant context.
     * @param assignment Entity representing the desired batch assignment.
     * @return Authoritative persisted entity with generated/updated fields.
     */
    BatchAssignmentEntity assignStudent(UserSecurityContext ctx, BatchAssignmentEntity assignment);

    /**
     * Finds an active or historical assignment for a student in a batch.
     */
    Optional<BatchAssignmentEntity> findAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId);

    /**
     * Lists all active student assignments enrolled in a batch.
     */
    List<BatchAssignmentEntity> listAssignmentsByBatch(UserSecurityContext ctx, UUID batchId);

    /**
     * Soft-removes or ends a student's batch assignment, setting status to 'ENDED' or 'DROPPED'.
     */
    BatchAssignmentEntity removeAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId, String reason);

    /**
     * Verifies that the specified batch exists and belongs to the caller's tenant.
     */
    boolean verifyBatchExists(UserSecurityContext ctx, UUID batchId);

    /**
     * Verifies that the specified student exists and belongs to the caller's tenant.
     */
    boolean verifyStudentExists(UserSecurityContext ctx, UUID studentId);
}
