package com.campx.academic.resource.service;

import com.campx.academic.resource.model.ResourceModels.ResourceHistory;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit adapter managing immutable lifecycle and security history (US-031, US-032, US-033).
 */
public class AuditAdapter {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AuditAdapter.class);

    // Map: resourceId -> List<ResourceHistory>
    private final Map<String, List<ResourceHistory>> historyMap = new ConcurrentHashMap<>();

    public ResourceHistory recordHistory(String tenantId, String resourceId, long versionNo,
                                         String action, String fromStatus, String toStatus,
                                         String changedBy, String reason, String approvalRef,
                                         String correlationId) {
        String historyId = "HIST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ResourceHistory entry = new ResourceHistory();
        entry.setId(historyId);
        entry.setTenantId(tenantId);
        entry.setResourceId(resourceId);
        entry.setVersionNo(versionNo);
        entry.setAction(action);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setChangedBy(changedBy);
        entry.setChangedAt(Instant.now().toString());
        entry.setReason(reason);
        entry.setApprovalRef(approvalRef);
        entry.setCorrelationId(correlationId);

        historyMap.computeIfAbsent(resourceId, k -> Collections.synchronizedList(new ArrayList<>())).add(entry);

        logger.info("[AuditAdapter] Recorded audit history entry {} for resource {} action {}", historyId, resourceId, action);
        return entry;
    }

    public List<ResourceHistory> getHistory(String resourceId) {
        List<ResourceHistory> list = historyMap.get(resourceId);
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }
}
