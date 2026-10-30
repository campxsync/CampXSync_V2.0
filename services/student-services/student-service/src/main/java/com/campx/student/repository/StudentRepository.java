package com.campx.student.repository;

import com.campx.student.model.CursorPagedResult;
import com.campx.student.model.StudentEntity;
import com.campx.student.model.StudentRosterItem;
import com.campx.student.model.StudentRosterQuery;
import com.campx.student.security.UserSecurityContext;

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
}
