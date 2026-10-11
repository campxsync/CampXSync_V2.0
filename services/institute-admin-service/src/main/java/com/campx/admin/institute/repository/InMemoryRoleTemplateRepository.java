package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link RoleTemplateRepository}.
 */
public class InMemoryRoleTemplateRepository implements RoleTemplateRepository {

    private final Map<String, RoleTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, RoleTemplatePermission> permissions = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            throw new SecurityViolationException("Missing required security context: userId");
        }
    }

    @Override
    public RoleTemplate createRoleTemplate(UserSecurityContext context, RoleTemplate template) {
        checkContext(context);
        if (template == null) {
            throw new MalformedPayloadException("RoleTemplate payload cannot be null");
        }
        if (template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("RoleTemplate code is required");
        }
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("RoleTemplate name is required");
        }

        String code = template.getCode().trim().toUpperCase(Locale.ROOT);
        for (RoleTemplate t : templates.values()) {
            if (t.getDeletedAt() == null && code.equalsIgnoreCase(t.getCode())) {
                throw new MalformedPayloadException("RoleTemplate code already exists: " + code);
            }
        }

        String id = template.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        RoleTemplate copy = new RoleTemplate();
        copy.setId(id);
        copy.setCode(code);
        copy.setName(template.getName().trim());
        copy.setCatalogueId(template.getCatalogueId());
        copy.setKind(template.getKind());
        copy.setScopeNote(template.getScopeNote());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId().toString());
        copy.setRowVersion(1);

        templates.put(id, copy);
        return copy;
    }

    @Override
    public Optional<RoleTemplate> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        RoleTemplate t = templates.get(id);
        if (t != null && t.getDeletedAt() == null) {
            return Optional.of(t);
        }
        return Optional.empty();
    }

    @Override
    public Optional<RoleTemplate> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        String targetCode = code.trim();
        for (RoleTemplate t : templates.values()) {
            if (t.getDeletedAt() == null && targetCode.equalsIgnoreCase(t.getCode())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<RoleTemplate> listRoleTemplates(UserSecurityContext context) {
        checkContext(context);
        List<RoleTemplate> result = new ArrayList<>();
        for (RoleTemplate t : templates.values()) {
            if (t.getDeletedAt() == null) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public RoleTemplate updateRoleTemplate(UserSecurityContext context, RoleTemplate template) {
        checkContext(context);
        if (template == null || template.getId() == null || template.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Template ID is required for update");
        }

        RoleTemplate existing = templates.get(template.getId());
        if (existing == null || existing.getDeletedAt() != null) {
            throw new ResourceNotFoundException("RoleTemplate", template.getId());
        }

        if (template.getName() != null && !template.getName().trim().isEmpty()) {
            existing.setName(template.getName().trim());
        }
        if (template.getCatalogueId() != null) {
            existing.setCatalogueId(template.getCatalogueId());
        }
        if (template.getKind() != null) {
            existing.setKind(template.getKind());
        }
        if (template.getScopeNote() != null) {
            existing.setScopeNote(template.getScopeNote());
        }

        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId().toString());
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteRoleTemplate(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Template ID is required for deletion");
        }

        RoleTemplate existing = templates.get(id);
        if (existing != null && existing.getDeletedAt() == null) {
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId().toString());
        }
    }

    @Override
    public RoleTemplatePermission addPermission(UserSecurityContext context, String roleCode, String permissionCode) {
        checkContext(context);
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new MalformedPayloadException("roleCode is required");
        }
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            throw new MalformedPayloadException("permissionCode is required");
        }

        String rc = roleCode.trim().toUpperCase(Locale.ROOT);
        String pc = permissionCode.trim();

        for (RoleTemplatePermission p : permissions.values()) {
            if (p.getDeletedAt() == null && rc.equals(p.getRoleCode()) && pc.equals(p.getPermissionCode())) {
                return p;
            }
        }

        String id = UUID.randomUUID().toString();
        RoleTemplatePermission perm = new RoleTemplatePermission();
        perm.setId(id);
        perm.setRoleCode(rc);
        perm.setPermissionCode(pc);
        perm.setCreatedAt(System.currentTimeMillis());
        perm.setUpdatedAt(perm.getCreatedAt());
        perm.setCreatedBy(context.getUserId().toString());
        perm.setRowVersion(1);

        permissions.put(id, perm);
        return perm;
    }

    @Override
    public List<RoleTemplatePermission> listPermissions(UserSecurityContext context, String roleCode) {
        checkContext(context);
        if (roleCode == null || roleCode.trim().isEmpty()) return Collections.emptyList();

        String rc = roleCode.trim().toUpperCase(Locale.ROOT);
        List<RoleTemplatePermission> list = new ArrayList<>();
        for (RoleTemplatePermission p : permissions.values()) {
            if (p.getDeletedAt() == null && rc.equals(p.getRoleCode())) {
                list.add(p);
            }
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public void removePermission(UserSecurityContext context, String roleCode, String permissionCode) {
        checkContext(context);
        if (roleCode == null || permissionCode == null) return;

        String rc = roleCode.trim().toUpperCase(Locale.ROOT);
        String pc = permissionCode.trim();

        for (RoleTemplatePermission p : permissions.values()) {
            if (p.getDeletedAt() == null && rc.equals(p.getRoleCode()) && pc.equals(p.getPermissionCode())) {
                p.setDeletedAt(System.currentTimeMillis());
                p.setUpdatedAt(System.currentTimeMillis());
                p.setUpdatedBy(context.getUserId().toString());
            }
        }
    }
}
