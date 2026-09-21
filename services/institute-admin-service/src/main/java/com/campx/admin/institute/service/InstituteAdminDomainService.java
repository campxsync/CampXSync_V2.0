package com.campx.admin.institute.service;

import com.campx.admin.institute.model.InstituteModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business domain logic for ADM-01: Institute Admin Service (Platform Tier).
 * Enforces business rules, state machines, idempotency, and logs every execution step.
 */
public class InstituteAdminDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminDomainService.class);

    // In-memory thread-safe repositories
    private final Map<String, Institute> institutes = new ConcurrentHashMap<>();
    private final Map<String, College> colleges = new ConcurrentHashMap<>();
    private final Map<String, TenantProvisioning> provisioningJobs = new ConcurrentHashMap<>();
    private final Map<String, GlobalSetting> globalSettings = new ConcurrentHashMap<>();
    private final Map<String, CommercialPlan> commercialPlans = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> auditTrail = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> idempotencyKeys = Collections.synchronizedSet(new HashSet<>());

    public InstituteAdminDomainService() {
        seedDefaults();
    }

    private void seedDefaults() {
        // Seed default platform plan
        CommercialPlan defaultPlan = new CommercialPlan();
        defaultPlan.setPlanCode("ENTERPRISE_CAMPUS_2026");
        defaultPlan.setName("CampXSync Enterprise Multi-Campus Plan");
        defaultPlan.setBillingCycle("ANNUALLY");
        defaultPlan.setPrice(1200000.0);
        defaultPlan.setCurrency("INR");
        defaultPlan.getEntitlements().addAll(Arrays.asList("STUDENT_MGMT", "FACULTY_MGMT", "EXAM_PORTAL", "AI_BIOMETRIC_ATTENDANCE"));
        commercialPlans.put(defaultPlan.getPlanCode(), defaultPlan);

        // Seed initial platform settings
        GlobalSetting s1 = new GlobalSetting();
        s1.setKey("security.password.min_length");
        s1.setValue("10");
        s1.setDataType("INTEGER");
        s1.setScope("GLOBAL");
        s1.setEffectiveFrom(System.currentTimeMillis() - 100000);
        globalSettings.put(s1.getKey(), s1);
    }

    /**
     * User Story 3: Register a new institute (tenant) on the platform
     */
    public Institute registerInstitute(Institute institute) {
        try (FlowTracker flow = logger.flow("RegisterInstituteWorkflow", "INST-" + institute.getInstituteCode())) {
            logger.info("Initiating institute registration for: {} (code: {})", institute.getLegalName(), institute.getInstituteCode());

            if (institute.getInstituteCode() == null || institute.getInstituteCode().trim().isEmpty()) {
                flow.markFailed(new IllegalArgumentException("instituteCode is mandatory"));
                throw new IllegalArgumentException("instituteCode is mandatory");
            }

            // Check uniqueness
            for (Institute existing : institutes.values()) {
                if (existing.getInstituteCode().equalsIgnoreCase(institute.getInstituteCode())) {
                    flow.markFailed(new IllegalStateException("Uniqueness violation: instituteCode already exists"));
                    throw new IllegalStateException("Uniqueness violation: instituteCode " + institute.getInstituteCode() + " already exists");
                }
            }

            flow.step("ValidateInstituteDetails");

            institute.setId(UUID.randomUUID().toString());
            institute.setStatus("ACTIVE");
            institute.setVersion(1);
            institutes.put(institute.getId(), institute);

            flow.step("PersistInstituteRecord");

            // Publish Audit event
            logger.audit(AuditEvent.builder()
                    .action("INSTITUTE_CREATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SUPER_ADMIN")
                    .principalRole("SUPER_ADMIN")
                    .resourceType("INSTITUTE")
                    .resourceId(institute.getId())
                    .status("SUCCESS")
                    .description("Registered institute: " + institute.getDisplayName() + " [" + institute.getInstituteCode() + "]")
                    .build());

            return institute;
        }
    }

    /**
     * User Story 4: Update institute profile while protecting published identity keys
     */
    public Institute updateInstitute(String id, Institute updateReq) {
        Institute existing = institutes.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("Institute with id " + id + " not found");
        }

        // Prevent modifying immutable identity keys
        if (updateReq.getInstituteCode() != null && !updateReq.getInstituteCode().equals(existing.getInstituteCode())) {
            throw new IllegalArgumentException("Modification of immutable identity key 'instituteCode' is prohibited");
        }

        if (updateReq.getDisplayName() != null) existing.setDisplayName(updateReq.getDisplayName());
        if (updateReq.getTimezone() != null) existing.setTimezone(updateReq.getTimezone());
        if (updateReq.getLocale() != null) existing.setLocale(updateReq.getLocale());
        if (updateReq.getStatus() != null) existing.setStatus(updateReq.getStatus());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(System.currentTimeMillis());

        logger.info("Updated institute id={} to version={}", id, existing.getVersion());
        return existing;
    }

    public List<Institute> listInstitutes() {
        return new ArrayList<>(institutes.values());
    }

    /**
     * User Story 6 & 7: Register a college under an institute with active-state check
     */
    public College registerCollege(College college) {
        try (FlowTracker flow = logger.flow("RegisterCollegeUnderInstitute", "COLLEGE-" + college.getCollegeCode())) {
            if (college.getInstituteId() == null || !institutes.containsKey(college.getInstituteId())) {
                flow.markFailed(new IllegalArgumentException("Referenced parent institute does not exist"));
                throw new IllegalArgumentException("Referenced parent institute does not exist");
            }

            Institute parent = institutes.get(college.getInstituteId());
            if (!"ACTIVE".equalsIgnoreCase(parent.getStatus())) {
                flow.markFailed(new IllegalStateException("Cannot register college under an INACTIVE institute"));
                throw new IllegalStateException("Cannot register college under an INACTIVE institute");
            }

            flow.step("ValidateCollegeUniqueness");
            for (College c : colleges.values()) {
                if (c.getCollegeCode().equalsIgnoreCase(college.getCollegeCode())) {
                    flow.markFailed(new IllegalStateException("Uniqueness violation: collegeCode already exists"));
                    throw new IllegalStateException("Uniqueness violation: collegeCode already exists");
                }
            }

            college.setId(UUID.randomUUID().toString());
            college.setStatus("ACTIVE");
            colleges.put(college.getId(), college);

            flow.step("PersistCollegeRecord");
            logger.info("Successfully registered college {} under institute {}", college.getName(), parent.getDisplayName());

            return college;
        }
    }

    /**
     * User Story 8: Provision a tenant/college context end-to-end with idempotency
     */
    public TenantProvisioning provisionTenant(TenantProvisioning req) {
        String idempotencyKey = req.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKeys.add(idempotencyKey)) {
            logger.warn("Idempotent replay detected for provisioning request with key: {}", idempotencyKey);
            for (TenantProvisioning existing : provisioningJobs.values()) {
                if (idempotencyKey.equals(existing.getIdempotencyKey())) {
                    return existing;
                }
            }
        }

        try (FlowTracker flow = logger.flow("TenantProvisioningStateMachine", "PROV-" + req.getTenantId())) {
            req.setProvisioningId(UUID.randomUUID().toString());
            req.setProvisioningStatus("REQUESTED");
            flow.step("ValidateProvisioningPrerequisites");

            // Transition: REQUESTED -> VALIDATED
            req.setProvisioningStatus("VALIDATED");
            flow.step("ValidateCommercialPlanAndEntitlements");

            // Transition: VALIDATED -> PROVISIONING
            req.setProvisioningStatus("PROVISIONING");
            flow.step("InitializeTenantDatabaseAndIsolationScopes");

            // Transition: PROVISIONING -> COMPLETED
            req.setProvisioningStatus("COMPLETED");
            req.setCompletedAt(System.currentTimeMillis());

            provisioningJobs.put(req.getProvisioningId(), req);

            logger.audit(AuditEvent.builder()
                    .action("TENANT_PROVISIONED")
                    .principalId(req.getRequestedBy() != null ? req.getRequestedBy() : "SYSTEM_OPS")
                    .principalRole("PLATFORM_OPERATIONS")
                    .resourceType("TENANT")
                    .resourceId(req.getTenantId())
                    .status("SUCCESS")
                    .description("Completed provisioning for tenant: " + req.getTenantId() + " on scope: " + req.getTargetScope())
                    .build());

            return req;
        }
    }

    public List<TenantProvisioning> getProvisioningJobs() {
        return new ArrayList<>(provisioningJobs.values());
    }

    /**
     * User Story 11: Define and update a global platform configuration setting with secret protection
     */
    public GlobalSetting setGlobalSetting(GlobalSetting setting) {
        if (setting.isSecret() && setting.getValue() != null && !setting.getValue().startsWith("vault:") && !setting.getValue().startsWith("secret://")) {
            throw new IllegalArgumentException("Security Violation: Plaintext secrets are rejected. Must supply vault or KMS reference.");
        }
        globalSettings.put(setting.getKey(), setting);
        logger.info("Saved global configuration key: {} for scope: {}", setting.getKey(), setting.getScope());
        return setting;
    }

    public List<GlobalSetting> getGlobalSettings() {
        return new ArrayList<>(globalSettings.values());
    }

    public List<CommercialPlan> getCommercialPlans() {
        return new ArrayList<>(commercialPlans.values());
    }
}
