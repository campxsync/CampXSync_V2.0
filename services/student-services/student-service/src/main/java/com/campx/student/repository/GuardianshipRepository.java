package com.campx.student.repository;

import com.campx.student.model.GuardianshipEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for family and legal relationships in {@code stu.guardianships}.
 */
public interface GuardianshipRepository {

    GuardianshipEntity save(UserSecurityContext context, GuardianshipEntity guardianship);

    List<GuardianshipEntity> findByStudentId(UserSecurityContext context, UUID studentId);

    Optional<GuardianshipEntity> findPrimaryByStudentId(UserSecurityContext context, UUID studentId);
}
