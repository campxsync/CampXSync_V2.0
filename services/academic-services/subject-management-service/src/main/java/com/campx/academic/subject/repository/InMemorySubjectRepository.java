package com.campx.academic.subject.repository;

import com.campx.academic.subject.exception.DuplicateSubjectCodeException;
import com.campx.academic.subject.exception.SubjectNotFoundException;
import com.campx.academic.subject.model.SubjectModels.Subject;
import com.campx.academic.subject.model.SubjectModels.SubjectStatus;
import com.campx.academic.subject.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory fallback implementation of {@link SubjectRepository} for unit tests.
 */
public class InMemorySubjectRepository implements SubjectRepository {

    private final Map<String, Subject> store = new ConcurrentHashMap<>();

    @Override
    public Subject createSubject(UserSecurityContext context, Subject subject) {
        if (subject.getSubjectCode() == null || subject.getSubjectCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject code is required");
        }
        String code = subject.getSubjectCode().trim().toUpperCase(Locale.ROOT);
        for (Subject existing : store.values()) {
            if (existing.getSubjectCode().equalsIgnoreCase(code)) {
                if (context == null || context.getTenantId() == null
                        || Objects.equals(existing.getTenantId(), context.getTenantId().toString())) {
                    throw new DuplicateSubjectCodeException(code, subject.getInstitutionId() != null ? subject.getInstitutionId() : "default");
                }
            }
        }
        if (subject.getId() == null || subject.getId().trim().isEmpty()) {
            subject.setId(UUID.randomUUID().toString());
        }
        if (subject.getStatus() == null) {
            subject.setStatus(SubjectStatus.DRAFT);
        }
        if (context != null && context.getTenantId() != null) {
            subject.setTenantId(context.getTenantId().toString());
        }
        long now = System.currentTimeMillis();
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);
        store.put(subject.getId(), subject.copy());
        return subject.copy();
    }

    @Override
    public Optional<Subject> findById(UserSecurityContext context, String id) {
        Subject s = store.get(id);
        if (s != null && context != null && context.getTenantId() != null
                && s.getTenantId() != null && !context.getTenantId().toString().equals(s.getTenantId())) {
            return Optional.empty();
        }
        return s == null ? Optional.empty() : Optional.of(s.copy());
    }

    @Override
    public Optional<Subject> findByCode(UserSecurityContext context, String code) {
        for (Subject s : store.values()) {
            if (s.getSubjectCode().equalsIgnoreCase(code)) {
                if (context != null && context.getTenantId() != null
                        && s.getTenantId() != null && !context.getTenantId().toString().equals(s.getTenantId())) {
                    continue;
                }
                return Optional.of(s.copy());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Subject> listSubjects(UserSecurityContext context, String departmentId, String status,
                                      String codeContains, String nameContains, int offset, int limit) {
        return store.values().stream()
                .filter(s -> context == null || context.getTenantId() == null || context.getTenantId().toString().equals(s.getTenantId()))
                .filter(s -> departmentId == null || departmentId.isEmpty() || departmentId.equalsIgnoreCase(s.getDepartmentId()))
                .filter(s -> status == null || status.isEmpty() || (s.getStatus() != null && s.getStatus().name().equalsIgnoreCase(status)))
                .filter(s -> codeContains == null || codeContains.isEmpty() || s.getSubjectCode().toLowerCase().contains(codeContains.toLowerCase()))
                .filter(s -> nameContains == null || nameContains.isEmpty() || s.getName().toLowerCase().contains(nameContains.toLowerCase()))
                .skip(Math.max(offset, 0))
                .limit(limit > 0 ? limit : 50)
                .map(Subject::copy)
                .collect(Collectors.toList());
    }

    @Override
    public void updateSubject(UserSecurityContext context, Subject subject) {
        if (subject == null || subject.getId() == null || !store.containsKey(subject.getId())) {
            throw new SubjectNotFoundException("Subject not found: " + (subject != null ? subject.getId() : null));
        }
        subject.setUpdatedAt(System.currentTimeMillis());
        store.put(subject.getId(), subject.copy());
    }

    @Override
    public void updateStatus(UserSecurityContext context, String subjectId, String status) {
        Subject existing = store.get(subjectId);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found: " + subjectId);
        }
        existing.setStatus(SubjectStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        existing.setUpdatedAt(System.currentTimeMillis());
        store.put(subjectId, existing);
    }

    @Override
    public void deleteSubject(UserSecurityContext context, String id) {
        store.remove(id);
    }
}
