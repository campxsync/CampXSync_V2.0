package com.campx.academic.timetable.model;

import java.util.UUID;

/**
 * Ingress request DTO for creating a timetable slot entry.
 */
public class CreateTimetableSlotRequest {

    private UUID timetableVersionId;
    private UUID sectionId;
    private UUID subjectId;
    private UUID employeeId;
    private UUID roomId;
    private int dayOfWeek;
    private String startTime;
    private String endTime;
    private String entryType = "LECTURE";
    private String validFrom;
    private String validTo;

    public CreateTimetableSlotRequest() {}

    public CreateTimetableSlotRequest(UUID timetableVersionId, UUID sectionId, UUID subjectId,
                                     UUID employeeId, UUID roomId, int dayOfWeek,
                                     String startTime, String endTime, String entryType) {
        this.timetableVersionId = timetableVersionId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.employeeId = employeeId;
        this.roomId = roomId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.entryType = entryType != null ? entryType : "LECTURE";
    }

    public UUID getTimetableVersionId() { return timetableVersionId; }
    public void setTimetableVersionId(UUID timetableVersionId) { this.timetableVersionId = timetableVersionId; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getValidFrom() { return validFrom; }
    public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
    public String getValidTo() { return validTo; }
    public void setValidTo(String validTo) { this.validTo = validTo; }
}
