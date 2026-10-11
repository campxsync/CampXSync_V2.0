package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.College;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link CollegeRepository} intended solely for fast
 * unit and contract testing.
 * <p>
 * <b>NOTE:</b> This is NEVER used for production persistence. Production persistence
 * uses {@link PostgresCollegeRepository}.
 */
public class InMemoryCollegeRepository implements CollegeRepository {

    private final Map<String, College> storage = new ConcurrentHashMap<>();
    private final Set<UUID> platformAdmins = Collections.synchronizedSet(new HashSet<>());

    public void addPlatformAdmin(UUID adminId) {
        if (adminId != null) {
            platformAdmins.add(adminId);
        }
    }

    public void clear() {
        storage.clear();
        platformAdmins.clear();
    }

    @Override
    public College createCollege(UserSecurityContext context, College college) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (college == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (college.getCollegeCode() == null || college.getCollegeCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'collegeCode' is required");
        }
        if (college.getName() == null || college.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'name' is required");
        }
        if (college.getInstituteId() == null || college.getInstituteId().trim().isEmpty()) {
            throw new InstituteNotFoundException("Parent Institute", college.getInstituteId());
        }

        // Validate UUID syntax for instituteId
        UUID instituteUuid;
        try {
            instituteUuid = UUID.fromString(college.getInstituteId().trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("Parent Institute", college.getInstituteId());
        }

        // Tenant scope check: tenant admin can only create college for their own tenant
        if (!context.isPlatformScope() && !context.getTenantId().equals(instituteUuid)) {
            throw new UserProfileAccessDeniedException("Caller not authorized to manage colleges for tenant: " + instituteUuid);
        }

        // Platform admin whitelist check if configured
        if (!platformAdmins.isEmpty() && context.isPlatformScope() && !platformAdmins.contains(context.getUserId())) {
            throw new UserProfileAccessDeniedException("Caller '" + context.getUserId() + "' lacks required platform administrator privilege");
        }

        // Uniqueness check: duplicate collegeCode within the same institute
        String code = college.getCollegeCode().trim();
        for (College existing : storage.values()) {
            if (existing.getInstituteId().equalsIgnoreCase(college.getInstituteId().trim())
                    && existing.getCollegeCode().equalsIgnoreCase(code)) {
                throw new InstituteAlreadyExistsException("College", "collegeCode", code);
            }
        }

        College copy = copyCollege(college);
        if (copy.getId() == null || copy.getId().trim().isEmpty()) {
            copy.setId(UUID.randomUUID().toString());
        } else {
            // Verify safe UUID format
            try {
                UUID.fromString(copy.getId().trim());
            } catch (IllegalArgumentException e) {
                copy.setId(UUID.randomUUID().toString());
            }
        }
        if (copy.getStatus() == null || copy.getStatus().trim().isEmpty()) {
            copy.setStatus("ACTIVE");
        }
        copy.setVersion(1);
        long now = System.currentTimeMillis();
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);

        storage.put(copy.getId(), copy);
        return copyCollege(copy);
    }

    @Override
    public Optional<College> getCollegeById(UserSecurityContext context, String id) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        // Pre-validate UUID format safely
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        College found = storage.get(id.trim());
        if (found == null) {
            return Optional.empty();
        }

        // Tenant isolation check
        if (!context.isPlatformScope() && !context.getTenantId().toString().equalsIgnoreCase(found.getInstituteId())) {
            return Optional.empty();
        }

        return Optional.of(copyCollege(found));
    }

    @Override
    public Optional<College> getCollegeByCode(UserSecurityContext context, String instituteId, String collegeCode) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (instituteId == null || collegeCode == null) {
            return Optional.empty();
        }

        try {
            UUID.fromString(instituteId.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (!context.isPlatformScope() && !context.getTenantId().toString().equalsIgnoreCase(instituteId.trim())) {
            return Optional.empty();
        }

        for (College c : storage.values()) {
            if (c.getInstituteId().equalsIgnoreCase(instituteId.trim())
                    && c.getCollegeCode().equalsIgnoreCase(collegeCode.trim())) {
                return Optional.of(copyCollege(c));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<College> listColleges(UserSecurityContext context, String instituteId) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (instituteId == null || instituteId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            UUID.fromString(instituteId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        if (!context.isPlatformScope() && !context.getTenantId().toString().equalsIgnoreCase(instituteId.trim())) {
            return Collections.emptyList();
        }

        List<College> result = new ArrayList<>();
        for (College c : storage.values()) {
            if (c.getInstituteId().equalsIgnoreCase(instituteId.trim())) {
                result.add(copyCollege(c));
            }
        }
        return result;
    }

    @Override
    public College updateCollege(UserSecurityContext context, String id, College update, Integer expectedVersion) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null || id.trim().isEmpty()) {
            throw new InstituteNotFoundException("College", id);
        }
        if (update == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }

        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("College", id);
        }

        College existing = storage.get(id.trim());
        if (existing == null) {
            throw new InstituteNotFoundException("College", id);
        }

        if (!context.isPlatformScope() && !context.getTenantId().toString().equalsIgnoreCase(existing.getInstituteId())) {
            throw new UserProfileAccessDeniedException("Cross-tenant college modification disallowed");
        }

        // Immutable code check
        if (update.getCollegeCode() != null && !update.getCollegeCode().equalsIgnoreCase(existing.getCollegeCode())) {
            throw new SecurityViolationException("ADM01_IMMUTABLE_KEY_MODIFICATION",
                    "Modification of immutable identity key 'collegeCode' is prohibited");
        }

        // Optimistic locking
        if (expectedVersion != null && existing.getVersion() != expectedVersion) {
            throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                    String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, existing.getVersion()));
        }

        if (update.getName() != null && !update.getName().trim().isEmpty()) {
            existing.setName(update.getName().trim());
        }
        if (update.getLegalName() != null) {
            existing.setLegalName(update.getLegalName().trim());
        }
        if (update.getStatus() != null && !update.getStatus().trim().isEmpty()) {
            existing.setStatus(update.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(System.currentTimeMillis());

        return copyCollege(existing);
    }

    private College copyCollege(College source) {
        College copy = new College();
        copy.setId(source.getId());
        copy.setCollegeCode(source.getCollegeCode());
        copy.setName(source.getName());
        copy.setLegalName(source.getLegalName());
        copy.setInstituteId(source.getInstituteId());
        copy.setCampusIds(new ArrayList<>(source.getCampusIds()));
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
