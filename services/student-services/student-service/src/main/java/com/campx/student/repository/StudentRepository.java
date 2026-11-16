package com.campx.student.repository;

import com.campx.student.model.CursorPagedResult;
import com.campx.student.model.ProvisioningRequestRecord;
import com.campx.student.model.StudentEntity;
import com.campx.student.model.StudentRosterItem;
import com.campx.student.model.StudentRosterQuery;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for canonical student academic records in {@code stu.students}.
 */
public interface StudentRepository {

    StudentEntity save(UserSecurityContext context, StudentEntity student);

    Optional<StudentEntity> findById(UserSecurityContext context, UUID id);

    Optional<StudentEntity> findByEnrollmentNo(UserSecurityContext context, String enrollmentNo);

    boolean existsByEnrollmentNo(UserSecurityContext context, String enrollmentNo);

    CursorPagedResult<StudentRosterItem> findRoster(UserSecurityContext context, StudentRosterQuery query);

    Optional<StudentEntity> findByPersonId(UserSecurityContext context, UUID personId);

    Optional<StudentEntity> findByIamUserId(UserSecurityContext context, UUID iamUserId);

    long countByTenant(UserSecurityContext context);

    void delete(UserSecurityContext context, UUID id);

    /**
     * Persists an account-provisioning request row (STM-07-EN-001).
     * Returns the new request id.
     */
    UUID saveProvisioningRequest(UserSecurityContext context, UUID studentId, UUID requestedIamUserId);

    /**
     * Counts non-deleted provisioning requests for a student (STM-07-EN-001 readiness).
     */
    long countProvisioningRequests(UserSecurityContext context, UUID studentId);

    /**
     * STM-07-EN-004 — find an open REQUESTED provisioning row for idempotent create.
     */
    Optional<ProvisioningRequestRecord> findOpenProvisioningRequest(UserSecurityContext context, UUID studentId);

    /**
     * STM-07-EN-004 — load a provisioning request by id (tenant-scoped).
     */
    Optional<ProvisioningRequestRecord> findProvisioningRequestById(UserSecurityContext context, UUID requestId);

    /**
     * STM-07-EN-004 — list non-deleted provisioning requests newest-first.
     */
    List<ProvisioningRequestRecord> listProvisioningRequests(UserSecurityContext context, UUID studentId);

    /**
     * STM-07-EN-004 — update request status / failure reason (FAILED / COMPLETED / REQUESTED).
     */
    void updateProvisioningRequestStatus(UserSecurityContext context, UUID requestId, String status, String failureReason);

    /**
     * Soft-revokes all ACTIVE IAM links for a student (STM-07-US-010).
     */
    void revokeActiveIamLinks(UserSecurityContext context, UUID studentId);
}
