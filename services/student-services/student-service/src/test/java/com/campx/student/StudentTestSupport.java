package com.campx.student;

import com.campx.student.model.StudentEntity;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;

import java.util.UUID;

/**
 * Shared helpers for STM-07 unit tests after VERIFICATION_NOT_VERIFIED eligibility gate.
 */
final class StudentTestSupport {

    private StudentTestSupport() {
    }

    static void markVerified(InMemoryStudentRepository studentRepo, UserSecurityContext context, UUID studentId) {
        StudentEntity entity = studentRepo.findById(context, studentId)
                .orElseThrow(() -> new IllegalStateException("student missing: " + studentId));
        entity.setVerificationStatus("VERIFIED");
        studentRepo.save(context, entity);
    }
}
