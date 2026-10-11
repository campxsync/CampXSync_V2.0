package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link DelegationRepository}.
 */
public class InMemoryDelegationRepository implements DelegationRepository {

    private final Map<String, Delegation> delegations = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public Delegation createDelegation(UserSecurityContext context, Delegation delegation) {
        checkContext(context);
        if (delegation == null) {
            throw new MalformedPayloadException("Delegation payload cannot be null");
        }
        if (delegation.getFromUserId() == null || delegation.getFromUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegating user (fromUserId) is required");
        }
        if (delegation.getToUserId() == null || delegation.getToUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("Target user (toUserId) is required");
        }
        if (delegation.getRoleId() == null || delegation.getRoleId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegated role (roleId) is required");
        }
        if (delegation.getValidTo() <= delegation.getValidFrom()) {
            throw new MalformedPayloadException("validTo must be strictly greater than validFrom");
        }

        String id = delegation.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        Delegation copy = new Delegation();
        copy.setId(id);
        copy.setTenantId(context.getTenantId().toString());
        copy.setFromUserId(delegation.getFromUserId().trim());
        copy.setToUserId(delegation.getToUserId().trim());
        copy.setRoleId(delegation.getRoleId().trim());
        copy.setValidFrom(delegation.getValidFrom());
        copy.setValidTo(delegation.getValidTo());
        copy.setReason(delegation.getReason());
        copy.setStatus(delegation.getStatus() != null ? delegation.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        delegations.put(id, copy);
        return copy;
    }

    @Override
    public Optional<Delegation> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        Delegation d = delegations.get(id);
        if (d != null && d.getDeletedAt() == null && context.getTenantId().toString().equals(d.getTenantId())) {
            return Optional.of(d);
        }
        return Optional.empty();
    }

    @Override
    public List<Delegation> listDelegations(UserSecurityContext context) {
        checkContext(context);
        List<Delegation> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        for (Delegation d : delegations.values()) {
            if (d.getDeletedAt() == null && tenantStr.equals(d.getTenantId())) {
                result.add(d);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<Delegation> listDelegationsForUser(UserSecurityContext context, String userId) {
        checkContext(context);
        if (userId == null || userId.trim().isEmpty()) return Collections.emptyList();

        List<Delegation> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        String targetUid = userId.trim();
        for (Delegation d : delegations.values()) {
            if (d.getDeletedAt() == null && tenantStr.equals(d.getTenantId())
                    && (targetUid.equals(d.getFromUserId()) || targetUid.equals(d.getToUserId()))) {
                result.add(d);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Delegation updateDelegation(UserSecurityContext context, Delegation delegation) {
        checkContext(context);
        if (delegation == null || delegation.getId() == null || delegation.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Delegation ID is required for update");
        }

        Delegation existing = delegations.get(delegation.getId());
        if (existing == null || existing.getDeletedAt() != null || !context.getTenantId().toString().equals(existing.getTenantId())) {
            throw new ResourceNotFoundException("Delegation", delegation.getId());
        }

        if (delegation.getValidTo() > 0) {
            if (delegation.getValidTo() <= existing.getValidFrom()) {
                throw new MalformedPayloadException("validTo must be greater than validFrom");
            }
            existing.setValidTo(delegation.getValidTo());
        }
        if (delegation.getReason() != null) {
            existing.setReason(delegation.getReason());
        }
        if (delegation.getStatus() != null && !delegation.getStatus().trim().isEmpty()) {
            existing.setStatus(delegation.getStatus().trim().toUpperCase(Locale.ROOT));
        }

        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void revokeDelegation(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Delegation ID is required for revocation");
        }

        Delegation existing = delegations.get(id);
        if (existing != null && existing.getDeletedAt() == null && context.getTenantId().toString().equals(existing.getTenantId())) {
            existing.setStatus("REVOKED");
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }
}
