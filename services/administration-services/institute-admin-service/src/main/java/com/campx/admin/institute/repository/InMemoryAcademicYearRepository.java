package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AcademicYear;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link AcademicYearRepository}.
 */
public class InMemoryAcademicYearRepository implements AcademicYearRepository {

    private final Map<String, AcademicYear> years = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public AcademicYear createAcademicYear(UserSecurityContext context, AcademicYear year) {
        checkContext(context);
        if (year == null) {
            throw new MalformedPayloadException("AcademicYear payload cannot be null");
        }
        if (year.getCode() == null || year.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear code is required");
        }
        if (year.getEndDate() <= year.getStartDate()) {
            throw new MalformedPayloadException("endDate must be strictly greater than startDate");
        }

        String tenantStr = context.getTenantId().toString();
        String code = year.getCode().trim().toUpperCase(Locale.ROOT);

        for (AcademicYear y : years.values()) {
            if (y.getDeletedAt() == null && tenantStr.equals(y.getTenantId()) && code.equalsIgnoreCase(y.getCode())) {
                throw new MalformedPayloadException("AcademicYear code already exists: " + code);
            }
        }

        if (year.isCurrent()) {
            for (AcademicYear y : years.values()) {
                if (tenantStr.equals(y.getTenantId()) && y.isCurrent()) {
                    y.setCurrent(false);
                }
            }
        }

        String id = year.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        AcademicYear copy = new AcademicYear();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCode(code);
        copy.setStartDate(year.getStartDate());
        copy.setEndDate(year.getEndDate());
        copy.setCurrent(year.isCurrent());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        years.put(id, copy);
        return copy;
    }

    @Override
    public Optional<AcademicYear> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        AcademicYear y = years.get(id);
        if (y != null && y.getDeletedAt() == null && context.getTenantId().toString().equals(y.getTenantId())) {
            return Optional.of(y);
        }
        return Optional.empty();
    }

    @Override
    public Optional<AcademicYear> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        String tenantStr = context.getTenantId().toString();
        String targetCode = code.trim();

        for (AcademicYear y : years.values()) {
            if (y.getDeletedAt() == null && tenantStr.equals(y.getTenantId()) && targetCode.equalsIgnoreCase(y.getCode())) {
                return Optional.of(y);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<AcademicYear> findCurrent(UserSecurityContext context) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        for (AcademicYear y : years.values()) {
            if (y.getDeletedAt() == null && tenantStr.equals(y.getTenantId()) && y.isCurrent()) {
                return Optional.of(y);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<AcademicYear> listAcademicYears(UserSecurityContext context) {
        checkContext(context);
        List<AcademicYear> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        for (AcademicYear y : years.values()) {
            if (y.getDeletedAt() == null && tenantStr.equals(y.getTenantId())) {
                result.add(y);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public AcademicYear updateAcademicYear(UserSecurityContext context, AcademicYear year) {
        checkContext(context);
        if (year == null || year.getId() == null || year.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required for update");
        }

        AcademicYear existing = years.get(year.getId());
        if (existing == null || existing.getDeletedAt() != null || !context.getTenantId().toString().equals(existing.getTenantId())) {
            throw new ResourceNotFoundException("AcademicYear", year.getId());
        }

        if (year.getCode() != null && !year.getCode().trim().isEmpty()) {
            existing.setCode(year.getCode().trim().toUpperCase(Locale.ROOT));
        }
        if (year.getStartDate() > 0) {
            existing.setStartDate(year.getStartDate());
        }
        if (year.getEndDate() > 0) {
            existing.setEndDate(year.getEndDate());
        }
        if (existing.getEndDate() <= existing.getStartDate()) {
            throw new MalformedPayloadException("endDate must be strictly greater than startDate");
        }

        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void setCurrent(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required");
        }

        String tenantStr = context.getTenantId().toString();
        AcademicYear target = years.get(id);
        if (target == null || target.getDeletedAt() != null || !tenantStr.equals(target.getTenantId())) {
            throw new ResourceNotFoundException("AcademicYear", id);
        }

        for (AcademicYear y : years.values()) {
            if (tenantStr.equals(y.getTenantId())) {
                y.setCurrent(y.getId().equals(id));
            }
        }
    }

    @Override
    public void deleteAcademicYear(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("AcademicYear ID is required for deletion");
        }

        AcademicYear existing = years.get(id);
        if (existing != null && existing.getDeletedAt() == null && context.getTenantId().toString().equals(existing.getTenantId())) {
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }
}
