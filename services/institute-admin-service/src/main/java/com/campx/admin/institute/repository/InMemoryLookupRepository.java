package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.LookupType;
import com.campx.admin.institute.model.InstituteModels.LookupValue;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe fallback repository for {@link LookupRepository}.
 */
public class InMemoryLookupRepository implements LookupRepository {

    private final Map<String, LookupType> types = new ConcurrentHashMap<>();
    private final Map<String, LookupValue> values = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public LookupType createType(UserSecurityContext context, LookupType type) {
        checkContext(context);
        if (type == null) throw new MalformedPayloadException("LookupType cannot be null");
        if (type.getCode() == null || type.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupType code is required");
        }
        if (type.getName() == null || type.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupType name is required");
        }

        String tenantStr = context.getTenantId().toString();
        String code = type.getCode().trim().toUpperCase(Locale.ROOT);

        for (LookupType t : types.values()) {
            if (t.getDeletedAt() == null && tenantStr.equals(t.getTenantId()) && code.equalsIgnoreCase(t.getCode())) {
                throw new MalformedPayloadException("LookupType code already exists: " + code);
            }
        }

        String id = type.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        LookupType copy = new LookupType();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCode(code);
        copy.setName(type.getName().trim());
        copy.setSystem(type.isSystem());
        copy.setDescription(type.getDescription());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        types.put(id, copy);
        return copy;
    }

    @Override
    public Optional<LookupType> findTypeById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        LookupType t = types.get(id);
        if (t != null && tenantStr.equals(t.getTenantId()) && t.getDeletedAt() == null) {
            return Optional.of(t);
        }
        return Optional.empty();
    }

    @Override
    public Optional<LookupType> findTypeByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        for (LookupType t : types.values()) {
            if (t.getDeletedAt() == null && tenantStr.equals(t.getTenantId()) && code.equalsIgnoreCase(t.getCode())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<LookupType> listTypes(UserSecurityContext context) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return types.values().stream()
                .filter(t -> t.getDeletedAt() == null && tenantStr.equals(t.getTenantId()))
                .sorted(Comparator.comparing(LookupType::getCode))
                .collect(Collectors.toList());
    }

    @Override
    public LookupType updateType(UserSecurityContext context, LookupType type) {
        checkContext(context);
        if (type == null || type.getId() == null) throw new MalformedPayloadException("LookupType ID is required");
        LookupType existing = findTypeById(context, type.getId())
                .orElseThrow(() -> new ResourceNotFoundException("LookupType", type.getId()));

        if (type.getName() != null && !type.getName().trim().isEmpty()) {
            existing.setName(type.getName().trim());
        }
        if (type.getDescription() != null) {
            existing.setDescription(type.getDescription());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteType(UserSecurityContext context, String id) {
        checkContext(context);
        LookupType existing = findTypeById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("LookupType", id));
        long now = System.currentTimeMillis();
        String actor = context.getUserId() != null ? context.getUserId().toString() : null;
        existing.setDeletedAt(now);
        existing.setUpdatedBy(actor);

        // Cascade soft delete values
        for (LookupValue v : values.values()) {
            if (id.equals(v.getLookupTypeId()) && v.getDeletedAt() == null) {
                v.setDeletedAt(now);
                v.setUpdatedBy(actor);
            }
        }
    }

    @Override
    public LookupValue createValue(UserSecurityContext context, LookupValue value) {
        checkContext(context);
        if (value == null) throw new MalformedPayloadException("LookupValue cannot be null");
        if (value.getLookupTypeId() == null || value.getLookupTypeId().trim().isEmpty()) {
            throw new MalformedPayloadException("lookupTypeId is required");
        }
        if (value.getCode() == null || value.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupValue code is required");
        }
        if (value.getLabel() == null || value.getLabel().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupValue label is required");
        }

        String tenantStr = context.getTenantId().toString();
        String typeId = value.getLookupTypeId().trim();
        String code = value.getCode().trim().toUpperCase(Locale.ROOT);

        for (LookupValue v : values.values()) {
            if (v.getDeletedAt() == null && tenantStr.equals(v.getTenantId())
                    && typeId.equals(v.getLookupTypeId())
                    && code.equalsIgnoreCase(v.getCode())) {
                throw new MalformedPayloadException("LookupValue code already exists for type: " + code);
            }
        }

        String id = value.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        LookupValue copy = new LookupValue();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setLookupTypeId(typeId);
        copy.setCode(code);
        copy.setLabel(value.getLabel().trim());
        copy.setSortOrder(value.getSortOrder());
        copy.setActive(value.isActive());
        copy.setAttrs(value.getAttrs() != null ? value.getAttrs() : "{}");
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        values.put(id, copy);
        return copy;
    }

    @Override
    public Optional<LookupValue> findValueById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        LookupValue v = values.get(id);
        if (v != null && tenantStr.equals(v.getTenantId()) && v.getDeletedAt() == null) {
            return Optional.of(v);
        }
        return Optional.empty();
    }

    @Override
    public Optional<LookupValue> findValueByCode(UserSecurityContext context, String lookupTypeId, String code) {
        checkContext(context);
        if (lookupTypeId == null || code == null) return Optional.empty();
        String tenantStr = context.getTenantId().toString();
        for (LookupValue v : values.values()) {
            if (v.getDeletedAt() == null && tenantStr.equals(v.getTenantId())
                    && lookupTypeId.equals(v.getLookupTypeId())
                    && code.equalsIgnoreCase(v.getCode())) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<LookupValue> listValuesByType(UserSecurityContext context, String lookupTypeId) {
        checkContext(context);
        String tenantStr = context.getTenantId().toString();
        return values.values().stream()
                .filter(v -> v.getDeletedAt() == null && tenantStr.equals(v.getTenantId()) && Objects.equals(lookupTypeId, v.getLookupTypeId()))
                .sorted(Comparator.comparingInt(LookupValue::getSortOrder).thenComparing(LookupValue::getCode))
                .collect(Collectors.toList());
    }

    @Override
    public LookupValue updateValue(UserSecurityContext context, LookupValue value) {
        checkContext(context);
        if (value == null || value.getId() == null) throw new MalformedPayloadException("LookupValue ID is required");
        LookupValue existing = findValueById(context, value.getId())
                .orElseThrow(() -> new ResourceNotFoundException("LookupValue", value.getId()));

        if (value.getLabel() != null && !value.getLabel().trim().isEmpty()) {
            existing.setLabel(value.getLabel().trim());
        }
        existing.setSortOrder(value.getSortOrder());
        existing.setActive(value.isActive());
        if (value.getAttrs() != null) {
            existing.setAttrs(value.getAttrs());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteValue(UserSecurityContext context, String id) {
        checkContext(context);
        LookupValue existing = findValueById(context, id)
                .orElseThrow(() -> new ResourceNotFoundException("LookupValue", id));
        existing.setDeletedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
    }
}
