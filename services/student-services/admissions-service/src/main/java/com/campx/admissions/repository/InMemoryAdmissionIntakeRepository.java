package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionIntakeEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAdmissionIntakeRepository implements AdmissionIntakeRepository {

    private final Map<String, AdmissionIntakeEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public AdmissionIntakeEntity save(AdmissionIntakeEntity intake) {
        if (intake.getId() == null) {
            intake.setId(UUID.randomUUID());
        }
        store.put(key(intake.getTenantId(), intake.getId()), intake);
        return intake;
    }

    @Override
    public Optional<AdmissionIntakeEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)))
                .filter(i -> i.getDeletedAt() == null);
    }

    @Override
    public Optional<AdmissionIntakeEntity> findByCode(UUID tenantId, UUID academicYearId, String code) {
        return store.values().stream()
                .filter(i -> i.getTenantId().equals(tenantId)
                        && i.getAcademicYearId().equals(academicYearId)
                        && i.getIntakeCode().equalsIgnoreCase(code)
                        && i.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public List<AdmissionIntakeEntity> findActiveIntakes(UUID tenantId) {
        return store.values().stream()
                .filter(i -> i.getTenantId().equals(tenantId)
                        && i.isActive()
                        && i.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<AdmissionIntakeEntity> findByProgram(UUID tenantId, UUID programId) {
        return store.values().stream()
                .filter(i -> i.getTenantId().equals(tenantId)
                        && i.getProgramId().equals(programId)
                        && i.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<AdmissionIntakeEntity> findByTenantId(UUID tenantId) {
        return store.values().stream()
                .filter(i -> i.getTenantId().equals(tenantId) && i.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCode(UUID tenantId, UUID academicYearId, String code) {
        return findByCode(tenantId, academicYearId, code).isPresent();
    }

    public void clear() {
        store.clear();
    }
}
