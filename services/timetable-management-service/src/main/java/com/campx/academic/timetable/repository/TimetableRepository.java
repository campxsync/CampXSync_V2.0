package com.campx.academic.timetable.repository;

import com.campx.academic.timetable.model.TimetableEntity;
import com.campx.academic.timetable.model.TimetableEntryEntity;
import com.campx.academic.timetable.model.TimetableSlotResponse;
import com.campx.academic.timetable.model.TimetableVersionEntity;
import com.campx.academic.timetable.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface defining transactional persistence operations for the Timetable domain aggregate.
 */
public interface TimetableRepository {

    TimetableEntity saveTimetable(UserSecurityContext context, TimetableEntity timetable);

    Optional<TimetableEntity> findTimetableById(UserSecurityContext context, UUID timetableId);

    TimetableVersionEntity saveVersion(UserSecurityContext context, TimetableVersionEntity version);

    Optional<TimetableVersionEntity> findVersionById(UserSecurityContext context, UUID versionId);

    Optional<TimetableVersionEntity> findActiveVersionByTimetableId(UserSecurityContext context, UUID timetableId);

    TimetableEntryEntity saveEntry(UserSecurityContext context, TimetableEntryEntity entry);

    Optional<TimetableEntryEntity> findEntryById(UserSecurityContext context, UUID entryId);

    List<TimetableSlotResponse> findSlotResponsesByVersionId(UserSecurityContext context, UUID versionId);

    List<TimetableEntryEntity> findEntriesByVersionId(UserSecurityContext context, UUID versionId);

    void ensureSectionFacultyAssignment(UserSecurityContext context, UUID sectionId, UUID employeeId);

    boolean deleteEntry(UserSecurityContext context, UUID entryId);
}
