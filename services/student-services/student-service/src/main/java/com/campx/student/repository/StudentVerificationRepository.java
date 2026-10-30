package com.campx.student.repository;

import com.campx.student.model.StudentVerificationEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for profile verification records (STM01-US-013, STM01-US-014).
 */
public interface StudentVerificationRepository {

    StudentVerificationEntity save(UserSecurityContext context, StudentVerificationEntity verification);

    Optional<StudentVerificationEntity> findById(UserSecurityContext context, UUID id);

    List<StudentVerificationEntity> findByStudentId(UserSecurityContext context, UUID studentId);

    Optional<StudentVerificationEntity> findLatestByStudentId(UserSecurityContext context, UUID studentId);
}
