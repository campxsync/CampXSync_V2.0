package com.campx.admissions.model.dto;

import java.util.UUID;

public class AssignReviewerRequest {

    private UUID reviewerId;
    private UUID departmentId;

    public AssignReviewerRequest() {
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }
}
