package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.NumberSequence;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link NumberSequenceRepository}.
 */
public class InMemoryNumberSequenceRepository implements NumberSequenceRepository {

    private final Map<String, NumberSequence> sequences = new ConcurrentHashMap<>();

    private static final Set<String> VALID_RESET_POLICIES = new HashSet<>(Arrays.asList(
            "NEVER", "YEARLY", "MONTHLY", "ACADEMIC_YEAR"));

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public synchronized NumberSequence createSequence(UserSecurityContext context, NumberSequence seq) {
        checkContext(context);
        if (seq == null) throw new MalformedPayloadException("NumberSequence payload cannot be null");
        if (seq.getScopeKey() == null || seq.getScopeKey().trim().isEmpty()) {
            throw new MalformedPayloadException("scopeKey is required");
        }
        if (seq.getNextValue() < 0) {
            throw new MalformedPayloadException("nextValue cannot be negative");
        }
        String policy = seq.getResetPolicy() != null ? seq.getResetPolicy().trim().toUpperCase(Locale.ROOT) : "NEVER";
        if (!VALID_RESET_POLICIES.contains(policy)) {
            throw new MalformedPayloadException("Invalid resetPolicy: " + policy);
        }

        String tenantStr = context.getTenantId().toString();
        String scope = seq.getScopeKey().trim().toUpperCase(Locale.ROOT);
        String college = (seq.getCollegeId() != null && !seq.getCollegeId().trim().isEmpty())
                ? seq.getCollegeId().trim() : null;

        for (NumberSequence s : sequences.values()) {
            if (s.getDeletedAt() == null && tenantStr.equals(s.getTenantId())
                    && scope.equalsIgnoreCase(s.getScopeKey())
                    && Objects.equals(college, s.getCollegeId())) {
                throw new MalformedPayloadException("Sequence already exists for scope: " + scope);
            }
        }

        String id = seq.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        NumberSequence copy = new NumberSequence();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setScopeKey(scope);
        copy.setPrefix(seq.getPrefix());
        copy.setSuffix(seq.getSuffix());
        copy.setNextValue(seq.getNextValue());
        copy.setPadding(seq.getPadding() > 0 ? seq.getPadding() : (short) 6);
        copy.setResetPolicy(policy);
        copy.setLastResetOn(seq.getLastResetOn());
        copy.setRequiredPermission(seq.getRequiredPermission());
        copy.setCollegeId(college);
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        sequences.put(id, copy);
        return copy;
    }

    @Override
    public Optional<NumberSequence> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        NumberSequence s = sequences.get(id);
        if (s != null && tenantStr.equals(s.getTenantId()) && s.getDeletedAt() == null) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    @Override
    public Optional<NumberSequence> findByScope(UserSecurityContext context, String scopeKey, String collegeId) {
        checkContext(context);
        if (scopeKey == null || scopeKey.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        String scope = scopeKey.trim().toUpperCase(Locale.ROOT);
        String college = (collegeId != null && !collegeId.trim().isEmpty()) ? collegeId.trim() : null;

        for (NumberSequence s : sequences.values()) {
            if (s.getDeletedAt() == null && tenantStr.equals(s.getTenantId())
                    && scope.equalsIgnoreCase(s.getScopeKey())
                    && Objects.equals(college, s.getCollegeId())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<NumberSequence> listSequences(UserSecurityContext context) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return sequences.values().stream()
                .filter(s -> s.getDeletedAt() == null && tenantStr.equals(s.getTenantId()))
                .sorted(Comparator.comparing(NumberSequence::getScopeKey))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized NumberSequence updateSequence(UserSecurityContext context, NumberSequence seq) {
        checkContext(context);
        if (seq == null || seq.getId() == null) {
            throw new MalformedPayloadException("Sequence ID is required for update");
        }
        NumberSequence existing = findById(context, seq.getId())
                .orElseThrow(() -> new ResourceNotFoundException("NumberSequence", seq.getId()));

        if (seq.getPrefix() != null) existing.setPrefix(seq.getPrefix());
        if (seq.getSuffix() != null) existing.setSuffix(seq.getSuffix());
        if (seq.getNextValue() >= 0) existing.setNextValue(seq.getNextValue());
        if (seq.getPadding() > 0) existing.setPadding(seq.getPadding());
        if (seq.getResetPolicy() != null) {
            String policy = seq.getResetPolicy().trim().toUpperCase(Locale.ROOT);
            if (!VALID_RESET_POLICIES.contains(policy)) throw new MalformedPayloadException("Invalid resetPolicy: " + policy);
            existing.setResetPolicy(policy);
        }
        if (seq.getRequiredPermission() != null) existing.setRequiredPermission(seq.getRequiredPermission());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public synchronized void deleteSequence(UserSecurityContext context, String id) {
        checkContext(context);
        NumberSequence existing = findById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("NumberSequence", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }

    @Override
    public synchronized String generateNextNumber(UserSecurityContext context, String scopeKey, String collegeId) {
        checkContext(context);
        NumberSequence seq = findByScope(context, scopeKey, collegeId)
                .orElseThrow(() -> new ResourceNotFoundException("NumberSequence", scopeKey));

        long current = seq.getNextValue();
        seq.setNextValue(current + 1);
        seq.setUpdatedAt(System.currentTimeMillis());
        seq.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        seq.setRowVersion(seq.getRowVersion() + 1);

        String prefix = seq.getPrefix() != null ? seq.getPrefix() : "";
        String suffix = seq.getSuffix() != null ? seq.getSuffix() : "";
        short pad = seq.getPadding() > 0 ? seq.getPadding() : 6;
        String paddedNum = String.format("%0" + pad + "d", current);

        return prefix + paddedNum + suffix;
    }
}
