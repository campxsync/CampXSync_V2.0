package com.campx.student.repository;

import com.campx.student.model.CursorPagedResult;
import com.campx.student.model.PersonEntity;
import com.campx.student.model.StudentEntity;
import com.campx.student.model.StudentRosterItem;
import com.campx.student.model.StudentRosterQuery;
import com.campx.student.security.UserSecurityContext;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link StudentRepository} for isolated unit and mock testing.
 * Strictly enforces tenant isolation boundaries and provides deterministic cursor pagination.
 */
public class InMemoryStudentRepository implements StudentRepository {

    // Key: tenantId + ":" + studentId
    private final Map<String, StudentEntity> store = new ConcurrentHashMap<>();
    private final PersonRepository personRepository;

    public InMemoryStudentRepository(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public StudentEntity save(UserSecurityContext context, StudentEntity student) {
        if (student == null) throw new IllegalArgumentException("Student cannot be null");
        UUID tenantId = context.getTenantId();
        student.setTenantId(tenantId);

        if (student.getId() == null) {
            student.setId(UUID.randomUUID());
            student.setCreatedAt(OffsetDateTime.now());
            student.setCreatedBy(context.getUserId());
        }
        student.setUpdatedAt(OffsetDateTime.now());
        student.setUpdatedBy(context.getUserId());
        student.setRowVersion(student.getRowVersion() + 1);

        store.put(key(tenantId, student.getId()), student);
        return student;
    }

    @Override
    public Optional<StudentEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        StudentEntity entity = store.get(key(context.getTenantId(), id));
        if (entity != null && entity.getDeletedAt() == null) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    public Optional<StudentEntity> findByEnrollmentNo(UserSecurityContext context, String enrollmentNo) {
        if (enrollmentNo == null || enrollmentNo.trim().isEmpty()) return Optional.empty();
        UUID tenantId = context.getTenantId();
        String normalized = enrollmentNo.trim().toLowerCase();

        return store.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId())
                        && s.getDeletedAt() == null
                        && s.getEnrollmentNo() != null
                        && s.getEnrollmentNo().trim().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public boolean existsByEnrollmentNo(UserSecurityContext context, String enrollmentNo) {
        return findByEnrollmentNo(context, enrollmentNo).isPresent();
    }

    @Override
    public CursorPagedResult<StudentRosterItem> findRoster(UserSecurityContext context, StudentRosterQuery query) {
        UUID tenantId = context.getTenantId();
        int limit = query != null ? query.getLimit() : 20;

        List<StudentEntity> matching = store.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId()) && s.getDeletedAt() == null)
                .filter(s -> {
                    if (query == null) return true;
                    if (query.getCollegeId() != null && !query.getCollegeId().equals(s.getCollegeId())) {
                        return false;
                    }
                    if (query.getProgramId() != null && !query.getProgramId().equals(s.getProgramId())) {
                        return false;
                    }
                    if (query.getStatus() != null && query.getStatus() != s.getStatus()) {
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(StudentEntity::getEnrollmentNo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        // Apply cursor (enrollment_no > cursor)
        String cursorVal = query != null ? query.getCursor() : null;
        String decodedCursor = decodeCursor(cursorVal);

        List<StudentRosterItem> rosterItems = new ArrayList<>();
        for (StudentEntity s : matching) {
            if (decodedCursor != null && s.getEnrollmentNo().compareToIgnoreCase(decodedCursor) <= 0) {
                continue;
            }

            // Fetch linked person for search matching and display details
            PersonEntity person = null;
            if (personRepository != null && s.getPersonId() != null) {
                person = personRepository.findById(context, s.getPersonId()).orElse(null);
            }

            if (query != null && query.getSearch() != null && !query.getSearch().trim().isEmpty()) {
                String search = query.getSearch().trim().toLowerCase();
                boolean matchesEnrollment = s.getEnrollmentNo() != null && s.getEnrollmentNo().toLowerCase().contains(search);
                boolean matchesName = person != null && person.getDisplayName() != null && person.getDisplayName().toLowerCase().contains(search);
                boolean matchesEmail = person != null && person.getPrimaryEmail() != null && person.getPrimaryEmail().toLowerCase().contains(search);

                if (!matchesEnrollment && !matchesName && !matchesEmail) {
                    continue;
                }
            }

            StudentRosterItem item = new StudentRosterItem();
            item.setId(s.getId());
            item.setEnrollmentNo(s.getEnrollmentNo());
            item.setAdmissionNo(s.getAdmissionNo());
            item.setStatus(s.getStatus());
            item.setCollegeId(s.getCollegeId());
            item.setProgramId(s.getProgramId());
            item.setCreatedAt(s.getCreatedAt());

            if (person != null) {
                item.setDisplayName(person.getDisplayName());
                item.setFirstName(person.getFirstName());
                item.setLastName(person.getLastName());
                item.setPrimaryEmail(person.getPrimaryEmail());
            }

            rosterItems.add(item);
        }

        long totalCount = rosterItems.size();
        boolean hasMore = rosterItems.size() > limit;
        List<StudentRosterItem> pagedItems = hasMore ? rosterItems.subList(0, limit) : rosterItems;

        String nextCursor = null;
        if (hasMore && !pagedItems.isEmpty()) {
            StudentRosterItem lastItem = pagedItems.get(pagedItems.size() - 1);
            nextCursor = encodeCursor(lastItem.getEnrollmentNo());
        }

        return new CursorPagedResult<>(pagedItems, nextCursor, hasMore, totalCount);
    }

    @Override
    public void delete(UserSecurityContext context, UUID id) {
        if (id == null) return;
        StudentEntity entity = store.get(key(context.getTenantId(), id));
        if (entity != null) {
            entity.setDeletedAt(OffsetDateTime.now());
            entity.setUpdatedBy(context.getUserId());
        }
    }

    private String encodeCursor(String val) {
        if (val == null) return null;
        return Base64.getUrlEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return cursor.trim();
        }
    }
}
