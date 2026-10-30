package com.campx.student.repository;

import com.campx.student.model.StudentAddressEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for physical addresses in {@code core.addresses} (STM01-US-017).
 */
public interface StudentAddressRepository {

    StudentAddressEntity save(UserSecurityContext context, StudentAddressEntity address);

    Optional<StudentAddressEntity> findById(UserSecurityContext context, UUID id);
}
