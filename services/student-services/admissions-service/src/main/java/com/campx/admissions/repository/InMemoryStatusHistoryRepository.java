package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationStatusHistoryEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryStatusHistoryRepository implements StatusHistoryRepository {

    private final List<ApplicationStatusHistoryEntity> historyList = Collections.synchronizedList(new ArrayList<>());

    @Override
    public ApplicationStatusHistoryEntity save(ApplicationStatusHistoryEntity history) {
        if (history.getId() == null) {
            history.setId(UUID.randomUUID());
        }
        historyList.add(history);
        return history;
    }

    @Override
    public List<ApplicationStatusHistoryEntity> findByApplication(UUID tenantId, UUID applicationId) {
        synchronized (historyList) {
            return historyList.stream()
                    .filter(h -> h.getTenantId().equals(tenantId) && h.getApplicationId().equals(applicationId))
                    .collect(Collectors.toList());
        }
    }

    public void clear() {
        historyList.clear();
    }
}
