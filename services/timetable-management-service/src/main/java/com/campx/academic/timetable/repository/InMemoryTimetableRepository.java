package com.campx.academic.timetable.repository;

import com.campx.academic.timetable.exception.TimetableConflictException;
import com.campx.academic.timetable.model.*;
import com.campx.academic.timetable.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository implementation for isolated unit testing of the Timetable domain.
 */
public class InMemoryTimetableRepository implements TimetableRepository {

    private final Map<UUID, TimetableEntity> timetables = new ConcurrentHashMap<>();
    private final Map<UUID, TimetableVersionEntity> versions = new ConcurrentHashMap<>();
    private final Map<UUID, TimetableEntryEntity> entries = new ConcurrentHashMap<>();
    private final Set<String> sectionFacultyAssignments = Collections.synchronizedSet(new HashSet<>());

    @Override
    public TimetableEntity saveTimetable(UserSecurityContext context, TimetableEntity timetable) {
        if (timetable.getId() == null) {
            timetable.setId(UUID.randomUUID());
        }
        timetable.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (timetable.getCreatedAt() == null) timetable.setCreatedAt(now);
        timetable.setUpdatedAt(now);
        timetables.put(timetable.getId(), timetable);
        return timetable;
    }

    @Override
    public Optional<TimetableEntity> findTimetableById(UserSecurityContext context, UUID timetableId) {
        TimetableEntity t = timetables.get(timetableId);
        if (t != null && t.getTenantId().equals(context.getTenantId()) && t.getDeletedAt() == null) {
            return Optional.of(t);
        }
        return Optional.empty();
    }

    @Override
    public TimetableVersionEntity saveVersion(UserSecurityContext context, TimetableVersionEntity version) {
        if (version.getId() == null) {
            version.setId(UUID.randomUUID());
        }
        version.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (version.getCreatedAt() == null) version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versions.put(version.getId(), version);
        return version;
    }

    @Override
    public Optional<TimetableVersionEntity> findVersionById(UserSecurityContext context, UUID versionId) {
        TimetableVersionEntity v = versions.get(versionId);
        if (v != null && v.getTenantId().equals(context.getTenantId()) && v.getDeletedAt() == null) {
            return Optional.of(v);
        }
        return Optional.empty();
    }

    @Override
    public Optional<TimetableVersionEntity> findActiveVersionByTimetableId(UserSecurityContext context, UUID timetableId) {
        return versions.values().stream()
                .filter(v -> v.getTimetableId().equals(timetableId) && v.getTenantId().equals(context.getTenantId()) && v.getDeletedAt() == null)
                .max(Comparator.comparingInt(TimetableVersionEntity::getVersionNo));
    }

    @Override
    public void ensureSectionFacultyAssignment(UserSecurityContext context, UUID sectionId, UUID employeeId) {
        if (sectionId != null && employeeId != null) {
            sectionFacultyAssignments.add(context.getTenantId() + ":" + sectionId + ":" + employeeId);
        }
    }

    @Override
    public TimetableEntryEntity saveEntry(UserSecurityContext context, TimetableEntryEntity entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID());
        }
        entry.setTenantId(context.getTenantId());

        // In-memory conflict check mimicking PostgreSQL exclusion constraints
        for (TimetableEntryEntity existing : entries.values()) {
            if (existing.getId().equals(entry.getId())) continue;
            if (!existing.getTenantId().equals(context.getTenantId())) continue;
            if (!existing.getTimetableVersionId().equals(entry.getTimetableVersionId())) continue;
            if (existing.getDayOfWeek() != entry.getDayOfWeek()) continue;
            if (existing.getDeletedAt() != null) continue;
            if ("BREAK".equalsIgnoreCase(entry.getEntryType()) || "BREAK".equalsIgnoreCase(existing.getEntryType())) continue;

            // Check time overlap: (start1 < end2) && (start2 < end1)
            boolean overlap = entry.getStartTime().isBefore(existing.getEndTime()) &&
                    existing.getStartTime().isBefore(entry.getEndTime());

            if (overlap) {
                if (entry.getEmployeeId() != null && entry.getEmployeeId().equals(existing.getEmployeeId())) {
                    throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                            "Faculty schedule conflict: assigned faculty is already scheduled for another slot during this time period.", 409, null);
                }
                if (entry.getSectionId() != null && entry.getSectionId().equals(existing.getSectionId())) {
                    throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                            "Batch/Section schedule conflict: section is already scheduled for another class during this time period.", 409, null);
                }
                if (entry.getRoomId() != null && entry.getRoomId().equals(existing.getRoomId())) {
                    throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                            "Room conflict: room is already booked for another class during this time period.", 409, null);
                }
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (entry.getCreatedAt() == null) entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entries.put(entry.getId(), entry);
        return entry;
    }

    @Override
    public Optional<TimetableEntryEntity> findEntryById(UserSecurityContext context, UUID entryId) {
        TimetableEntryEntity e = entries.get(entryId);
        if (e != null && e.getTenantId().equals(context.getTenantId()) && e.getDeletedAt() == null) {
            return Optional.of(e);
        }
        return Optional.empty();
    }

    @Override
    public List<TimetableEntryEntity> findEntriesByVersionId(UserSecurityContext context, UUID versionId) {
        return entries.values().stream()
                .filter(e -> e.getTimetableVersionId().equals(versionId) && e.getTenantId().equals(context.getTenantId()) && e.getDeletedAt() == null)
                .sorted(Comparator.comparingInt(TimetableEntryEntity::getDayOfWeek)
                        .thenComparing(TimetableEntryEntity::getStartTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<TimetableSlotResponse> findSlotResponsesByVersionId(UserSecurityContext context, UUID versionId) {
        return findEntriesByVersionId(context, versionId).stream().map(e -> {
            TimetableSlotResponse r = new TimetableSlotResponse();
            r.setId(e.getId());
            r.setTenantId(e.getTenantId());
            r.setTimetableVersionId(e.getTimetableVersionId());
            r.setSectionId(e.getSectionId());
            r.setSubjectId(e.getSubjectId());
            r.setEmployeeId(e.getEmployeeId());
            r.setRoomId(e.getRoomId());
            r.setDayOfWeek(e.getDayOfWeek());
            r.setStartTime(e.getStartTime() != null ? e.getStartTime().toString() : null);
            r.setEndTime(e.getEndTime() != null ? e.getEndTime().toString() : null);
            r.setEntryType(e.getEntryType());
            r.setCreatedAt(e.getCreatedAt());
            return r;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean deleteEntry(UserSecurityContext context, UUID entryId) {
        TimetableEntryEntity e = entries.get(entryId);
        if (e != null && e.getTenantId().equals(context.getTenantId())) {
            e.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return true;
        }
        return false;
    }

    public void clear() {
        timetables.clear();
        versions.clear();
        entries.clear();
        sectionFacultyAssignments.clear();
    }
}
