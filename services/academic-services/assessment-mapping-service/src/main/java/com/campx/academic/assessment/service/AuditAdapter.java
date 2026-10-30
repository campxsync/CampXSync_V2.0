package com.campx.academic.assessment.service;

import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Audit adapter managing immutable historical audit entries in the assessment_history collection (US-034, US-035).
 */
public class AuditAdapter {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(AuditAdapter.class);
    private final List<AssessmentHistory> historyRecords = new CopyOnWriteArrayList<>();

    public void record(String assessmentId, String tenantId, int version, AuditAction action,
                       AssessmentStatus fromStatus, AssessmentStatus toStatus,
                       String changedBy, String reason, String approvalRef,
                       String correlationId, String diffSummary) {
        AssessmentHistory history = new AssessmentHistory();
        history.setAssessmentId(assessmentId);
        history.setTenantId(tenantId);
        history.setVersion(version);
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        history.setApprovalRef(approvalRef);
        history.setCorrelationId(correlationId);
        history.setDiffSummary(diffSummary);

        historyRecords.add(history);
        log.info("Audit logged: assessment=" + assessmentId + ", action=" + action + ", by=" + changedBy + ", corr=" + correlationId);
    }

    public void recordAuthFailure(String assessmentId, String tenantId, String userId, String reason, String correlationId) {
        AssessmentHistory history = new AssessmentHistory();
        history.setAssessmentId(assessmentId != null ? assessmentId : "UNKNOWN");
        history.setTenantId(tenantId != null ? tenantId : "GLOBAL");
        history.setVersion(0);
        history.setAction(AuditAction.AUTH_FAILURE);
        history.setChangedBy(userId != null ? userId : "ANONYMOUS");
        history.setReason(reason);
        history.setCorrelationId(correlationId);
        history.setDiffSummary("Authorization failure encountered: " + reason);

        historyRecords.add(history);
        log.warn("Auth failure audited: " + reason + ", user=" + userId + ", corr=" + correlationId);
    }

    public List<AssessmentHistory> getHistoryByAssessment(String assessmentId) {
        List<AssessmentHistory> result = new ArrayList<>();
        for (AssessmentHistory h : historyRecords) {
            if (h.getAssessmentId().equals(assessmentId)) {
                result.add(h);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<AssessmentHistory> getAll() {
        return Collections.unmodifiableList(historyRecords);
    }
}
