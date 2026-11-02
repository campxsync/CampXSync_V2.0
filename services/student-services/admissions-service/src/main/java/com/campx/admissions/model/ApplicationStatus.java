package com.campx.admissions.model;

/**
 * Authoritative lifecycle state machine for an admission application (STM-02 /
 * ADR D-02).
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    RETURNED_FOR_INFORMATION,
    ELIGIBLE,
    UNDER_REVIEW,
    OFFERED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    REJECTED,
    WITHDRAWN,
    HANDOVER_PENDING,
    HANDED_OVER;

    public boolean isTerminal() {
        return this == DECLINED || this == EXPIRED || this == REJECTED || this == WITHDRAWN || this == HANDED_OVER;
    }
}
