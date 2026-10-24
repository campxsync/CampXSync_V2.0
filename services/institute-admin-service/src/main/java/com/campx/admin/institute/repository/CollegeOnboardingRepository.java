package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.OnboardCollegeRequest;
import com.campx.admin.institute.model.OnboardCollegeResponse;
import com.campx.admin.institute.security.UserSecurityContext;

/**
 * Contract for autonomous institutional college onboarding saga execution.
 */
public interface CollegeOnboardingRepository {

    /**
     * Executes the college onboarding saga in an atomic transaction:
     * 1. Validates platform-admin authorization.
     * 2. Checks idempotency and uniqueness constraints.
     * 3. Creates tenant in core.tenants.
     * 4. Creates primary college in core.colleges.
     * 5. Seeds system roles via iam.seed_system_roles.
     * 6. Provisions college administrator in iam.user_profiles.
     * 7. Assigns COLLEGE_ADMIN role in iam.role_assignments.
     * 8. Records saga in plat.tenant_provisioning.
     * 9. Emits transactional outbox event in sys.outbox_events.
     * 10. Records platform audit event in audit.events.
     *
     * @param context authenticated caller security context
     * @param request onboarding parameters
     * @return populated onboarding response with generated IDs and saga status
     */
    OnboardCollegeResponse onboardCollege(UserSecurityContext context, OnboardCollegeRequest request);
}
