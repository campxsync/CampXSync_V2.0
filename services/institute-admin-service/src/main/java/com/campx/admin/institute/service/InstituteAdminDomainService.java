package com.campx.admin.institute.service;

import com.campx.admin.institute.exception.*;
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

    private void recordAudit(AuditEvent event) {
        logger.audit(event);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("eventId", UUID.randomUUID().toString());
        record.put("action", event.getAction());
        record.put("principalId", event.getPrincipalId());
        record.put("principalRole", event.getPrincipalRole());
        record.put("resourceType", event.getResourceType());
        record.put("resourceId", event.getResourceId());
        record.put("status", event.getStatus());
        record.put("description", event.getDescription());
        record.put("timestamp", event.getTimestamp());
        record.put("traceId", LogContext.getTraceId());
        record.put("tenantId", LogContext.getTenantId());
        auditTrail.add(record);
    }

    public List<Map<String, Object>> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }

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
                MalformedPayloadException ex = new MalformedPayloadException("Mandatory field 'instituteCode' is required");
                flow.markFailed(ex);
                throw ex;
            }

            // Check uniqueness
            for (Institute existing : institutes.values()) {
                if (existing.getInstituteCode().equalsIgnoreCase(institute.getInstituteCode())) {
                    InstituteAlreadyExistsException ex = new InstituteAlreadyExistsException("Institute", "instituteCode", institute.getInstituteCode());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            flow.step("ValidateInstituteDetails");

            institute.setId(UUID.randomUUID().toString());
            institute.setStatus("ACTIVE");
            institute.setVersion(1);
            institutes.put(institute.getId(), institute);

            flow.step("PersistInstituteRecord");

            // Record and publish Audit event
            AuditEvent event = AuditEvent.builder()
                    .action("INSTITUTE_CREATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SUPER_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SUPER_ADMIN")
                    .resourceType("INSTITUTE")
                    .resourceId(institute.getId())
                    .status("SUCCESS")
                    .description("Registered institute: " + institute.getDisplayName() + " [" + institute.getInstituteCode() + "]")
                    .build();
            recordAudit(event);

            return institute;
        }
    }

    /**
     * User Story 4: Update institute profile while protecting published identity keys
     */
    public Institute updateInstitute(String id, Institute updateReq) {
        try (FlowTracker flow = logger.flow("UpdateInstituteWorkflow", "INST-" + id)) {
            Institute existing = institutes.get(id);
            if (existing == null) {
                InstituteNotFoundException ex = new InstituteNotFoundException("Institute", id);
                flow.markFailed(ex);
                throw ex;
            }

            // Prevent modifying immutable identity keys
            if (updateReq.getInstituteCode() != null && !updateReq.getInstituteCode().equals(existing.getInstituteCode())) {
                SecurityViolationException ex = new SecurityViolationException("ADM01_IMMUTABLE_KEY_MODIFICATION",
                        "Modification of immutable identity key 'instituteCode' is prohibited");
                flow.markFailed(ex);
                throw ex;
            }

            flow.step("ValidateUpdateParameters");
            if (updateReq.getDisplayName() != null) existing.setDisplayName(updateReq.getDisplayName());
            if (updateReq.getTimezone() != null) existing.setTimezone(updateReq.getTimezone());
            if (updateReq.getLocale() != null) existing.setLocale(updateReq.getLocale());
            if (updateReq.getStatus() != null) existing.setStatus(updateReq.getStatus());
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(System.currentTimeMillis());

            flow.step("PersistUpdatedInstitute");
            logger.info("Updated institute id={} to version={}", id, existing.getVersion());

            AuditEvent audit = AuditEvent.builder()
                    .action("INSTITUTE_UPDATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SUPER_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SUPER_ADMIN")
                    .resourceType("INSTITUTE")
                    .resourceId(id)
                    .status("SUCCESS")
                    .description("Updated institute " + existing.getDisplayName() + " to version " + existing.getVersion())
                    .build();
            recordAudit(audit);

            return existing;
        }
    }

    public List<Institute> listInstitutes() {
        return new ArrayList<>(institutes.values());
    }

    /**
     * User Story 6 & 7: Register a college under an institute with active-state check
     */
    public College registerCollege(College college) {
        try (FlowTracker flow = logger.flow("RegisterCollegeUnderInstitute", "COLLEGE-" + college.getCollegeCode())) {
            if (college.getCollegeCode() == null || college.getCollegeCode().trim().isEmpty()) {
                MalformedPayloadException ex = new MalformedPayloadException("Mandatory field 'collegeCode' is required");
                flow.markFailed(ex);
                throw ex;
            }

            if (college.getInstituteId() == null || !institutes.containsKey(college.getInstituteId())) {
                InstituteNotFoundException ex = new InstituteNotFoundException("Parent Institute", college.getInstituteId());
                flow.markFailed(ex);
                throw ex;
            }

            Institute parent = institutes.get(college.getInstituteId());
            if (!"ACTIVE".equalsIgnoreCase(parent.getStatus())) {
                InvalidTenantStateException ex = new InvalidTenantStateException("Institute", parent.getStatus(), "ACTIVE");
                flow.markFailed(ex);
                throw ex;
            }

            flow.step("ValidateCollegeUniqueness");
            for (College c : colleges.values()) {
                if (c.getCollegeCode().equalsIgnoreCase(college.getCollegeCode())) {
                    InstituteAlreadyExistsException ex = new InstituteAlreadyExistsException("College", "collegeCode", college.getCollegeCode());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            college.setId(UUID.randomUUID().toString());
            college.setStatus("ACTIVE");
            colleges.put(college.getId(), college);

            flow.step("PersistCollegeRecord");
            logger.info("Successfully registered college {} under institute {}", college.getName(), parent.getDisplayName());

            AuditEvent audit = AuditEvent.builder()
                    .action("COLLEGE_REGISTERED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "INSTITUTE_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "INSTITUTE_ADMIN")
                    .resourceType("COLLEGE")
                    .resourceId(college.getId())
                    .status("SUCCESS")
                    .description("Registered college: " + college.getName() + " [" + college.getCollegeCode() + "] under institute " + parent.getDisplayName())
                    .build();
            recordAudit(audit);

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

            AuditEvent audit = AuditEvent.builder()
                    .action("TENANT_PROVISIONED")
                    .principalId(req.getRequestedBy() != null ? req.getRequestedBy() : (LogContext.getUserId() != null ? LogContext.getUserId() : "SYSTEM_OPS"))
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "PLATFORM_OPERATIONS")
                    .resourceType("TENANT")
                    .resourceId(req.getTenantId())
                    .status("SUCCESS")
                    .description("Completed provisioning for tenant: " + req.getTenantId() + " on scope: " + req.getTargetScope())
                    .build();
            recordAudit(audit);

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
            throw new SecurityViolationException("ADM01_PLAINTEXT_SECRET_REJECTED",
                    "Security Violation: Plaintext secrets are rejected. Must supply vault or KMS reference.");
        }
        globalSettings.put(setting.getKey(), setting);
        logger.info("Saved global configuration key: {} for scope: {}", setting.getKey(), setting.getScope());

        AuditEvent audit = AuditEvent.builder()
                .action("GLOBAL_SETTING_CONFIGURED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SUPER_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SUPER_ADMIN")
                .resourceType("GLOBAL_SETTING")
                .resourceId(setting.getKey())
                .status("SUCCESS")
                .description("Configured global setting " + setting.getKey() + " for scope: " + setting.getScope())
                .build();
        recordAudit(audit);

        return setting;
    }

    public List<GlobalSetting> getGlobalSettings() {
        return new ArrayList<>(globalSettings.values());
    }

    public List<CommercialPlan> getCommercialPlans() {
        return new ArrayList<>(commercialPlans.values());
    }
}
