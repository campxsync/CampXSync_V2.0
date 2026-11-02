package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryApplicationRepository implements ApplicationRepository {

        // Key: tenantId.toString() + ":" + id.toString()
        private final Map<String, ApplicationEntity> store = new ConcurrentHashMap<>();

        private String key(UUID tenantId, UUID id) {
                return tenantId.toString() + ":" + id.toString();
        }

        @Override
        public ApplicationEntity save(ApplicationEntity app) {
                if (app.getId() == null) {
                        app.setId(UUID.randomUUID());
                }
                store.put(key(app.getTenantId(), app.getId()), app);
                return app;
        }

        @Override
        public Optional<ApplicationEntity> findById(UUID tenantId, UUID id) {
                return Optional.ofNullable(store.get(key(tenantId, id)))
                                .filter(a -> a.getDeletedAt() == null);
        }

        @Override
        public Optional<ApplicationEntity> findByApplicationNumber(UUID tenantId, UUID intakeId, String appNum) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && a.getIntakeId().equals(intakeId)
                                                && a.getApplicationNumber().equalsIgnoreCase(appNum)
                                                && a.getDeletedAt() == null)
                                .findFirst();
        }

        @Override
        public Optional<ApplicationEntity> findActiveByApplicant(UUID tenantId, UUID intakeId, UUID applicantPersonId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && a.getIntakeId().equals(intakeId)
                                                && a.getApplicantPersonId().equals(applicantPersonId)
                                                && a.getDeletedAt() == null
                                                && a.getStatus() != ApplicationStatus.WITHDRAWN
                                                && a.getStatus() != ApplicationStatus.REJECTED)
                                .findFirst();
        }

        @Override
        public List<ApplicationEntity> findByIntake(UUID tenantId, UUID intakeId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && a.getIntakeId().equals(intakeId)
                                                && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ApplicationEntity> findByApplicant(UUID tenantId, UUID applicantPersonId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && a.getApplicantPersonId().equals(applicantPersonId)
                                                && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ApplicationEntity> findByStatus(UUID tenantId, ApplicationStatus status) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && a.getStatus() == status
                                                && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ApplicationEntity> findByReviewer(UUID tenantId, UUID reviewerId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && reviewerId.equals(a.getAssignedReviewerId())
                                                && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ApplicationEntity> findByDepartment(UUID tenantId, UUID departmentId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId)
                                                && departmentId.equals(a.getAssignedDepartmentId())
                                                && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public List<ApplicationEntity> findByTenantId(UUID tenantId) {
                return store.values().stream()
                                .filter(a -> a.getTenantId().equals(tenantId) && a.getDeletedAt() == null)
                                .collect(Collectors.toList());
        }

        @Override
        public boolean existsByApplicationNumber(UUID tenantId, UUID intakeId, String appNum) {
                return findByApplicationNumber(tenantId, intakeId, appNum).isPresent();
        }

        public void clear() {
                store.clear();
        }
}
