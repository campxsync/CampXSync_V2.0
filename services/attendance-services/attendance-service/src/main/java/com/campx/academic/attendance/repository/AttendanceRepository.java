package com.campx.academic.attendance.repository;

import com.campx.academic.attendance.model.AttendanceCorrectionEntity;
import com.campx.academic.attendance.model.AttendanceRecordEntity;
import com.campx.academic.attendance.model.AttendanceSessionEntity;
import com.campx.academic.attendance.security.UserSecurityContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for ACD-06 Attendance Management Service.
 */
public interface AttendanceRepository {

    AttendanceSessionEntity createSession(UserSecurityContext ctx, AttendanceSessionEntity session);

    Optional<AttendanceSessionEntity> findSessionById(UserSecurityContext ctx, UUID sessionId);

    Optional<AttendanceSessionEntity> findSessionByTimetableEntryAndDate(UserSecurityContext ctx, UUID timetableEntryId, LocalDate sessionDate);

    List<AttendanceSessionEntity> findSessionsBySectionAndDate(UserSecurityContext ctx, UUID sectionId, LocalDate sessionDate);

    List<AttendanceSessionEntity> listSessions(UserSecurityContext ctx, UUID sectionId, LocalDate fromDate, LocalDate toDate, int limit, int offset);

    List<AttendanceRecordEntity> markAttendance(UserSecurityContext ctx, UUID sessionId, List<AttendanceRecordEntity> records);

    List<AttendanceRecordEntity> findRecordsBySessionId(UserSecurityContext ctx, UUID sessionId);

    Optional<AttendanceRecordEntity> findRecordById(UserSecurityContext ctx, UUID recordId);

    AttendanceCorrectionEntity requestCorrection(UserSecurityContext ctx, AttendanceCorrectionEntity correction);

    AttendanceCorrectionEntity applyCorrection(UserSecurityContext ctx, UUID correctionId, String decision, UUID approverId);

    Optional<AttendanceCorrectionEntity> findCorrectionById(UserSecurityContext ctx, UUID correctionId);

    List<AttendanceCorrectionEntity> findCorrectionsBySessionId(UserSecurityContext ctx, UUID sessionId);

    List<AttendanceCorrectionEntity> findCorrectionsByRecordId(UserSecurityContext ctx, UUID recordId);
}
