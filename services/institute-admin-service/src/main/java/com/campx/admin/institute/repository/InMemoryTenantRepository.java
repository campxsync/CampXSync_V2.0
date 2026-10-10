package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.Institute;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link TenantRepository} for testing and dependency injection.
 * Emulates PostgreSQL {@code core.tenants} constraints, RLS tenant isolation, platform admin checks,
 * and optimistic locking behavior without requiring a live database.
 */
public class InMemoryTenantRepository implements TenantRepository {

    private final Map<String, Institute> institutes = new ConcurrentHashMap<>();
    private final Set<UUID> platformAdmins = Collections.synchronizedSet(new HashSet<>());

    public InMemoryTenantRepository() {
        // Empty by default: all authenticated callers are accepted unless explicit platform admins are configured
    }

    public InMemoryTenantRepository(Collection<UUID> initialPlatformAdmins) {
        if (initialPlatformAdmins != null) {
            platformAdmins.addAll(initialPlatformAdmins);
        }
    }

    /**
     * Registers a user ID as a recognized platform administrator.
     *
     * @param userId platform administrator user UUID
     */
    public void addPlatformAdmin(UUID userId) {
        if (userId != null) {
            platformAdmins.add(userId);
        }
    }

    private void verifyPlatformAdmin(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        // If specific platform admins are configured, verify membership
        if (!platformAdmins.isEmpty() && !platformAdmins.contains(context.getUserId())) {
            throw new UserProfileAccessDeniedException("Caller '" + context.getUserId() + "' lacks required platform administrator privilege");
        }
    }

    private boolean isPlatformAdmin(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            return false;
        }
        if (platformAdmins.isEmpty()) {
            return true; // Default permissive in test if no explicit admin set
        }
        return platformAdmins.contains(context.getUserId());
    }

    @Override
    public Institute createInstitute(UserSecurityContext context, Institute institute) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        verifyPlatformAdmin(context);

        if (institute == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (institute.getInstituteCode() == null || institute.getInstituteCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'instituteCode' is required");
        }

        String code = institute.getInstituteCode().trim();
        // Unique code enforcement
        for (Institute existing : institutes.values()) {
            if (existing.getInstituteCode().equalsIgnoreCase(code)) {
                throw new InstituteAlreadyExistsException("Institute", "instituteCode", code);
            }
        }

        Institute record = new Institute();
        String id = institute.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        } else {
            try {
                id = UUID.fromString(id.trim()).toString();
            } catch (IllegalArgumentException e) {
                id = UUID.randomUUID().toString();
            }
        }
        record.setId(id);
        record.setInstituteCode(code);
        record.setLegalName(institute.getLegalName());
        record.setDisplayName(institute.getDisplayName() != null ? institute.getDisplayName() : institute.getLegalName());
        record.setTimezone(institute.getTimezone() != null ? institute.getTimezone() : "Asia/Kolkata");
        record.setLocale(institute.getLocale() != null ? institute.getLocale() : "en-IN");
        record.setDefaultCurrency(institute.getDefaultCurrency() != null ? institute.getDefaultCurrency() : "INR");
        record.setStatus(institute.getStatus() != null ? institute.getStatus() : "ACTIVE");
        record.setVersion(1);
        record.setCreatedAt(System.currentTimeMillis());
        record.setUpdatedAt(record.getCreatedAt());

        institutes.put(record.getId(), record);
        return copy(record);
    }

    @Override
    public Institute updateInstitute(UserSecurityContext context, String id, Institute update, Integer expectedVersion) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        if (id == null || id.trim().isEmpty()) {
            throw new InstituteNotFoundException("Institute", id);
        }

        // Validate UUID format (safe handling: prevents 22P02, returns 404 for invalid IDs)
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("Institute", id);
        }

        Institute existing = institutes.get(id);
        if (existing == null) {
            throw new InstituteNotFoundException("Institute", id);
        }

        // Enforce tenant isolation if caller is not platform admin
        if (!isPlatformAdmin(context) && context.getTenantId() != null && !context.getTenantId().toString().equals(id)) {
            throw new UserProfileAccessDeniedException("Caller lacks permission to update institute in another tenant");
        }

        // Immutable identity key check
        if (update.getInstituteCode() != null && !update.getInstituteCode().equals(existing.getInstituteCode())) {
            throw new SecurityViolationException("ADM01_IMMUTABLE_KEY_MODIFICATION",
                    "Modification of immutable identity key 'instituteCode' is prohibited");
        }

        // Optimistic locking check
        if (expectedVersion != null && existing.getVersion() != expectedVersion) {
            throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                    String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, existing.getVersion()));
        }

        if (update.getDisplayName() != null) existing.setDisplayName(update.getDisplayName());
        if (update.getStatus() != null) existing.setStatus(update.getStatus());
        if (update.getTimezone() != null) existing.setTimezone(update.getTimezone());
        if (update.getLocale() != null) existing.setLocale(update.getLocale());
        if (update.getDefaultCurrency() != null) existing.setDefaultCurrency(update.getDefaultCurrency());
        if (update.getLegalName() != null) existing.setLegalName(update.getLegalName());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(System.currentTimeMillis());

        return copy(existing);
    }

    @Override
    public Optional<Institute> getInstituteById(UserSecurityContext context, String id) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        Institute inst = institutes.get(id);
        if (inst == null) {
            return Optional.empty();
        }

        // Tenant isolation
        if (!isPlatformAdmin(context) && context.getTenantId() != null && !context.getTenantId().toString().equals(id)) {
            return Optional.empty();
        }

        return Optional.of(copy(inst));
    }

    @Override
    public Optional<Institute> getInstituteByCode(UserSecurityContext context, String code) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }

        for (Institute inst : institutes.values()) {
            if (inst.getInstituteCode().equalsIgnoreCase(code.trim())) {
                if (!isPlatformAdmin(context) && context.getTenantId() != null && !context.getTenantId().toString().equals(inst.getId())) {
                    return Optional.empty();
                }
                return Optional.of(copy(inst));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Institute> listInstitutes(UserSecurityContext context) {
        return listInstitutes(context, null, 1, 100);
    }

    @Override
    public List<Institute> listInstitutes(UserSecurityContext context, String status, int page, int size) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }

        List<Institute> results = new ArrayList<>();
        boolean admin = isPlatformAdmin(context);

        for (Institute inst : institutes.values()) {
            if (!admin && context.getTenantId() != null && !context.getTenantId().toString().equals(inst.getId())) {
                continue;
            }
            if (status == null || status.trim().isEmpty() || status.equalsIgnoreCase(inst.getStatus())) {
                results.add(copy(inst));
            }
        }

        int limit = (size > 0) ? size : 50;
        int p = (page > 0) ? page : 1;
        int fromIndex = (p - 1) * limit;
        if (fromIndex >= results.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + limit, results.size());
        return results.subList(fromIndex, toIndex);
    }

    private Institute copy(Institute original) {
        if (original == null) return null;
        Institute c = new Institute();
        c.setId(original.getId());
        c.setInstituteCode(original.getInstituteCode());
        c.setDisplayName(original.getDisplayName());
        c.setLegalName(original.getLegalName());
        c.setTimezone(original.getTimezone());
        c.setLocale(original.getLocale());
        c.setDefaultCurrency(original.getDefaultCurrency());
        c.setStatus(original.getStatus());
        c.setVersion(original.getVersion());
        c.setCreatedAt(original.getCreatedAt());
        c.setUpdatedAt(original.getUpdatedAt());
        return c;
    }
}
