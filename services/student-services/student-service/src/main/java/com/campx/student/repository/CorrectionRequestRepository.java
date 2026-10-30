package com.campx.student.repository;

import com.campx.student.model.StudentCorrectionRequestEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for profile correction requests (STM01-US-010, STM01-US-011).
 */
public interface CorrectionRequestRepository {

    StudentCorrectionRequestEntity save(UserSecurityContext context, StudentCorrectionRequestEntity request);

    Optional<StudentCorrectionRequestEntity> findById(UserSecurityContext context, UUID id);

    List<StudentCorrectionRequestEntity> findByStudentId(UserSecurityContext context, UUID studentId);

    List<StudentCorrectionRequestEntity> findPendingByTenant(UserSecurityContext context);
}
