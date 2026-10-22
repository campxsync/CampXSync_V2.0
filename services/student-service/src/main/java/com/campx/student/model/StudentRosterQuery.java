package com.campx.student.model;

import java.util.UUID;

/**
 * Encapsulates filtering, search, and cursor parameters for the student roster query.
 */
public class StudentRosterQuery {

    private int limit = 20;
    private String cursor; // Opaque base64 cursor or last enrollment_no
    private String search; // text search against enrollment_no, display_name, primary_email
    private UUID collegeId;
    private UUID programId;
    private UUID batchId;
    private StudentStatus status;

    public StudentRosterQuery() {}

    public int getLimit() { return limit; }
    public void setLimit(int limit) {
        if (limit <= 0) this.limit = 20;
        else if (limit > 100) this.limit = 100;
        else this.limit = limit;
    }

    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }
}
