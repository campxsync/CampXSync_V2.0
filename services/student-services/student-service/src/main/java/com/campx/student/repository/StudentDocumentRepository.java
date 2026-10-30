package com.campx.student.repository;

import com.campx.student.model.StudentDocumentEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for document metadata linked to student profiles (STM01-US-020, STM01-US-021).
 */
public interface StudentDocumentRepository {

    StudentDocumentEntity save(UserSecurityContext context, StudentDocumentEntity document);

    Optional<StudentDocumentEntity> findById(UserSecurityContext context, UUID id);

    List<StudentDocumentEntity> findByStudentId(UserSecurityContext context, UUID studentId);
}
