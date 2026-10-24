package com.campx.academic.attendance.model;

import java.util.UUID;

/**
 * Request payload for creating an attendance session.
 */
public class CreateAttendanceSessionRequest {

    private UUID sectionId;
    private UUID timetableEntryId;
    private UUID employeeId;
    private String sessionDate; // YYYY-MM-DD
    private String startTime;   // HH:MM:SS or HH:MM
    private String endTime;     // HH:MM:SS or HH:MM

    public CreateAttendanceSessionRequest() {}

    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }

    public UUID getTimetableEntryId() { return timetableEntryId; }
    public void setTimetableEntryId(UUID timetableEntryId) { this.timetableEntryId = timetableEntryId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
