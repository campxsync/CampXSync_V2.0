package com.campx.academic.subject.repository;

import com.campx.academic.subject.model.SubjectModels.Subject;
import com.campx.academic.subject.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@code acd.subjects} persistence.
 */
public interface SubjectRepository {

    Subject createSubject(UserSecurityContext context, Subject subject);

    Optional<Subject> findById(UserSecurityContext context, String id);

    Optional<Subject> findByCode(UserSecurityContext context, String code);

    List<Subject> listSubjects(UserSecurityContext context, String departmentId, String status, String codeContains, String nameContains, int offset, int limit);

    void updateSubject(UserSecurityContext context, Subject subject);

    void updateStatus(UserSecurityContext context, String subjectId, String status);

    void deleteSubject(UserSecurityContext context, String id);
}
