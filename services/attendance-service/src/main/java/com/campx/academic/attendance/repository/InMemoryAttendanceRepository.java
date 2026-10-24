package com.campx.academic.attendance.repository;

import com.campx.academic.attendance.exception.AttendanceNotFoundException;
import com.campx.academic.attendance.exception.DuplicateAttendanceRecordException;
import com.campx.academic.attendance.exception.DuplicateSessionException;
import com.campx.academic.attendance.model.AttendanceCorrectionEntity;
import com.campx.academic.attendance.model.AttendanceRecordEntity;
import com.campx.academic.attendance.model.AttendanceSessionEntity;
import com.campx.academic.attendance.security.UserSecurityContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe implementation of {@link AttendanceRepository} for isolated unit testing.
 */
public class InMemoryAttendanceRepository implements AttendanceRepository {

    private final Map<UUID, AttendanceSessionEntity> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, AttendanceRecordEntity> records = new ConcurrentHashMap<>();
    private final Map<UUID, AttendanceCorrectionEntity> corrections = new ConcurrentHashMap<>();

    // tenantId:sectionId:sessionDate:startTime -> sessionId
    private final Map<String, UUID> sessionKeyIndex = new ConcurrentHashMap<>();

    // tenantId:sessionId:studentId -> recordId
    private final Map<String, UUID> recordKeyIndex = new ConcurrentHashMap<>();

