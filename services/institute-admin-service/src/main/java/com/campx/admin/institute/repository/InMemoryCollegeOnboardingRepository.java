package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.OnboardCollegeRequest;
import com.campx.admin.institute.model.OnboardCollegeResponse;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double for CollegeOnboardingRepository.
 */
public class InMemoryCollegeOnboardingRepository implements CollegeOnboardingRepository {

    private final Map<String, OnboardCollegeResponse> tenantsByCode = new ConcurrentHashMap<>();
    private final Map<String, OnboardCollegeResponse> tenantsByEmail = new ConcurrentHashMap<>();
    private final Map<String, OnboardCollegeResponse> tenantsByIdempotency = new ConcurrentHashMap<>();

    @Override
    public OnboardCollegeResponse onboardCollege(UserSecurityContext context, OnboardCollegeRequest request) {
        if (context == null || context.getUserId() == null) {
            throw new UserProfileAccessDeniedException("Missing authenticated caller security context");
        }
        if (!context.isPlatformScope()) {
            throw new UserProfileAccessDeniedException("Only platform administrators are authorized to onboard institutional colleges");
        }
        if (request == null) {
            throw new MalformedPayloadException("Onboarding request cannot be null");
        }
        if (request.getTenantName() == null || request.getTenantName().trim().isEmpty() ||
            request.getTenantCode() == null || request.getTenantCode().trim().isEmpty() ||
            request.getCollegeName() == null || request.getCollegeName().trim().isEmpty() ||
            request.getCollegeCode() == null || request.getCollegeCode().trim().isEmpty() ||
            request.getAdminEmail() == null || request.getAdminEmail().trim().isEmpty() ||
            request.getAdminName() == null || request.getAdminName().trim().isEmpty()) {
            throw new MalformedPayloadException("Missing mandatory onboarding parameters");
        }

        String tenantCode = request.getTenantCode().trim().toUpperCase();
        String adminEmail = request.getAdminEmail().trim().toLowerCase();
        String idempotencyKey = request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null;

        if (idempotencyKey != null && tenantsByIdempotency.containsKey(idempotencyKey)) {
            OnboardCollegeResponse existing = tenantsByIdempotency.get(idempotencyKey);
            OnboardCollegeResponse replay = new OnboardCollegeResponse();
            replay.setTenantId(existing.getTenantId());
            replay.setTenantCode(existing.getTenantCode());
            replay.setTenantName(existing.getTenantName());
            replay.setCollegeId(existing.getCollegeId());
            replay.setCollegeCode(existing.getCollegeCode());
            replay.setCollegeName(existing.getCollegeName());
            replay.setAdminUserId(existing.getAdminUserId());
            replay.setAdminEmail(existing.getAdminEmail());
            replay.setStatus("ACTIVE");
            replay.setIdempotentReplay(true);
            replay.addStep(1, "CREATE_TENANT", "COMPLETED", "Tenant already registered (idempotent replay)");
            replay.addStep(2, "CREATE_COLLEGE", "COMPLETED", "College already configured");
            replay.addStep(3, "SEED_ROLES", "COMPLETED", "System roles already active");
            replay.addStep(4, "PROVISION_ADMIN", "COMPLETED", "Administrator already provisioned");
            replay.addStep(5, "ASSIGN_ROLE", "COMPLETED", "COLLEGE_ADMIN role verified");
            return replay;
        }

        if (tenantsByCode.containsKey(tenantCode)) {
            throw new ResourceConflictException("Tenant Code", tenantCode);
        }
        if (tenantsByEmail.containsKey(adminEmail)) {
            throw new ResourceConflictException("Administrator Email", adminEmail);
        }

        UUID tenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        OnboardCollegeResponse response = new OnboardCollegeResponse();
        response.setTenantId(tenantId);
        response.setTenantCode(tenantCode);
        response.setTenantName(request.getTenantName().trim());
        response.setCollegeId(collegeId);
        response.setCollegeCode(request.getCollegeCode().trim().toUpperCase());
        response.setCollegeName(request.getCollegeName().trim());
        response.setAdminUserId(adminUserId);
        response.setAdminEmail(adminEmail);
        response.setStatus("ACTIVE");
        response.setIdempotentReplay(false);
        response.addStep(1, "CREATE_TENANT", "COMPLETED", "Institutional tenant created in core.tenants");
        response.addStep(2, "CREATE_COLLEGE", "COMPLETED", "Primary college created in core.colleges");
        response.addStep(3, "SEED_ROLES", "COMPLETED", "System role templates seeded into tenant scope");
        response.addStep(4, "PROVISION_ADMIN", "COMPLETED", "College administrator profile created in iam.user_profiles");
        response.addStep(5, "ASSIGN_ROLE", "COMPLETED", "COLLEGE_ADMIN role assigned in iam.role_assignments");
        response.addStep(6, "AUDIT_OUTBOX", "COMPLETED", "Transactional outbox event and immutable audit logged");

        tenantsByCode.put(tenantCode, response);
        tenantsByEmail.put(adminEmail, response);
        if (idempotencyKey != null) {
            tenantsByIdempotency.put(idempotencyKey, response);
        }

        return response;
    }
}
