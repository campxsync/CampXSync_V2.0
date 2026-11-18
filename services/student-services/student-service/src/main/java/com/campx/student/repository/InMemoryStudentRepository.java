package com.campx.student.repository;

import com.campx.student.model.CursorPagedResult;
import com.campx.student.model.PersonEntity;
import com.campx.student.model.PortalPreferencesDto;
import com.campx.student.model.ProvisioningRequestRecord;
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
    /** Key: tenantId + ":" + requestId */
    private final Map<String, ProvisioningRequestRecord> provisioningById = new ConcurrentHashMap<>();
    /** Key: tenantId + ":" + collegeId + ":" + studentId */
    private final Map<String, PortalPreferencesDto> portalPreferencesByKey = new ConcurrentHashMap<>();
    private final PersonRepository personRepository;

    public InMemoryStudentRepository() {
        this(new InMemoryPersonRepository());
    }

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
    public Optional<StudentEntity> findByPersonId(UserSecurityContext context, UUID personId) {
        if (personId == null) return Optional.empty();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId())
                        && personId.equals(s.getPersonId())
                        && s.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public Optional<StudentEntity> findByIamUserId(UserSecurityContext context, UUID iamUserId) {
        if (iamUserId == null) return Optional.empty();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId())
                        && iamUserId.equals(s.getIamUserId())
                        && s.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public long countByTenant(UserSecurityContext context) {
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId()) && s.getDeletedAt() == null)
                .count();
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

    @Override
    public UUID saveProvisioningRequest(UserSecurityContext context, UUID studentId, UUID requestedIamUserId) {
        if (studentId == null) {
            throw new IllegalArgumentException("studentId cannot be null");
        }
        UUID requestId = UUID.randomUUID();
        ProvisioningRequestRecord row = new ProvisioningRequestRecord();
        row.setId(requestId);
        row.setStudentId(studentId);
        row.setRequestedIamUserId(requestedIamUserId);
        row.setRequestStatus("REQUESTED");
        row.setRequestedAt(OffsetDateTime.now());
        provisioningById.put(key(context.getTenantId(), requestId), row);
        return requestId;
    }

    @Override
    public long countProvisioningRequests(UserSecurityContext context, UUID studentId) {
        return listProvisioningRequests(context, studentId).size();
    }

    @Override
    public Optional<ProvisioningRequestRecord> findOpenProvisioningRequest(UserSecurityContext context, UUID studentId) {
        for (ProvisioningRequestRecord row : listProvisioningRequests(context, studentId)) {
            if ("REQUESTED".equalsIgnoreCase(row.getRequestStatus())) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ProvisioningRequestRecord> findProvisioningRequestById(UserSecurityContext context, UUID requestId) {
        if (requestId == null || context == null || context.getTenantId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(provisioningById.get(key(context.getTenantId(), requestId)));
    }

    @Override
    public List<ProvisioningRequestRecord> listProvisioningRequests(UserSecurityContext context, UUID studentId) {
        if (studentId == null || context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }
        List<ProvisioningRequestRecord> list = new ArrayList<ProvisioningRequestRecord>();
        for (Map.Entry<String, ProvisioningRequestRecord> e : provisioningById.entrySet()) {
            if (!e.getKey().startsWith(context.getTenantId().toString() + ":")) {
                continue;
            }
            ProvisioningRequestRecord row = e.getValue();
            if (studentId.equals(row.getStudentId())) {
                list.add(row);
            }
        }
        list.sort(new Comparator<ProvisioningRequestRecord>() {
            @Override
            public int compare(ProvisioningRequestRecord a, ProvisioningRequestRecord b) {
                OffsetDateTime at = a.getRequestedAt();
                OffsetDateTime bt = b.getRequestedAt();
                if (at == null && bt == null) return 0;
                if (at == null) return 1;
                if (bt == null) return -1;
                return bt.compareTo(at);
            }
        });
        return list;
    }

    @Override
    public void updateProvisioningRequestStatus(UserSecurityContext context, UUID requestId, String status,
                                                String failureReason) {
        Optional<ProvisioningRequestRecord> opt = findProvisioningRequestById(context, requestId);
        if (!opt.isPresent()) {
            return;
        }
        ProvisioningRequestRecord row = opt.get();
        row.setRequestStatus(status);
        row.setFailureReason(failureReason);
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            row.setCompletedAt(OffsetDateTime.now());
        }
        if ("REQUESTED".equalsIgnoreCase(status)) {
            row.setCompletedAt(null);
            row.setFailureReason(null);
            row.setRequestedAt(OffsetDateTime.now());
        }
    }

    @Override
    public void revokeActiveIamLinks(UserSecurityContext context, UUID studentId) {
        if (studentId == null) {
            return;
        }
        StudentEntity entity = store.get(key(context.getTenantId(), studentId));
        if (entity != null) {
            entity.setIamUserId(null);
            entity.setUpdatedBy(context.getUserId());
        }
    }

    private String portalPrefKey(UUID tenantId, UUID collegeId, UUID studentId) {
        return tenantId + ":" + collegeId + ":" + studentId;
    }

    @Override
    public Optional<PortalPreferencesDto> findPortalPreferences(UserSecurityContext context, UUID studentId, UUID collegeId) {
        if (context == null || context.getTenantId() == null || studentId == null || collegeId == null) {
            return Optional.empty();
        }
        PortalPreferencesDto stored = portalPreferencesByKey.get(portalPrefKey(context.getTenantId(), collegeId, studentId));
        return Optional.ofNullable(copyPortalPreferences(stored));
    }

    @Override
    public PortalPreferencesDto savePortalPreferences(UserSecurityContext context, PortalPreferencesDto preferences) {
        if (context == null || context.getTenantId() == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        if (preferences == null || preferences.getStudentId() == null || preferences.getCollegeId() == null) {
            throw new IllegalArgumentException("studentId and collegeId are required");
        }
        PortalPreferencesDto copy = copyPortalPreferences(preferences);
        portalPreferencesByKey.put(
                portalPrefKey(context.getTenantId(), copy.getCollegeId(), copy.getStudentId()),
                copy);
        return copyPortalPreferences(copy);
    }

    private PortalPreferencesDto copyPortalPreferences(PortalPreferencesDto src) {
        if (src == null) {
            return null;
        }
        PortalPreferencesDto copy = new PortalPreferencesDto();
        copy.setStudentId(src.getStudentId());
        copy.setCollegeId(src.getCollegeId());
        copy.setVersion(src.getVersion());
        if (src.getWidgets() != null) {
            PortalPreferencesDto.DashboardWidgetsConfig w = new PortalPreferencesDto.DashboardWidgetsConfig();
            w.setOrder(src.getWidgets().getOrder() != null ? new ArrayList<>(src.getWidgets().getOrder()) : new ArrayList<>());
            w.setHidden(src.getWidgets().getHidden() != null ? new ArrayList<>(src.getWidgets().getHidden()) : new ArrayList<>());
            copy.setWidgets(w);
        }
        if (src.getShortcuts() != null) {
            List<PortalPreferencesDto.ShortcutItem> shortcuts = new ArrayList<>();
            for (PortalPreferencesDto.ShortcutItem item : src.getShortcuts()) {
                shortcuts.add(new PortalPreferencesDto.ShortcutItem(item.getId(), item.getLabel(), item.getPath()));
            }
            copy.setShortcuts(shortcuts);
        }
        if (src.getDisplay() != null) {
            PortalPreferencesDto.DisplayPreferences d = new PortalPreferencesDto.DisplayPreferences();
            d.setTheme(src.getDisplay().getTheme());
            d.setLocale(src.getDisplay().getLocale());
            d.setTimezone(src.getDisplay().getTimezone());
            d.setTableDensity(src.getDisplay().getTableDensity());
            if (src.getDisplay().getAccessibility() != null) {
                d.setAccessibility(new HashMap<>(src.getDisplay().getAccessibility()));
            }
            copy.setDisplay(d);
        }
        return copy;
    }

    @Override
    public Optional<StudentEntity> findStudentForAccountLink(UserSecurityContext context, UUID studentId) {
        if (context == null || studentId == null || context.getTenantId() == null) {
            return Optional.empty();
        }
        StudentEntity found = store.get(key(context.getTenantId(), studentId));
        if (found == null || found.getDeletedAt() != null) {
            return Optional.empty();
        }
        if (context.getCollegeId() != null && found.getCollegeId() != null
                && !context.getCollegeId().equals(found.getCollegeId())) {
            return Optional.empty();
        }
        StudentEntity minimal = new StudentEntity();
        minimal.setId(found.getId());
        minimal.setTenantId(found.getTenantId());
        minimal.setCollegeId(found.getCollegeId());
        minimal.setPersonId(found.getPersonId());
        minimal.setStatus(found.getStatus());
        minimal.setIamUserId(found.getIamUserId());
        minimal.setVerificationStatus(found.getVerificationStatus());
        return Optional.of(minimal);
    }

    @Override
    public void linkIamAccount(UserSecurityContext context, UUID studentId, UUID iamUserId) {
        if (studentId == null || context == null || context.getTenantId() == null) {
            return;
        }
        StudentEntity found = store.get(key(context.getTenantId(), studentId));
        if (found != null) {
            found.setIamUserId(iamUserId);
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