    @Override
    public AttendanceSessionEntity createSession(UserSecurityContext ctx, AttendanceSessionEntity session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID());
        }
        session.setTenantId(ctx.getTenantId());
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());

        String key = String.format("%s:%s:%s:%s",
                ctx.getTenantId(),
                session.getSectionId(),
                session.getSessionDate(),
                session.getStartTime() != null ? session.getStartTime().toString() : "00:00:00");

        if (sessionKeyIndex.putIfAbsent(key, session.getId()) != null) {
            throw new DuplicateSessionException("Session already exists for section " + session.getSectionId()
                    + " on date " + session.getSessionDate());
        }

        sessions.put(session.getId(), session);
        return session;
    }

    @Override
    public Optional<AttendanceSessionEntity> findSessionById(UserSecurityContext ctx, UUID sessionId) {
        AttendanceSessionEntity s = sessions.get(sessionId);
        if (s != null && s.getTenantId().equals(ctx.getTenantId())) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    @Override
    public Optional<AttendanceSessionEntity> findSessionByTimetableEntryAndDate(UserSecurityContext ctx, UUID timetableEntryId, LocalDate sessionDate) {
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(ctx.getTenantId()))
                .filter(s -> timetableEntryId.equals(s.getTimetableEntryId()))
                .filter(s -> sessionDate.equals(s.getSessionDate()))
                .findFirst();
    }

    @Override
    public List<AttendanceSessionEntity> findSessionsBySectionAndDate(UserSecurityContext ctx, UUID sectionId, LocalDate sessionDate) {
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(ctx.getTenantId()))
                .filter(s -> sectionId.equals(s.getSectionId()))
                .filter(s -> sessionDate.equals(s.getSessionDate()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AttendanceSessionEntity> listSessions(UserSecurityContext ctx, UUID sectionId, LocalDate fromDate, LocalDate toDate, int limit, int offset) {
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(ctx.getTenantId()))
                .filter(s -> sectionId == null || sectionId.equals(s.getSectionId()))
                .filter(s -> fromDate == null || !s.getSessionDate().isBefore(fromDate))
                .filter(s -> toDate == null || !s.getSessionDate().isAfter(toDate))
                .sorted(Comparator.comparing(AttendanceSessionEntity::getSessionDate).reversed())
                .skip(offset)
                .limit(limit > 0 ? limit : 50)
                .collect(Collectors.toList());
    }

    @Override
    public List<AttendanceRecordEntity> markAttendance(UserSecurityContext ctx, UUID sessionId, List<AttendanceRecordEntity> items) {
        AttendanceSessionEntity session = sessions.get(sessionId);
        if (session == null || !session.getTenantId().equals(ctx.getTenantId())) {
            throw new AttendanceNotFoundException("Attendance session not found: " + sessionId);
        }

        List<AttendanceRecordEntity> saved = new ArrayList<>();
        for (AttendanceRecordEntity r : items) {
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            r.setTenantId(ctx.getTenantId());
            r.setSessionId(sessionId);
            r.setCreatedAt(OffsetDateTime.now());
            r.setUpdatedAt(OffsetDateTime.now());
            if (r.getMarkedAt() == null) {
                r.setMarkedAt(OffsetDateTime.now());
            }

            String recKey = String.format("%s:%s:%s", ctx.getTenantId(), sessionId, r.getStudentId());
            if (recordKeyIndex.putIfAbsent(recKey, r.getId()) != null) {
                throw new DuplicateAttendanceRecordException("Duplicate attendance record for student " + r.getStudentId()
                        + " in session " + sessionId);
            }
            records.put(r.getId(), r);
            saved.add(r);
        }
        return saved;
    }

    @Override
    public List<AttendanceRecordEntity> findRecordsBySessionId(UserSecurityContext ctx, UUID sessionId) {
        return records.values().stream()
                .filter(r -> r.getTenantId().equals(ctx.getTenantId()))
                .filter(r -> sessionId.equals(r.getSessionId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AttendanceRecordEntity> findRecordById(UserSecurityContext ctx, UUID recordId) {
        AttendanceRecordEntity r = records.get(recordId);
        if (r != null && r.getTenantId().equals(ctx.getTenantId())) {
            return Optional.of(r);
        }
        return Optional.empty();
    }

    @Override
    public AttendanceCorrectionEntity requestCorrection(UserSecurityContext ctx, AttendanceCorrectionEntity correction) {
        if (correction.getId() == null) {
            correction.setId(UUID.randomUUID());
        }
        correction.setTenantId(ctx.getTenantId());
        correction.setStatus("PENDING");
        correction.setCreatedAt(OffsetDateTime.now());
        correction.setUpdatedAt(OffsetDateTime.now());

        if (correction.getRecordId() != null) {
            AttendanceRecordEntity r = records.get(correction.getRecordId());
            if (r != null) {
                correction.setOldStatus(r.getStatus());
            }
        }

        corrections.put(correction.getId(), correction);
        return correction;
    }

    @Override
    public AttendanceCorrectionEntity applyCorrection(UserSecurityContext ctx, UUID correctionId, String decision, UUID approverId) {
        AttendanceCorrectionEntity c = corrections.get(correctionId);
        if (c == null || !c.getTenantId().equals(ctx.getTenantId())) {
            throw new AttendanceNotFoundException("Attendance correction not found: " + correctionId);
        }

        if (!"PENDING".equalsIgnoreCase(c.getStatus())) {
            throw new IllegalStateException("Correction " + correctionId + " has already been decided: " + c.getStatus());
        }

        // Self-approval prevention (Wave 7 invariant: requester cannot approve their own request)
        if (approverId != null && approverId.equals(c.getRequestedBy())) {
            throw new com.campx.academic.attendance.exception.AttendanceCorrectionForbiddenException(
                    "Self-approval prohibited: requester cannot approve their own correction request");
        }

        c.setStatus(decision);
        c.setApprovedBy(approverId != null ? approverId : ctx.getUserId());
        c.setDecidedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());

        if ("APPROVED".equalsIgnoreCase(decision)) {
            AttendanceRecordEntity r = records.get(c.getRecordId());
            if (r != null && r.getTenantId().equals(ctx.getTenantId())) {
                r.setStatus(c.getNewStatus());
                r.setRemarks(c.getReason());
                r.setRowVersion(r.getRowVersion() + 1);
                r.setUpdatedAt(OffsetDateTime.now());
            }
        }
        return c;
    }

    @Override
    public Optional<AttendanceCorrectionEntity> findCorrectionById(UserSecurityContext ctx, UUID correctionId) {
        AttendanceCorrectionEntity c = corrections.get(correctionId);
        if (c != null && c.getTenantId().equals(ctx.getTenantId())) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public List<AttendanceCorrectionEntity> findCorrectionsBySessionId(UserSecurityContext ctx, UUID sessionId) {
        List<UUID> sessionRecordIds = records.values().stream()
                .filter(r -> r.getTenantId().equals(ctx.getTenantId()))
                .filter(r -> sessionId.equals(r.getSessionId()))
                .map(AttendanceRecordEntity::getId)
                .collect(Collectors.toList());

        return corrections.values().stream()
                .filter(c -> c.getTenantId().equals(ctx.getTenantId()))
                .filter(c -> sessionRecordIds.contains(c.getRecordId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AttendanceCorrectionEntity> findCorrectionsByRecordId(UserSecurityContext ctx, UUID recordId) {
        return corrections.values().stream()
                .filter(c -> c.getTenantId().equals(ctx.getTenantId()))
                .filter(c -> recordId.equals(c.getRecordId()))
                .collect(Collectors.toList());
    }
}
