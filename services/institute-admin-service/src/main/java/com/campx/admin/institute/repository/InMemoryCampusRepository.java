package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Campus;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link CampusRepository}.
 */
public class InMemoryCampusRepository implements CampusRepository {

    private final Map<String, Campus> campuses = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public Campus createCampus(UserSecurityContext context, Campus campus) {
        checkContext(context);
        if (campus == null) {
            throw new MalformedPayloadException("Campus payload cannot be null");
        }
        if (campus.getCollegeId() == null || campus.getCollegeId().trim().isEmpty()) {
            throw new MalformedPayloadException("College ID is required for campus");
        }
        if (campus.getCode() == null || campus.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus code is required");
        }
        if (campus.getName() == null || campus.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus name is required");
        }

        String tenantStr = context.getTenantId().toString();
        String collegeId = campus.getCollegeId().trim();
        String code = campus.getCode().trim().toUpperCase(Locale.ROOT);

        for (Campus c : campuses.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId())
                    && collegeId.equals(c.getCollegeId()) && code.equalsIgnoreCase(c.getCode())) {
                throw new MalformedPayloadException("Campus code already exists in college: " + code);
            }
        }

        String id = campus.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        Campus copy = new Campus();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCollegeId(collegeId);
        copy.setCode(code);
        copy.setName(campus.getName().trim());
        copy.setAddressId(campus.getAddressId());
        copy.setPrimary(campus.isPrimary());
        copy.setStatus(campus.getStatus() != null ? campus.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        campuses.put(id, copy);
        return copy;
    }

    @Override
    public Optional<Campus> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        Campus c = campuses.get(id);
        if (c != null && c.getDeletedAt() == null && context.getTenantId().toString().equals(c.getTenantId())) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Campus> findByCode(UserSecurityContext context, String collegeId, String code) {
        checkContext(context);
        if (collegeId == null || code == null) return Optional.empty();

        String tenantStr = context.getTenantId().toString();
        String cid = collegeId.trim();
        String targetCode = code.trim();

        for (Campus c : campuses.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId())
                    && cid.equals(c.getCollegeId()) && targetCode.equalsIgnoreCase(c.getCode())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Campus> listCampuses(UserSecurityContext context, String collegeId) {
        checkContext(context);
        List<Campus> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        String cid = collegeId != null ? collegeId.trim() : null;

        for (Campus c : campuses.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId())) {
                if (cid == null || cid.equals(c.getCollegeId())) {
                    result.add(c);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Campus updateCampus(UserSecurityContext context, Campus campus) {
        checkContext(context);
        if (campus == null || campus.getId() == null || campus.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Campus ID is required for update");
        }

        Campus existing = campuses.get(campus.getId());
        if (existing == null || existing.getDeletedAt() != null || !context.getTenantId().toString().equals(existing.getTenantId())) {
            throw new ResourceNotFoundException("Campus", campus.getId());
        }

        if (campus.getName() != null && !campus.getName().trim().isEmpty()) {
            existing.setName(campus.getName().trim());
        }
        if (campus.getStatus() != null && !campus.getStatus().trim().isEmpty()) {
            existing.setStatus(campus.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        existing.setPrimary(campus.isPrimary());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteCampus(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Campus ID is required for deletion");
        }

        Campus existing = campuses.get(id);
        if (existing != null && existing.getDeletedAt() == null && context.getTenantId().toString().equals(existing.getTenantId())) {
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }
}
