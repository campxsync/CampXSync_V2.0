package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.DataClassification;
import com.campx.admin.institute.model.InstituteModels.DataRetentionPolicy;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link DataGovernanceRepository}.
 */
public class InMemoryDataGovernanceRepository implements DataGovernanceRepository {

    private final Map<String, DataRetentionPolicy> retentionPolicies = new ConcurrentHashMap<>();
    private final Map<String, DataClassification> classifications = new ConcurrentHashMap<>();

    private static final Set<String> VALID_ACTIONS = new HashSet<>(Arrays.asList("ARCHIVE", "DELETE", "ANONYMIZE"));
    private static final Set<String> VALID_SENSITIVITIES = new HashSet<>(Arrays.asList("LOW", "MEDIUM", "HIGH", "RESTRICTED"));

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public DataRetentionPolicy createRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy) {
        checkContext(context);
        if (policy == null) throw new MalformedPayloadException("DataRetentionPolicy cannot be null");
        if (policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new MalformedPayloadException("policyCode is required");
        }
        if (policy.getRetentionDays() <= 0) {
            throw new MalformedPayloadException("retentionDays must be > 0");
        }
        String action = policy.getAction() != null ? policy.getAction().trim().toUpperCase(Locale.ROOT) : "ARCHIVE";
        if (!VALID_ACTIONS.contains(action)) {
            throw new MalformedPayloadException("Invalid action: " + action);
        }

        String code = policy.getPolicyCode().trim().toUpperCase(Locale.ROOT);
        for (DataRetentionPolicy p : retentionPolicies.values()) {
            if (p.getDeletedAt() == null && code.equalsIgnoreCase(p.getPolicyCode())) {
                throw new MalformedPayloadException("Policy code already exists: " + code);
            }
        }

        String id = policy.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        DataRetentionPolicy copy = new DataRetentionPolicy();
        copy.setId(id);
        copy.setPolicyCode(code);
        copy.setEntityType(policy.getEntityType() != null ? policy.getEntityType().trim() : "STUDENT_RECORD");
        copy.setRetentionDays(policy.getRetentionDays());
        copy.setAction(action);
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        retentionPolicies.put(id, copy);
        return copy;
    }

    @Override
    public Optional<DataRetentionPolicy> findRetentionPolicyById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        DataRetentionPolicy p = retentionPolicies.get(id);
        if (p != null && p.getDeletedAt() == null) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    @Override
    public Optional<DataRetentionPolicy> findRetentionPolicyByCode(UserSecurityContext context, String policyCode) {
        checkContext(context);
        if (policyCode == null || policyCode.trim().isEmpty()) return Optional.empty();
        for (DataRetentionPolicy p : retentionPolicies.values()) {
            if (p.getDeletedAt() == null && policyCode.equalsIgnoreCase(p.getPolicyCode())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DataRetentionPolicy> listRetentionPolicies(UserSecurityContext context) {
        checkContext(context);
        return retentionPolicies.values().stream()
                .filter(p -> p.getDeletedAt() == null)
                .sorted(Comparator.comparing(DataRetentionPolicy::getPolicyCode))
                .collect(Collectors.toList());
    }

    @Override
    public DataRetentionPolicy updateRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy) {
        checkContext(context);
        if (policy == null || policy.getId() == null) throw new MalformedPayloadException("Policy ID is required");
        DataRetentionPolicy existing = findRetentionPolicyById(context, policy.getId())
                .orElseThrow(() -> new ResourceNotFoundException("DataRetentionPolicy", policy.getId()));

        if (policy.getEntityType() != null) existing.setEntityType(policy.getEntityType());
        if (policy.getRetentionDays() > 0) existing.setRetentionDays(policy.getRetentionDays());
        if (policy.getAction() != null) {
            String act = policy.getAction().trim().toUpperCase(Locale.ROOT);
            if (!VALID_ACTIONS.contains(act)) throw new MalformedPayloadException("Invalid action: " + act);
            existing.setAction(act);
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteRetentionPolicy(UserSecurityContext context, String id) {
        checkContext(context);
        DataRetentionPolicy existing = findRetentionPolicyById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("DataRetentionPolicy", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }

    @Override
    public DataClassification createClassification(UserSecurityContext context, DataClassification classification) {
        checkContext(context);
        if (classification == null) throw new MalformedPayloadException("DataClassification cannot be null");
        if (classification.getClassificationCode() == null || classification.getClassificationCode().trim().isEmpty()) {
            throw new MalformedPayloadException("classificationCode is required");
        }
        String sens = classification.getSensitivityLevel() != null
                ? classification.getSensitivityLevel().trim().toUpperCase(Locale.ROOT) : "LOW";
        if (!VALID_SENSITIVITIES.contains(sens)) {
            throw new MalformedPayloadException("Invalid sensitivityLevel: " + sens);
        }

        String code = classification.getClassificationCode().trim().toUpperCase(Locale.ROOT);
        for (DataClassification c : classifications.values()) {
            if (c.getDeletedAt() == null && code.equalsIgnoreCase(c.getClassificationCode())) {
                throw new MalformedPayloadException("Classification code already exists: " + code);
            }
        }

        String id = classification.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        DataClassification copy = new DataClassification();
        copy.setId(id);
        copy.setClassificationCode(code);
        copy.setSensitivityLevel(sens);
        copy.setEncryptionRequired(classification.isEncryptionRequired());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        classifications.put(id, copy);
        return copy;
    }

    @Override
    public Optional<DataClassification> findClassificationById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        DataClassification c = classifications.get(id);
        if (c != null && c.getDeletedAt() == null) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public Optional<DataClassification> findClassificationByCode(UserSecurityContext context, String classificationCode) {
        checkContext(context);
        if (classificationCode == null || classificationCode.trim().isEmpty()) return Optional.empty();
        for (DataClassification c : classifications.values()) {
            if (c.getDeletedAt() == null && classificationCode.equalsIgnoreCase(c.getClassificationCode())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DataClassification> listClassifications(UserSecurityContext context) {
        checkContext(context);
        return classifications.values().stream()
                .filter(c -> c.getDeletedAt() == null)
                .sorted(Comparator.comparing(DataClassification::getClassificationCode))
                .collect(Collectors.toList());
    }

    @Override
    public DataClassification updateClassification(UserSecurityContext context, DataClassification classification) {
        checkContext(context);
        if (classification == null || classification.getId() == null) throw new MalformedPayloadException("Classification ID is required");
        DataClassification existing = findClassificationById(context, classification.getId())
                .orElseThrow(() -> new ResourceNotFoundException("DataClassification", classification.getId()));

        if (classification.getSensitivityLevel() != null) {
            String sens = classification.getSensitivityLevel().trim().toUpperCase(Locale.ROOT);
            if (!VALID_SENSITIVITIES.contains(sens)) throw new MalformedPayloadException("Invalid sensitivityLevel: " + sens);
            existing.setSensitivityLevel(sens);
        }
        existing.setEncryptionRequired(classification.isEncryptionRequired());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteClassification(UserSecurityContext context, String id) {
        checkContext(context);
        DataClassification existing = findClassificationById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("DataClassification", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }
}
