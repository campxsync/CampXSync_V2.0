package com.campx.academic.attendance.model;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for single or bulk attendance marking.
 */
public class MarkAttendanceRequest {

    public static class StudentAttendanceItem {
        private UUID studentId;
        private String status = "PRESENT"; // 'PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED'
        private String method = "MANUAL";
        private String remarks;

        public StudentAttendanceItem() {}

        public StudentAttendanceItem(UUID studentId, String status, String method, String remarks) {
            this.studentId = studentId;
            this.status = status;
            this.method = method;
            this.remarks = remarks;
        }

        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
    }

    private UUID sessionId;
    private List<StudentAttendanceItem> entries;

    public MarkAttendanceRequest() {}

    public MarkAttendanceRequest(UUID sessionId, List<StudentAttendanceItem> entries) {
        this.sessionId = sessionId;
        this.entries = entries;
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public List<StudentAttendanceItem> getEntries() { return entries; }
    public void setEntries(List<StudentAttendanceItem> entries) { this.entries = entries; }
}
