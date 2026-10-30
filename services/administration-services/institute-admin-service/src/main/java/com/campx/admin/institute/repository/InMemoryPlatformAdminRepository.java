package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.PlatformAdmin;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory fallback implementation of {@link PlatformAdminRepository}.
 */
public class InMemoryPlatformAdminRepository implements PlatformAdminRepository {

    private final Map<String, PlatformAdmin> storage = new ConcurrentHashMap<>();

    public InMemoryPlatformAdminRepository() {
        // Pre-seed default Super Admin ba77b8f7-ab32-46bd-85d2-5aade3526880
        PlatformAdmin defaultAdmin = new PlatformAdmin();
        defaultAdmin.setId("ec688db1-9476-4ea9-808f-fafc03e7d93b");
        defaultAdmin.setUserId("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        defaultAdmin.setFullName("CampXSync Platform Administrator");
        defaultAdmin.setRoleCode("SUPER_ADMIN");
        defaultAdmin.setStatus("ACTIVE");
        defaultAdmin.setCreatedAt(System.currentTimeMillis());
        defaultAdmin.setUpdatedAt(defaultAdmin.getCreatedAt());
        defaultAdmin.setRowVersion(1);
        storage.put(defaultAdmin.getId(), defaultAdmin);
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            throw new SecurityViolationException("Missing required security context: userId");
        }
    }

    @Override
    public PlatformAdmin createPlatformAdmin(UserSecurityContext context, PlatformAdmin admin) {
        checkContext(context);
        if (admin == null || admin.getUserId() == null || admin.getUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("User ID is mandatory for PlatformAdmin creation");
        }
        if (admin.getRoleCode() == null || admin.getRoleCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Role code is mandatory for PlatformAdmin creation");
        }

        String id = admin.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        PlatformAdmin copy = new PlatformAdmin();
        copy.setId(id);
        copy.setUserId(admin.getUserId().trim());
        copy.setFullName(admin.getFullName());
        copy.setRoleCode(admin.getRoleCode().trim().toUpperCase(Locale.ROOT));
        copy.setStatus(admin.getStatus() != null ? admin.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
        copy.setCreatedBy(context.getUserId().toString());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setRowVersion(1);

        storage.put(id, copy);
        return copy;
    }

    @Override
    public Optional<PlatformAdmin> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        PlatformAdmin a = storage.get(id);
        if (a != null && a.getDeletedAt() == null) {
            return Optional.of(a);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PlatformAdmin> findByUserId(UserSecurityContext context, String userId) {
        checkContext(context);
        if (userId == null || userId.trim().isEmpty()) return Optional.empty();
        for (PlatformAdmin a : storage.values()) {
            if (userId.trim().equalsIgnoreCase(a.getUserId()) && a.getDeletedAt() == null) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PlatformAdmin> listPlatformAdmins(UserSecurityContext context) {
        checkContext(context);
        List<PlatformAdmin> result = new ArrayList<>();
        for (PlatformAdmin a : storage.values()) {
            if (a.getDeletedAt() == null) {
                result.add(a);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public PlatformAdmin updatePlatformAdmin(UserSecurityContext context, PlatformAdmin admin) {
        checkContext(context);
        if (admin == null || admin.getId() == null || admin.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("PlatformAdmin ID is required for update");
        }
        PlatformAdmin existing = storage.get(admin.getId());
        if (existing == null || existing.getDeletedAt() != null) {
            throw new ResourceNotFoundException("PlatformAdmin", admin.getId());
        }

        if (admin.getFullName() != null) {
            existing.setFullName(admin.getFullName());
        }
        if (admin.getRoleCode() != null && !admin.getRoleCode().trim().isEmpty()) {
            existing.setRoleCode(admin.getRoleCode().trim().toUpperCase(Locale.ROOT));
        }
        if (admin.getStatus() != null && !admin.getStatus().trim().isEmpty()) {
            existing.setStatus(admin.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        existing.setUpdatedBy(context.getUserId().toString());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deletePlatformAdmin(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("PlatformAdmin ID is required for deletion");
        }
        PlatformAdmin existing = storage.get(id);
        if (existing != null && existing.getDeletedAt() == null) {
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId().toString());
        }
    }
}
