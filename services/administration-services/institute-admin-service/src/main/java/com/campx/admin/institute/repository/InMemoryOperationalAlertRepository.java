package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.OperationalAlert;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback and testing implementation of {@link OperationalAlertRepository}.
 */
public class InMemoryOperationalAlertRepository implements OperationalAlertRepository {

    private final Map<String, OperationalAlert> store = new ConcurrentHashMap<>();

    @Override
    public OperationalAlert createAlert(UserSecurityContext context, OperationalAlert alert) {
        if (alert == null) {
            throw new MalformedPayloadException("OperationalAlert payload cannot be null");
        }
        if (alert.getMessage() == null || alert.getMessage().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'message' is required");
        }

        if (alert.getId() == null || alert.getId().trim().isEmpty()) {
            alert.setId(UUID.randomUUID().toString());
        }
        if (alert.getSeverity() == null || alert.getSeverity().trim().isEmpty()) {
            alert.setSeverity("INFO");
        }
        if (alert.getStatus() == null || alert.getStatus().trim().isEmpty()) {
            alert.setStatus("ACTIVE");
        }
        alert.setCreatedAt(System.currentTimeMillis());

        store.put(alert.getId(), alert);
        return alert;
    }

    @Override
    public Optional<OperationalAlert> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id.toString()));
    }

    @Override
    public List<OperationalAlert> listAlerts(UserSecurityContext context, String status) {
        List<OperationalAlert> list = new ArrayList<>();
        for (OperationalAlert a : store.values()) {
            if (status == null || status.trim().isEmpty() || status.equalsIgnoreCase(a.getStatus())) {
                list.add(a);
            }
        }
        return list;
    }

    @Override
    public OperationalAlert acknowledgeAlert(UserSecurityContext context, UUID id) {
        if (id == null) throw new ResourceNotFoundException("OperationalAlert", "null");
        OperationalAlert alert = store.get(id.toString());
        if (alert == null) throw new ResourceNotFoundException("OperationalAlert", id.toString());
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(System.currentTimeMillis());
        return alert;
    }

    @Override
    public OperationalAlert resolveAlert(UserSecurityContext context, UUID id) {
        if (id == null) throw new ResourceNotFoundException("OperationalAlert", "null");
        OperationalAlert alert = store.get(id.toString());
        if (alert == null) throw new ResourceNotFoundException("OperationalAlert", id.toString());
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(System.currentTimeMillis());
        return alert;
    }
}
