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
 * <p>
 * Enforces multi-tenant business rules, state machines, idempotency deduplication,
 * distributed execution flow tracing, tamper-evident hash chaining, and regulatory audit logging.
 *
 * @see InstituteModels
 * @see InstituteAdminController
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
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final List<OutboxEvent> outboxEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, InboxEvent> inboxEvents = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, FeatureFlag> featureFlags = new ConcurrentHashMap<>();
    private final Map<String, GlobalPolicy> globalPolicies = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigurationVersion>> configVersions = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Invoice> invoices = new ConcurrentHashMap<>();
    private final Map<String, BillingGatewayTransaction> billingTransactions = new ConcurrentHashMap<>();
    private final List<UsageMetric> usageMetrics = Collections.synchronizedList(new ArrayList<>());
    private final List<PlatformHealth> healthSnapshots = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, OperationalAlert> operationalAlerts = new ConcurrentHashMap<>();
    private final Map<String, WorkflowInstance> workflowInstances = new ConcurrentHashMap<>();
    private final Map<String, DataRetentionPolicy> retentionPolicies = new ConcurrentHashMap<>();
    private final Map<String, DataClassification> dataClassifications = new ConcurrentHashMap<>();
    private final Map<String, ExportRequest> exportRequests = new ConcurrentHashMap<>();
    private String lastAuditHash = "GENESIS_HASH_0000000000000000";

    private void recordAudit(AuditEvent event) {
        logger.audit(event);
        Map<String, Object> record = new LinkedHashMap<>();
        String eventId = UUID.randomUUID().toString();
        record.put("eventId", eventId);
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

        // Tamper-evident hash chain (CSV Line 36)
        String beforeHash = lastAuditHash;
        String payloadToHash = beforeHash + ":" + eventId + ":" + event.getAction() + ":" + event.getResourceId() + ":" + event.getTimestamp();
        String afterHash = sha256(payloadToHash);
        record.put("beforeHash", beforeHash);
        record.put("afterHash", afterHash);
        lastAuditHash = afterHash;

        auditTrail.add(record);
    }

    /**
     * Returns a thread-safe snapshot copy of the platform audit trail ledger.
     *
     * @return copy of audit records
     */
    public List<Map<String, Object>> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }

    /**
     * Constructs the domain service and seeds default platform plans and configuration settings.
     */
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

    public List<Institute> listInstitutes(String status, int page, int size) {
        List<Institute> filtered = new ArrayList<>();
        for (Institute inst : institutes.values()) {
            if (status == null || status.trim().isEmpty() || status.equalsIgnoreCase(inst.getStatus())) {
                filtered.add(inst);
            }
        }
        if (page <= 0) page = 1;
        if (size <= 0) size = 50;
        int fromIndex = (page - 1) * size;
        if (fromIndex >= filtered.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return filtered.subList(fromIndex, toIndex);
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
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String requestHash = sha256(req.getTenantId() + ":" + req.getPlanId());
            IdempotencyRecord rec = checkOrRecordIdempotency(idempotencyKey, "PROVISION_TENANT", requestHash, 3600000L);
            if ("COMPLETED".equals(rec.getStatus()) && rec.getResponseRef() != null) {
                logger.warn("Idempotent replay detected for provisioning request with key: {}", idempotencyKey);
                TenantProvisioning existing = provisioningJobs.get(rec.getResponseRef());
                if (existing != null) {
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
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                completeIdempotency(idempotencyKey, req.getProvisioningId());
            }
            emitOutboxEvent("TenantProvisioned", req.getProvisioningId(), req.getTenantId(),
                    "{\"tenantId\":\"" + req.getTenantId() + "\",\"status\":\"COMPLETED\"}");

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

    public List<GlobalSetting> getGlobalSettings(boolean effectiveOnly) {
        if (!effectiveOnly) {
            return new ArrayList<>(globalSettings.values());
        }
        long now = System.currentTimeMillis();
        List<GlobalSetting> effective = new ArrayList<>();
        for (GlobalSetting s : globalSettings.values()) {
            if (s.getEffectiveFrom() <= now && (s.getEffectiveTo() == 0 || s.getEffectiveTo() > now)) {
                effective.add(s);
            }
        }
        return effective;
    }

    public List<CommercialPlan> getCommercialPlans() {
        return new ArrayList<>(commercialPlans.values());
    }

    // =========================================================================
    // Phase 1: RBAC & Identity Management (User Story Lines 17–21)
    // =========================================================================

    private final Map<String, AdminUser> adminUsers = new ConcurrentHashMap<>();
    private final Map<String, PlatformRole> platformRoles = new ConcurrentHashMap<>();
    private final Map<String, Permission> permissions = new ConcurrentHashMap<>();
    private final Map<String, RoleBinding> roleBindings = new ConcurrentHashMap<>();
    private final Map<String, AccessReview> accessReviews = new ConcurrentHashMap<>();

    // IAM-known user references (simulated external IAM provider)
    private final Set<String> knownIamUsers = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
            "iam_super_admin_01", "iam_inst_admin_01", "iam_sec_admin_01", "iam_fin_admin_01",
            "iam_platform_ops_01", "iam_auditor_01", "iam_college_dean_01"
    )));

    /**
     * User Story 17: Register a platform administrative user without storing credentials.
     */
    public AdminUser registerAdminUser(AdminUser user) {
        try (FlowTracker flow = logger.flow("RegisterAdminUser", "USER-" + user.getUserId())) {
            if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                throw new MalformedPayloadException("Mandatory field 'userId' (IAM reference) is required");
            }

            // Validate against IAM provider — reject if unresolvable
            if (!knownIamUsers.contains(user.getUserId())) {
                SecurityViolationException ex = new SecurityViolationException("ADM01_IAM_REFERENCE_UNRESOLVABLE",
                        "User identity reference '" + user.getUserId() + "' cannot be resolved through IAM provider");
                flow.markFailed(ex);
                throw ex;
            }

            // Uniqueness check on userId
            for (AdminUser existing : adminUsers.values()) {
                if (existing.getUserId().equals(user.getUserId())) {
                    InstituteAlreadyExistsException ex = new InstituteAlreadyExistsException("AdminUser", "userId", user.getUserId());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            user.setId(UUID.randomUUID().toString());
            user.setUpdatedAt(System.currentTimeMillis());
            adminUsers.put(user.getId(), user);

            flow.step("PersistAdminUserRecord");
            logger.info("Registered admin user [{}] with IAM ref [{}], status={}", user.getDisplayName(), user.getUserId(), user.getStatus());

            AuditEvent audit = AuditEvent.builder()
                    .action("ADMIN_USER_REGISTERED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                    .resourceType("ADMIN_USER")
                    .resourceId(user.getId())
                    .status("SUCCESS")
                    .description("Registered admin user: " + user.getDisplayName() + " [" + user.getUserId() + "]")
                    .build();
            recordAudit(audit);

            return user;
        }
    }

    /**
     * User Story 17: Update admin user status/risk state.
     */
    public AdminUser updateAdminUserStatus(String id, String status, String riskState) {
        AdminUser user = adminUsers.get(id);
        if (user == null) {
            throw new InstituteNotFoundException("AdminUser", id);
        }
        if (status != null) user.setStatus(status);
        if (riskState != null) user.setRiskState(riskState);
        user.setUpdatedAt(System.currentTimeMillis());

        logger.info("Updated admin user [{}] status={}, riskState={}", user.getUserId(), user.getStatus(), user.getRiskState());

        AuditEvent audit = AuditEvent.builder()
                .action("ADMIN_USER_UPDATED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                .resourceType("ADMIN_USER")
                .resourceId(id)
                .status("SUCCESS")
                .description("Updated admin user status=" + user.getStatus() + ", riskState=" + user.getRiskState())
                .build();
        recordAudit(audit);

        return user;
    }

    public List<AdminUser> listAdminUsers() {
        return new ArrayList<>(adminUsers.values());
    }

    /**
     * User Story 18: Define a platform role with a permission bundle.
     */
    public PlatformRole createRole(PlatformRole role) {
        try (FlowTracker flow = logger.flow("CreatePlatformRole", "ROLE-" + role.getRoleCode())) {
            if (role.getRoleCode() == null || role.getRoleCode().trim().isEmpty()) {
                throw new MalformedPayloadException("Mandatory field 'roleCode' is required");
            }

            // Enforce (scope, roleCode) uniqueness
            for (PlatformRole existing : platformRoles.values()) {
                if (existing.getRoleCode().equalsIgnoreCase(role.getRoleCode())
                        && existing.getScope() != null && existing.getScope().equalsIgnoreCase(role.getScope())) {
                    InstituteAlreadyExistsException ex = new InstituteAlreadyExistsException("PlatformRole", "roleCode+scope", role.getRoleCode());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            role.setId(UUID.randomUUID().toString());
            platformRoles.put(role.getId(), role);

            flow.step("PersistPlatformRole");
            logger.info("Created platform role [{}] '{}' with {} permissions", role.getRoleCode(), role.getName(), role.getPermissionCodes().size());

            AuditEvent audit = AuditEvent.builder()
                    .action("ROLE_CREATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                    .resourceType("PLATFORM_ROLE")
                    .resourceId(role.getId())
                    .status("SUCCESS")
                    .description("Created role: " + role.getRoleCode() + " [" + role.getScope() + "]")
                    .build();
            recordAudit(audit);

            return role;
        }
    }

    /**
     * User Story 18: Update role permission bundle (increments version).
     */
    public PlatformRole updateRolePermissions(String roleId, List<String> permissionCodes) {
        PlatformRole role = platformRoles.get(roleId);
        if (role == null) {
            throw new InstituteNotFoundException("PlatformRole", roleId);
        }
        role.setPermissionCodes(permissionCodes);
        role.setVersion(role.getVersion() + 1);
        logger.info("Updated role [{}] permissions to version {}", role.getRoleCode(), role.getVersion());

        AuditEvent audit = AuditEvent.builder()
                .action("ROLE_UPDATED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                .resourceType("PLATFORM_ROLE")
                .resourceId(roleId)
                .status("SUCCESS")
                .description("Updated role " + role.getRoleCode() + " permissions to version " + role.getVersion())
                .build();
        recordAudit(audit);

        return role;
    }

    /**
     * User Story 18: Delete role — blocked for protected system roles.
     */
    public void deleteRole(String roleId) {
        PlatformRole role = platformRoles.get(roleId);
        if (role == null) {
            throw new InstituteNotFoundException("PlatformRole", roleId);
        }
        if (role.isProtectedSystemRole()) {
            throw new SecurityViolationException("ADM01_PROTECTED_ROLE_DELETE_BLOCKED",
                    "Cannot delete protected system role: " + role.getRoleCode());
        }
        platformRoles.remove(roleId);
        logger.warn("Deleted platform role: {}", role.getRoleCode());
    }

    public List<PlatformRole> listRoles() {
        return new ArrayList<>(platformRoles.values());
    }

    /**
     * User Story 19: Create a permission in the fine-grained catalog.
     */
    public Permission createPermission(Permission perm) {
        if (perm.getPermissionCode() == null || perm.getPermissionCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'permissionCode' is required");
        }

        // Enforce permissionCode uniqueness
        for (Permission existing : permissions.values()) {
            if (existing.getPermissionCode().equalsIgnoreCase(perm.getPermissionCode())) {
                throw new InstituteAlreadyExistsException("Permission", "permissionCode", perm.getPermissionCode());
            }
        }

        perm.setId(UUID.randomUUID().toString());
        permissions.put(perm.getId(), perm);
        logger.info("Created permission [{}] for resource={}, action={}", perm.getPermissionCode(), perm.getResource(), perm.getAction());
        return perm;
    }

    /**
     * User Story 19: Block mutation of permission codes already in use by roles.
     */
    public Permission updatePermission(String permissionId, Permission update) {
        Permission existing = permissions.get(permissionId);
        if (existing == null) {
            throw new InstituteNotFoundException("Permission", permissionId);
        }

        // Immutable once used
        if (existing.getUsedByRoles() > 0 && update.getPermissionCode() != null
                && !update.getPermissionCode().equals(existing.getPermissionCode())) {
            throw new SecurityViolationException("ADM01_PERMISSION_IMMUTABLE",
                    "Cannot modify permissionCode '" + existing.getPermissionCode() + "' — already referenced by " + existing.getUsedByRoles() + " role(s)");
        }

        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getResource() != null && existing.getUsedByRoles() == 0) existing.setResource(update.getResource());
        if (update.getAction() != null && existing.getUsedByRoles() == 0) existing.setAction(update.getAction());

        return existing;
    }

    public List<Permission> listPermissions() {
        return new ArrayList<>(permissions.values());
    }

    /**
     * User Story 20: Create a scoped role binding with privilege escalation detection.
     */
    public RoleBinding createRoleBinding(RoleBinding binding) {
        try (FlowTracker flow = logger.flow("CreateRoleBinding", "BIND-" + binding.getPrincipalId())) {
            if (binding.getPrincipalId() == null || binding.getRoleId() == null) {
                throw new MalformedPayloadException("Fields 'principalId' and 'roleId' are required");
            }

            // Validate role exists
            PlatformRole role = platformRoles.get(binding.getRoleId());
            if (role == null) {
                throw new InstituteNotFoundException("PlatformRole", binding.getRoleId());
            }

            // Enforce (tenantId, principalId, roleId, scope) uniqueness
            for (RoleBinding existing : roleBindings.values()) {
                if (safeEquals(existing.getTenantId(), binding.getTenantId())
                        && existing.getPrincipalId().equals(binding.getPrincipalId())
                        && existing.getRoleId().equals(binding.getRoleId())
                        && safeEquals(existing.getScope(), binding.getScope())) {
                    InstituteAlreadyExistsException ex = new InstituteAlreadyExistsException(
                            "RoleBinding", "tenantId+principalId+roleId+scope", binding.getPrincipalId() + ":" + binding.getRoleId());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            // Privilege escalation detection: granter must hold at least the same permissions
            String granterId = binding.getGrantedBy() != null ? binding.getGrantedBy()
                    : (LogContext.getUserId() != null ? LogContext.getUserId() : "SYSTEM");
            Set<String> granterPermissions = getEffectivePermissions(granterId);
            Set<String> grantedPermissions = new HashSet<>(role.getPermissionCodes());
            if (!granterPermissions.isEmpty() && !granterPermissions.containsAll(grantedPermissions)) {
                SecurityViolationException ex = new SecurityViolationException("ADM01_PRIVILEGE_ESCALATION",
                        "Role binding would grant privileges beyond the requester's own authorization scope");
                flow.markFailed(ex);
                throw ex;
            }

            binding.setId(UUID.randomUUID().toString());
            if (binding.getEffectiveFrom() == 0) {
                binding.setEffectiveFrom(System.currentTimeMillis());
            }
            binding.setGrantedBy(granterId);
            roleBindings.put(binding.getId(), binding);

            // Increment usedByRoles count for each permission in the role
            for (String pc : role.getPermissionCodes()) {
                for (Permission p : permissions.values()) {
                    if (p.getPermissionCode().equalsIgnoreCase(pc)) {
                        p.setUsedByRoles(p.getUsedByRoles() + 1);
                    }
                }
            }

            flow.step("PersistRoleBinding");
            logger.info("Created role binding: principal={}, role={}, scope={}", binding.getPrincipalId(), role.getRoleCode(), binding.getScope());

            AuditEvent audit = AuditEvent.builder()
                    .action("ROLE_BINDING_CREATED")
                    .principalId(granterId)
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                    .resourceType("ROLE_BINDING")
                    .resourceId(binding.getId())
                    .status("SUCCESS")
                    .description("Bound role " + role.getRoleCode() + " to principal " + binding.getPrincipalId() + " at scope " + binding.getScope())
                    .build();
            recordAudit(audit);

            return binding;
        }
    }

    /**
     * User Story 20: Revoke a role binding by setting effectiveTo.
     */
    public void revokeRoleBinding(String bindingId) {
        RoleBinding binding = roleBindings.get(bindingId);
        if (binding == null) {
            throw new InstituteNotFoundException("RoleBinding", bindingId);
        }
        binding.setEffectiveTo(System.currentTimeMillis());
        logger.warn("Revoked role binding id={}, principal={}", bindingId, binding.getPrincipalId());

        AuditEvent audit = AuditEvent.builder()
                .action("ROLE_BINDING_REVOKED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                .resourceType("ROLE_BINDING")
                .resourceId(bindingId)
                .status("SUCCESS")
                .description("Revoked role binding for principal " + binding.getPrincipalId())
                .build();
        recordAudit(audit);
    }

    public List<RoleBinding> listRoleBindings() {
        return new ArrayList<>(roleBindings.values());
    }

    /**
     * User Story 21: Create a periodic access review with separation-of-duties enforcement.
     */
    public AccessReview createAccessReview(AccessReview review) {
        try (FlowTracker flow = logger.flow("CreateAccessReview", "REVIEW-" + review.getPrincipalId())) {
            if (review.getPrincipalId() == null || review.getDueAt() == 0) {
                throw new MalformedPayloadException("Fields 'principalId' and 'dueAt' are required");
            }

            review.setId(UUID.randomUUID().toString());
            review.setReviewId("AR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            review.setStatus("OPEN");
            review.setDecision("PENDING");
            accessReviews.put(review.getId(), review);

            flow.step("PersistAccessReview");
            logger.info("Created access review [{}] for principal={}, dueAt={}", review.getReviewId(), review.getPrincipalId(), review.getDueAt());

            return review;
        }
    }

    /**
     * User Story 21: Complete an access review — self-certification blocked.
     */
    public AccessReview completeAccessReview(String reviewId, String reviewerId, String decision) {
        AccessReview review = null;
        for (AccessReview ar : accessReviews.values()) {
            if (ar.getReviewId().equals(reviewId) || ar.getId().equals(reviewId)) {
                review = ar;
                break;
            }
        }
        if (review == null) {
            throw new InstituteNotFoundException("AccessReview", reviewId);
        }

        // Self-certification prevention (separation of duties)
        if (review.getPrincipalId().equals(reviewerId)) {
            throw new SecurityViolationException("ADM01_SELF_CERTIFICATION_BLOCKED",
                    "Separation of duties: reviewer '" + reviewerId + "' cannot certify their own access (principal=" + review.getPrincipalId() + ")");
        }

        review.setReviewerId(reviewerId);
        review.setDecision(decision);
        review.setStatus("COMPLETED");
        review.setCompletedAt(System.currentTimeMillis());

        logger.info("Completed access review [{}]: decision={}, reviewer={}", review.getReviewId(), decision, reviewerId);

        AuditEvent audit = AuditEvent.builder()
                .action("ACCESS_REVIEW_COMPLETED")
                .principalId(reviewerId)
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "SECURITY_ADMIN")
                .resourceType("ACCESS_REVIEW")
                .resourceId(review.getReviewId())
                .status("SUCCESS")
                .description("Access review " + review.getReviewId() + " completed: " + decision + " for principal " + review.getPrincipalId())
                .build();
        recordAudit(audit);

        return review;
    }

    public List<AccessReview> listAccessReviews() {
        return new ArrayList<>(accessReviews.values());
    }

    // =========================================================================
    // Helper Utilities
    // =========================================================================

    /**
     * Resolve effective permissions for a given principal by aggregating all active role bindings.
     */
    private Set<String> getEffectivePermissions(String principalId) {
        Set<String> effectivePerms = new HashSet<>();
        long now = System.currentTimeMillis();
        for (RoleBinding rb : roleBindings.values()) {
            if (rb.getPrincipalId().equals(principalId)
                    && rb.getEffectiveFrom() <= now
                    && (rb.getEffectiveTo() == 0 || rb.getEffectiveTo() > now)) {
                PlatformRole role = platformRoles.get(rb.getRoleId());
                if (role != null) {
                    effectivePerms.addAll(role.getPermissionCodes());
                }
            }
        }
        return effectivePerms;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer (User Story Lines 40–43)
    // =========================================================================

    /**
     * User Story 40: Exactly-once command processing with payload hash verification.
     */
    public IdempotencyRecord checkOrRecordIdempotency(String idempotencyKey, String operation, String requestHash, long ttlMs) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new MalformedPayloadException("Idempotency key cannot be empty");
        }

        IdempotencyRecord existing = idempotencyRecords.get(idempotencyKey);
        if (existing != null) {
            // Validate request hash to guard against payload mutation with the same key
            if (requestHash != null && existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash)) {
                throw new InstituteAlreadyExistsException("IdempotencyRecord", "requestHash",
                        "Payload hash mismatch for idempotency key: " + idempotencyKey);
            }
            logger.info("[Reliability] Idempotency record found for key [{}] - status={}", idempotencyKey, existing.getStatus());
            return existing;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID().toString());
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation(operation);
        record.setRequestHash(requestHash);
        record.setStatus("PROCESSING");
        record.setExpiresAt(System.currentTimeMillis() + (ttlMs > 0 ? ttlMs : 3600000L));
        record.setCreatedAt(System.currentTimeMillis());

        idempotencyRecords.put(idempotencyKey, record);
        logger.info("[Reliability] Registered new idempotency key [{}] for operation [{}]", idempotencyKey, operation);
        return record;
    }

    public void completeIdempotency(String idempotencyKey, String responseRef) {
        IdempotencyRecord rec = idempotencyRecords.get(idempotencyKey);
        if (rec != null) {
            rec.setStatus("COMPLETED");
            rec.setResponseRef(responseRef);
            logger.info("[Reliability] Completed idempotency key [{}] with ref [{}]", idempotencyKey, responseRef);
        }
    }

    public IdempotencyRecord getIdempotencyRecord(String idempotencyKey) {
        return idempotencyRecords.get(idempotencyKey);
    }

    public List<IdempotencyRecord> listIdempotencyRecords() {
        return new ArrayList<>(idempotencyRecords.values());
    }

    /**
     * User Story 41: Publish domain event via transactional outbox.
     */
    public OutboxEvent emitOutboxEvent(String eventType, String aggregateId, String tenantId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setTenantId(tenantId != null ? tenantId : (LogContext.getTenantId() != null ? LogContext.getTenantId() : "GLOBAL"));
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setCreatedAt(System.currentTimeMillis());

        outboxEvents.add(event);
        logger.info("[ADM-01 Outbox] Emitted event [{}] for aggregate [{}]", eventType, aggregateId);
        return event;
    }

    public List<OutboxEvent> listOutboxEvents() {
        return new ArrayList<>(outboxEvents);
    }

    /**
     * User Story 42: Consume inbound event via inbox with deduplication.
     */
    public InboxEvent processInboxEvent(String eventId, String sourceService, String consumerGroup, String payload) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new MalformedPayloadException("Inbound eventId is required");
        }
        String dedupeKey = (sourceService != null ? sourceService : "UPSTREAM") + ":" + (consumerGroup != null ? consumerGroup : "DEFAULT") + ":" + eventId;

        InboxEvent existing = inboxEvents.get(dedupeKey);
        if (existing != null) {
            logger.warn("[ADM-01 Inbox] Duplicate inbound event ignored: {}", dedupeKey);
            return existing;
        }

        InboxEvent inbox = new InboxEvent();
        inbox.setId(UUID.randomUUID().toString());
        inbox.setEventId(eventId);
        inbox.setSourceService(sourceService != null ? sourceService : "UPSTREAM");
        inbox.setConsumerGroup(consumerGroup != null ? consumerGroup : "DEFAULT");
        inbox.setStatus("PROCESSED");
        inbox.setProcessedAt(System.currentTimeMillis());

        inboxEvents.put(dedupeKey, inbox);
        logger.info("[ADM-01 Inbox] Successfully processed inbound event [{}] from [{}]", eventId, sourceService);
        return inbox;
    }

    public List<InboxEvent> listInboxEvents() {
        return new ArrayList<>(inboxEvents.values());
    }

    /**
     * User Story 43: Route failed events to dead-letter queue.
     */
    public DeadLetterEvent routeToDeadLetter(String originalEventId, String eventType, String failureCode, int retryCount, String payload) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId(UUID.randomUUID().toString());
        dlq.setOriginalEventId(originalEventId);
        dlq.setEventType(eventType);
        dlq.setFailureCode(failureCode);
        dlq.setRetryCount(retryCount);
        dlq.setPayload(payload);
        dlq.setDisposition("OPEN");
        dlq.setCreatedAt(System.currentTimeMillis());

        deadLetterEvents.add(dlq);
        logger.error("[ADM-01 DLQ] Routed event [{}] to dead-letter queue: code={}, retries={}", originalEventId, failureCode, retryCount);
        return dlq;
    }

    public List<DeadLetterEvent> listDeadLetterEvents() {
        return new ArrayList<>(deadLetterEvents);
    }

    /**
     * User Story 43: Controlled replay of dead-letter event.
     */
    public DeadLetterEvent replayDeadLetterEvent(String deadLetterId) {
        DeadLetterEvent found = null;
        for (DeadLetterEvent dl : deadLetterEvents) {
            if (dl.getId().equals(deadLetterId)) {
                found = dl;
                break;
            }
        }
        if (found == null) {
            throw new InstituteNotFoundException("DeadLetterEvent", deadLetterId);
        }

        found.setDisposition("REPLAYED");
        emitOutboxEvent(found.getEventType() + ".Replayed", found.getOriginalEventId(), "GLOBAL", found.getPayload());
        logger.info("[ADM-01 DLQ] Replayed dead-letter event [{}]", deadLetterId);
        return found;
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toHexString(input != null ? input.hashCode() : 0);
        }
    }

    // =========================================================================
    // Phase 3: Configuration & Policy Engine Methods (User Story Lines 13–15)
    // =========================================================================

    /**
     * User Story 13: Roll out a feature flag with overrides & kill switch
     */
    public FeatureFlag createFeatureFlag(FeatureFlag flag) {
        if (flag.getFlagKey() == null || flag.getFlagKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Feature flag key is required");
        }
        for (FeatureFlag existing : featureFlags.values()) {
            if (existing.getFlagKey().equalsIgnoreCase(flag.getFlagKey())) {
                throw new InstituteAlreadyExistsException("FeatureFlag", "flagKey", flag.getFlagKey());
            }
        }
        flag.setId(UUID.randomUUID().toString());
        featureFlags.put(flag.getFlagKey(), flag);
        emitOutboxEvent("FeatureFlagCreated", flag.getId(), "GLOBAL", "{\"flagKey\":\"" + flag.getFlagKey() + "\"}");
        logger.info("[ADM-01] Created feature flag [{}] default={}", flag.getFlagKey(), flag.getDefaultValue());
        return flag;
    }

    public FeatureFlag getFeatureFlag(String flagKey) {
        FeatureFlag flag = featureFlags.get(flagKey);
        if (flag == null) {
            throw new InstituteNotFoundException("FeatureFlag", flagKey);
        }
        return flag;
    }

    public List<FeatureFlag> listFeatureFlags() {
        return new ArrayList<>(featureFlags.values());
    }

    public FeatureFlag addTenantOverride(String flagKey, String tenantId, String overrideValue, String reason) {
        FeatureFlag flag = getFeatureFlag(flagKey);
        if (flag.isGloballyLocked()) {
            throw new SecurityViolationException("ADM01_FLAG_LOCKED", "Feature flag '" + flagKey + "' is globally locked and cannot be overridden");
        }
        FeatureFlag.TenantOverride override = new FeatureFlag.TenantOverride();
        override.setTenantId(tenantId);
        override.setOverrideValue(overrideValue);
        override.setReason(reason);
        override.setEffectiveFrom(System.currentTimeMillis());
        flag.getTenantOverrides().add(override);

        emitOutboxEvent("FeatureFlagOverridden", flag.getId(), tenantId, "{\"flagKey\":\"" + flagKey + "\",\"override\":\"" + overrideValue + "\"}");
        logger.info("[ADM-01] Added override for feature flag [{}] on tenant [{}]: {}", flagKey, tenantId, overrideValue);
        return flag;
    }

    /**
     * User Story 14: Publish an immutable governance policy
     */
    public GlobalPolicy createGlobalPolicy(GlobalPolicy policy) {
        if (policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Policy code is required");
        }
        for (GlobalPolicy existing : globalPolicies.values()) {
            if (existing.getPolicyCode().equalsIgnoreCase(policy.getPolicyCode())) {
                throw new InstituteAlreadyExistsException("GlobalPolicy", "policyCode", policy.getPolicyCode());
            }
        }
        policy.setId(UUID.randomUUID().toString());
        policy.setStatus("DRAFT");
        policy.setVersion(1);
        globalPolicies.put(policy.getPolicyCode(), policy);
        logger.info("[ADM-01] Created draft policy [{}]", policy.getPolicyCode());
        return policy;
    }

    public GlobalPolicy approveGlobalPolicy(String policyCode, String approvedBy) {
        GlobalPolicy policy = getGlobalPolicy(policyCode);
        if (approvedBy == null || approvedBy.trim().isEmpty()) {
            throw new MalformedPayloadException("Approver identity is required");
        }
        policy.setApprovedBy(approvedBy);
        policy.setStatus("APPROVED");
        logger.info("[ADM-01] Approved policy [{}] by {}", policyCode, approvedBy);
        return policy;
    }

    public GlobalPolicy publishGlobalPolicy(String policyCode) {
        GlobalPolicy policy = getGlobalPolicy(policyCode);
        if (!"APPROVED".equalsIgnoreCase(policy.getStatus()) && policy.getApprovedBy() == null) {
            throw new InvalidTenantStateException("GlobalPolicy", policyCode, "Policy must be approved before publication");
        }
        policy.setStatus("PUBLISHED");
        policy.setPublishedAt(System.currentTimeMillis());
        emitOutboxEvent("GlobalPolicyPublished", policy.getId(), "GLOBAL", "{\"policyCode\":\"" + policyCode + "\",\"version\":" + policy.getVersion() + "}");
        logger.info("[ADM-01] Published immutable policy [{}] v{}", policyCode, policy.getVersion());
        return policy;
    }

    public GlobalPolicy getGlobalPolicy(String policyCode) {
        GlobalPolicy policy = globalPolicies.get(policyCode);
        if (policy == null) {
            throw new InstituteNotFoundException("GlobalPolicy", policyCode);
        }
        return policy;
    }

    public List<GlobalPolicy> listGlobalPolicies() {
        return new ArrayList<>(globalPolicies.values());
    }

    /**
     * User Story 15: Snapshot and roll back configuration versions
     */
    public ConfigurationVersion snapshotConfiguration(String scope) {
        String effectiveScope = scope != null ? scope : "GLOBAL";
        List<ConfigurationVersion> versions = configVersions.computeIfAbsent(effectiveScope, k -> Collections.synchronizedList(new ArrayList<>()));

        int nextVersion = versions.size() + 1;
        // Build snapshot JSON from current globalSettings
        StringBuilder sb = new StringBuilder("{\"settings\":{");
        int count = 0;
        for (GlobalSetting s : globalSettings.values()) {
            if (count > 0) sb.append(",");
            sb.append("\"").append(s.getKey()).append("\":\"").append(s.getValue() != null ? s.getValue() : "").append("\"");
            count++;
        }
        sb.append("}}");
        String snapshotJson = sb.toString();
        String checksum = sha256(snapshotJson);

        // Mark previous versions SUPERSEDED
        for (ConfigurationVersion v : versions) {
            v.setStatus("SUPERSEDED");
        }

        ConfigurationVersion cv = new ConfigurationVersion();
        cv.setId(UUID.randomUUID().toString());
        cv.setScope(effectiveScope);
        cv.setVersion(nextVersion);
        cv.setSnapshotJson(snapshotJson);
        cv.setChecksum(checksum);
        cv.setStatus("ACTIVE");
        cv.setCreatedAt(System.currentTimeMillis());

        versions.add(cv);
        emitOutboxEvent("ConfigurationSnapshotCreated", cv.getId(), effectiveScope, "{\"version\":" + nextVersion + ",\"checksum\":\"" + checksum + "\"}");
        logger.info("[ADM-01] Created configuration snapshot v{} for scope [{}], checksum={}", nextVersion, effectiveScope, checksum);
        return cv;
    }

    public ConfigurationVersion rollbackConfiguration(String scope, int targetVersion) {
        String effectiveScope = scope != null ? scope : "GLOBAL";
        List<ConfigurationVersion> versions = configVersions.get(effectiveScope);
        if (versions == null || versions.isEmpty()) {
            throw new InstituteNotFoundException("ConfigurationVersion", effectiveScope);
        }

        ConfigurationVersion target = null;
        for (ConfigurationVersion v : versions) {
            if (v.getVersion() == targetVersion) {
                target = v;
                break;
            }
        }
        if (target == null) {
            throw new InstituteNotFoundException("ConfigurationVersion", effectiveScope + ":" + targetVersion);
        }

        // Validate checksum integrity
        String recalculated = sha256(target.getSnapshotJson());
        if (!recalculated.equals(target.getChecksum())) {
            throw new SecurityViolationException("ADM01_CHECKSUM_MISMATCH", "Snapshot checksum verification failed for rollback");
        }

        for (ConfigurationVersion v : versions) {
            v.setStatus("SUPERSEDED");
        }
        target.setStatus("ACTIVE");

        emitOutboxEvent("ConfigurationRolledBack", target.getId(), effectiveScope, "{\"targetVersion\":" + targetVersion + "}");
        logger.info("[ADM-01] Rolled back configuration for scope [{}] to version {}", effectiveScope, targetVersion);
        return target;
    }

    public List<ConfigurationVersion> listConfigurationVersions(String scope) {
        String effectiveScope = scope != null ? scope : "GLOBAL";
        List<ConfigurationVersion> list = configVersions.get(effectiveScope);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    // =========================================================================
    // Phase 4: Commercial & Billing Domain Methods (User Story Lines 23–27)
    // =========================================================================

    /**
     * User Story 23: Maintain commercial plan pricing, currency, billing frequency, entitlements.
     */
    public CommercialPlan createCommercialPlan(CommercialPlan plan) {
        if (plan.getPlanCode() == null || plan.getPlanCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Plan code is required");
        }
        if (plan.getPrice() < 0) {
            throw new MalformedPayloadException("Plan price cannot be negative");
        }
        for (CommercialPlan existing : commercialPlans.values()) {
            if (existing.getPlanCode().equalsIgnoreCase(plan.getPlanCode())) {
                throw new InstituteAlreadyExistsException("CommercialPlan", "planCode", plan.getPlanCode());
            }
        }
        plan.setPublished(false);
        plan.setVersion(1);
        commercialPlans.put(plan.getPlanCode(), plan);
        logger.info("[ADM-01 Billing] Created draft commercial plan [{}]", plan.getPlanCode());
        return plan;
    }

    public CommercialPlan publishCommercialPlan(String planCode) {
        CommercialPlan plan = commercialPlans.get(planCode);
        if (plan == null) {
            throw new InstituteNotFoundException("CommercialPlan", planCode);
        }
        plan.setPublished(true);
        emitOutboxEvent("CommercialPlanPublished", plan.getPlanCode(), "GLOBAL", "{\"planCode\":\"" + planCode + "\",\"version\":" + plan.getVersion() + "}");
        logger.info("[ADM-01 Billing] Published commercial plan [{}]", planCode);
        return plan;
    }

    /**
     * User Story 24: Tenant subscription lifecycle and entitlement capture.
     */
    public Subscription createSubscription(Subscription sub) {
        if (sub.getTenantId() == null || sub.getPlanId() == null) {
            throw new MalformedPayloadException("Both tenantId and planId are required for subscription");
        }
        CommercialPlan plan = commercialPlans.get(sub.getPlanId());
        if (plan == null) {
            throw new InstituteNotFoundException("CommercialPlan", sub.getPlanId());
        }
        if (!plan.isPublished()) {
            throw new InvalidTenantStateException("CommercialPlan", sub.getPlanId(), "Plan must be published before subscription activation");
        }

        sub.setId(UUID.randomUUID().toString());
        sub.setStatus("ACTIVE");
        sub.setEntitlementSnapshot(new ArrayList<>(plan.getEntitlements()));
        sub.setStartDate(System.currentTimeMillis());
        if (sub.getEndDate() == 0) {
            sub.setEndDate(System.currentTimeMillis() + 31536000000L); // 1 year default
        }
        subscriptions.put(sub.getId(), sub);

        emitOutboxEvent("SubscriptionCreated", sub.getId(), sub.getTenantId(), "{\"planId\":\"" + sub.getPlanId() + "\",\"status\":\"ACTIVE\"}");
        logger.info("[ADM-01 Billing] Created subscription [{}] for tenant [{}] on plan [{}]", sub.getId(), sub.getTenantId(), sub.getPlanId());

        AuditEvent audit = AuditEvent.builder()
                .action("SUBSCRIPTION_CREATED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "FINANCE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "FINANCE_ADMIN")
                .resourceType("SUBSCRIPTION")
                .resourceId(sub.getId())
                .status("SUCCESS")
                .description("Subscription activated for tenant " + sub.getTenantId() + " on plan " + sub.getPlanId())
                .build();
        recordAudit(audit);

        return sub;
    }

    public Subscription transitionSubscription(String subId, String newStatus) {
        Subscription sub = subscriptions.get(subId);
        if (sub == null) {
            throw new InstituteNotFoundException("Subscription", subId);
        }

        // State machine validation
        String current = sub.getStatus();
        if ("CANCELLED".equalsIgnoreCase(current)) {
            throw new InvalidTenantStateException("Subscription", subId, "Cannot transition a CANCELLED subscription");
        }

        sub.setStatus(newStatus);
        emitOutboxEvent("SubscriptionChanged", sub.getId(), sub.getTenantId(), "{\"previousStatus\":\"" + current + "\",\"newStatus\":\"" + newStatus + "\"}");
        logger.info("[ADM-01 Billing] Transitioned subscription [{}] from {} to {}", subId, current, newStatus);

        AuditEvent audit = AuditEvent.builder()
                .action("SUBSCRIPTION_STATUS_CHANGED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "FINANCE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "FINANCE_ADMIN")
                .resourceType("SUBSCRIPTION")
                .resourceId(subId)
                .status("SUCCESS")
                .description("Subscription status changed from " + current + " to " + newStatus)
                .build();
        recordAudit(audit);

        return sub;
    }

    public List<Subscription> listSubscriptions(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return new ArrayList<>(subscriptions.values());
        }
        List<Subscription> filtered = new ArrayList<>();
        for (Subscription s : subscriptions.values()) {
            if (tenantId.equalsIgnoreCase(s.getTenantId())) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    /**
     * User Story 25: Generate and issue invoices with tax computation.
     */
    public Invoice issueInvoice(String subscriptionId, String billingPeriod) {
        Subscription sub = subscriptions.get(subscriptionId);
        if (sub == null) {
            throw new InstituteNotFoundException("Subscription", subscriptionId);
        }
        CommercialPlan plan = commercialPlans.get(sub.getPlanId());
        double subtotal = plan != null ? plan.getPrice() : 100000.0;
        double tax = subtotal * 0.18; // 18% GST / VAT
        double total = subtotal + tax;

        Invoice inv = new Invoice();
        inv.setId(UUID.randomUUID().toString());
        inv.setInvoiceNo("INV-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        inv.setSubscriptionId(subscriptionId);
        inv.setTenantId(sub.getTenantId());
        inv.setBillingPeriod(billingPeriod != null ? billingPeriod : "2026-09");
        inv.setSubtotal(subtotal);
        inv.setTax(tax);
        inv.setTotal(total);
        inv.setStatus("ISSUED");
        inv.setIssuedAt(System.currentTimeMillis());

        invoices.put(inv.getId(), inv);
        emitOutboxEvent("InvoiceIssued", inv.getId(), inv.getTenantId(), "{\"invoiceNo\":\"" + inv.getInvoiceNo() + "\",\"total\":" + total + "}");
        logger.info("[ADM-01 Billing] Issued invoice [{}] for tenant [{}] total={}", inv.getInvoiceNo(), inv.getTenantId(), total);

        AuditEvent audit = AuditEvent.builder()
                .action("INVOICE_ISSUED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "FINANCE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "FINANCE_ADMIN")
                .resourceType("INVOICE")
                .resourceId(inv.getId())
                .status("SUCCESS")
                .description("Issued invoice " + inv.getInvoiceNo() + " for total=" + total)
                .build();
        recordAudit(audit);

        return inv;
    }

    public List<Invoice> listInvoices(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return new ArrayList<>(invoices.values());
        }
        List<Invoice> filtered = new ArrayList<>();
        for (Invoice inv : invoices.values()) {
            if (tenantId.equalsIgnoreCase(inv.getTenantId())) {
                filtered.add(inv);
            }
        }
        return filtered;
    }

    /**
     * User Story 26: Reconcile gateway payment transactions.
     * Idempotent by gatewayTransactionId; sensitive payment details never stored.
     */
    public BillingGatewayTransaction reconcileGatewayTransaction(String gatewayTxnId, String invoiceId, double amount, String currency, String rawReference) {
        if (gatewayTxnId == null || gatewayTxnId.trim().isEmpty()) {
            throw new MalformedPayloadException("Gateway transaction ID is required");
        }

        // Idempotent webhook handling
        BillingGatewayTransaction existing = billingTransactions.get(gatewayTxnId);
        if (existing != null) {
            logger.info("[ADM-01 Billing] Transaction [{}] already reconciled (idempotent replay)", gatewayTxnId);
            return existing;
        }

        Invoice inv = invoices.get(invoiceId);
        if (inv == null) {
            // Check by invoiceNo
            for (Invoice i : invoices.values()) {
                if (i.getInvoiceNo().equalsIgnoreCase(invoiceId)) {
                    inv = i;
                    break;
                }
            }
        }
        if (inv == null) {
            throw new InstituteNotFoundException("Invoice", invoiceId);
        }

        inv.setStatus("PAID");
        inv.setPaidAt(System.currentTimeMillis());

        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        txn.setId(UUID.randomUUID().toString());
        txn.setGatewayTransactionId(gatewayTxnId);
        txn.setInvoiceId(inv.getId());
        txn.setTenantId(inv.getTenantId());
        txn.setAmount(amount > 0 ? amount : inv.getTotal());
        txn.setCurrency(currency != null ? currency : "INR");
        txn.setRawReference(rawReference != null ? rawReference : "GATEWAY_WEBHOOK_ACK");
        txn.setStatus("SUCCESS");
        txn.setReconciledAt(System.currentTimeMillis());

        billingTransactions.put(gatewayTxnId, txn);
        emitOutboxEvent("BillingTransactionReconciled", txn.getId(), inv.getTenantId(), "{\"gatewayTxnId\":\"" + gatewayTxnId + "\",\"invoiceId\":\"" + inv.getId() + "\"}");
        logger.info("[ADM-01 Billing] Reconciled payment [{}] for invoice [{}]", gatewayTxnId, inv.getInvoiceNo());

        AuditEvent audit = AuditEvent.builder()
                .action("PAYMENT_RECONCILED")
                .principalId("PAYMENT_GATEWAY_WEBHOOK")
                .principalRole("GATEWAY")
                .resourceType("BILLING_TRANSACTION")
                .resourceId(gatewayTxnId)
                .status("SUCCESS")
                .description("Reconciled payment of " + txn.getAmount() + " " + txn.getCurrency() + " for invoice " + inv.getInvoiceNo())
                .build();
        recordAudit(audit);

        return txn;
    }

    public List<BillingGatewayTransaction> listBillingTransactions() {
        return new ArrayList<>(billingTransactions.values());
    }

    /**
     * User Story 27: Record tenant consumption usage metrics for billing.
     */
    public UsageMetric recordUsageMetric(String tenantId, String metricType, String period, long value) {
        if (tenantId == null || metricType == null) {
            throw new MalformedPayloadException("TenantId and metricType are required");
        }
        UsageMetric metric = new UsageMetric();
        metric.setId(UUID.randomUUID().toString());
        metric.setTenantId(tenantId);
        metric.setMetricType(metricType);
        metric.setPeriod(period != null ? period : "2026-09");
        metric.setValue(value);
        metric.setCalculatedAt(System.currentTimeMillis());

        usageMetrics.add(metric);
        emitOutboxEvent("UsageMetricCalculated", metric.getId(), tenantId, "{\"metricType\":\"" + metricType + "\",\"value\":" + value + "}");
        logger.info("[ADM-01 Billing] Recorded usage metric [{}] for tenant [{}]: {}", metricType, tenantId, value);
        return metric;
    }

    public List<UsageMetric> getUsageMetrics(String tenantId, String period) {
        List<UsageMetric> result = new ArrayList<>();
        for (UsageMetric m : usageMetrics) {
            if (tenantId != null && !tenantId.isEmpty() && !tenantId.equalsIgnoreCase(m.getTenantId())) {
                continue;
            }
            if (period != null && !period.isEmpty() && !period.equalsIgnoreCase(m.getPeriod())) {
                continue;
            }
            result.add(m);
        }
        return result;
    }

    // =========================================================================
    // Phase 6: Operations & Workflows Domain Logic (Lines 29, 30, 39)
    // =========================================================================

    /**
     * User Story 29: Record synthetic platform health snapshot.
     */
    public PlatformHealth recordHealthSnapshot(PlatformHealth snapshot) {
        if (snapshot.getComponent() == null) {
            throw new MalformedPayloadException("Component is required for health snapshot");
        }
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setObservedAt(System.currentTimeMillis());
        healthSnapshots.add(snapshot);
        logger.info("[ADM-01 Health] Component [{}] status: {} latency: {}ms", snapshot.getComponent(), snapshot.getStatus(), snapshot.getLatencyMs());
        return snapshot;
    }

    public List<PlatformHealth> getHealthSnapshots() {
        return new ArrayList<>(healthSnapshots);
    }

    /**
     * User Story 30: Raise operational alert with deduplication against active alerts.
     */
    public OperationalAlert raiseAlert(OperationalAlert alert) {
        if (alert.getAlertCode() == null || alert.getSource() == null) {
            throw new MalformedPayloadException("AlertCode and Source are required");
        }

        // Deduplication against OPEN or ACKNOWLEDGED alerts
        for (OperationalAlert existing : operationalAlerts.values()) {
            if (!"RESOLVED".equalsIgnoreCase(existing.getStatus())) {
                if (alert.getDeduplicationKey() != null && alert.getDeduplicationKey().equals(existing.getDeduplicationKey())) {
                    logger.info("[ADM-01 Alerts] Deduplicated alert by key [{}]: returning existing alert [{}]", alert.getDeduplicationKey(), existing.getId());
                    return existing;
                }
                if (alert.getSource().equalsIgnoreCase(existing.getSource())
                        && alert.getAlertCode().equalsIgnoreCase(existing.getAlertCode())
                        && (alert.getScope() == null || alert.getScope().equalsIgnoreCase(existing.getScope()))) {
                    logger.info("[ADM-01 Alerts] Deduplicated alert by signature [{}:{}]: returning existing [{}]", alert.getSource(), alert.getAlertCode(), existing.getId());
                    return existing;
                }
            }
        }

        alert.setId("ALT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        alert.setStatus("OPEN");
        alert.setCreatedAt(System.currentTimeMillis());
        operationalAlerts.put(alert.getId(), alert);

        emitOutboxEvent("OperationalAlertRaised", alert.getId(), alert.getSource(), "{\"alertCode\":\"" + alert.getAlertCode() + "\",\"severity\":\"" + alert.getSeverity() + "\"}");
        logger.warn("[ADM-01 Alerts] Raised new alert [{}] code: [{}] severity: {}", alert.getId(), alert.getAlertCode(), alert.getSeverity());
        return alert;
    }

    public OperationalAlert acknowledgeAlert(String alertId, String assignee) {
        OperationalAlert alert = operationalAlerts.get(alertId);
        if (alert == null) {
            throw new InstituteNotFoundException("OperationalAlert", alertId);
        }
        if ("RESOLVED".equalsIgnoreCase(alert.getStatus())) {
            throw new InvalidTenantStateException("Cannot acknowledge an already RESOLVED alert");
        }
        alert.setStatus("ACKNOWLEDGED");
        alert.setAssignee(assignee != null ? assignee : "OPS_ONCALL");
        alert.setAcknowledgedAt(System.currentTimeMillis());

        emitOutboxEvent("OperationalAlertAcknowledged", alert.getId(), alert.getSource(), "{\"assignee\":\"" + alert.getAssignee() + "\"}");
        logger.info("[ADM-01 Alerts] Acknowledged alert [{}] by [{}]", alertId, alert.getAssignee());
        return alert;
    }

    public OperationalAlert resolveAlert(String alertId, String resolutionNotes) {
        OperationalAlert alert = operationalAlerts.get(alertId);
        if (alert == null) {
            throw new InstituteNotFoundException("OperationalAlert", alertId);
        }
        alert.setStatus("RESOLVED");
        alert.setResolutionNotes(resolutionNotes != null ? resolutionNotes : "Issue resolved");
        alert.setResolvedAt(System.currentTimeMillis());

        emitOutboxEvent("OperationalAlertResolved", alert.getId(), alert.getSource(), "{\"resolutionNotes\":\"" + alert.getResolutionNotes() + "\"}");
        logger.info("[ADM-01 Alerts] Resolved alert [{}]", alertId);
        return alert;
    }

    public List<OperationalAlert> listAlerts(String status, String severity) {
        List<OperationalAlert> result = new ArrayList<>();
        for (OperationalAlert a : operationalAlerts.values()) {
            if (status != null && !status.isEmpty() && !a.getStatus().equalsIgnoreCase(status)) {
                continue;
            }
            if (severity != null && !severity.isEmpty() && !a.getSeverity().equalsIgnoreCase(severity)) {
                continue;
            }
            result.add(a);
        }
        return result;
    }

    /**
     * User Story 39: Start an administrative workflow instance.
     */
    public WorkflowInstance startWorkflow(String workflowType, String subject, String startedBy) {
        if (workflowType == null) {
            throw new MalformedPayloadException("WorkflowType is required");
        }
        WorkflowInstance wf = new WorkflowInstance();
        wf.setId("WF_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        wf.setWorkflowType(workflowType);
        wf.setSubject(subject != null ? subject : "Global Administrative Task");
        wf.setStartedBy(startedBy != null ? startedBy : "SUPER_ADMIN");
        wf.setCurrentState("INITIATED");
        wf.setStartedAt(System.currentTimeMillis());
        wf.getSteps().add("INITIATED");

        workflowInstances.put(wf.getId(), wf);
        emitOutboxEvent("AdministrativeWorkflowStarted", wf.getId(), workflowType, "{\"subject\":\"" + wf.getSubject() + "\"}");
        logger.info("[ADM-01 Workflow] Started workflow [{}] type: [{}]", wf.getId(), workflowType);
        return wf;
    }

    public WorkflowInstance transitionWorkflow(String workflowId, String targetState, String stepName) {
        WorkflowInstance wf = workflowInstances.get(workflowId);
        if (wf == null) {
            throw new InstituteNotFoundException("WorkflowInstance", workflowId);
        }
        if (stepName != null && !stepName.isEmpty()) {
            wf.getSteps().add(stepName);
        }
        wf.setCurrentState(targetState != null ? targetState : "RUNNING");
        if ("COMPLETED".equalsIgnoreCase(targetState) || "FAILED".equalsIgnoreCase(targetState) || "TIMED_OUT".equalsIgnoreCase(targetState)) {
            wf.setCompletedAt(System.currentTimeMillis());
            emitOutboxEvent("AdministrativeWorkflowCompleted", wf.getId(), wf.getWorkflowType(), "{\"finalState\":\"" + wf.getCurrentState() + "\"}");
        }
        logger.info("[ADM-01 Workflow] Transitioned workflow [{}] to [{}]", workflowId, wf.getCurrentState());
        return wf;
    }

    public WorkflowInstance getWorkflow(String workflowId) {
        WorkflowInstance wf = workflowInstances.get(workflowId);
        if (wf == null) {
            throw new InstituteNotFoundException("WorkflowInstance", workflowId);
        }
        return wf;
    }

    public List<WorkflowInstance> listWorkflows() {
        return new ArrayList<>(workflowInstances.values());
    }

    // =========================================================================
    // Phase 7: Data Governance & Audit Enhancement (User Story Lines 32–34, 37)
    // =========================================================================

    /**
     * Create a data retention policy. Rejects if retentionDays < legalMinimumDays (CSV Line 32).
     */
    public DataRetentionPolicy createRetentionPolicy(DataRetentionPolicy policy) {
        if (policy.getPolicyCode() == null || policy.getPolicyCode().isEmpty()) {
            throw new MalformedPayloadException("policyCode is required");
        }
        // Uniqueness check
        for (DataRetentionPolicy existing : retentionPolicies.values()) {
            if (existing.getPolicyCode().equalsIgnoreCase(policy.getPolicyCode())) {
                throw new InstituteAlreadyExistsException("DataRetentionPolicy with policyCode '" + policy.getPolicyCode() + "' already exists");
            }
        }
        // Legal minimum enforcement
        if (policy.getRetentionDays() < policy.getLegalMinimumDays()) {
            throw new SecurityViolationException(
                    "Retention policy violation: retentionDays (" + policy.getRetentionDays()
                    + ") cannot be less than legalMinimumDays (" + policy.getLegalMinimumDays() + ")");
        }
        policy.setId(UUID.randomUUID().toString());
        retentionPolicies.put(policy.getId(), policy);

        emitOutboxEvent("DataRetentionPoliciesUpdated", policy.getId(), "PLATFORM",
                "{\"policyCode\":\"" + policy.getPolicyCode() + "\",\"retentionDays\":" + policy.getRetentionDays() + "}");

        AuditEvent audit = AuditEvent.builder()
                .action("DATA_RETENTION_POLICY_CREATED")
                .principalId("SUPER_ADMIN").principalRole("SUPER_ADMIN")
                .resourceType("DATA_RETENTION_POLICY").resourceId(policy.getId())
                .status("SUCCESS")
                .description("Created retention policy: " + policy.getPolicyCode() + " (" + policy.getRetentionDays() + " days, legal min: " + policy.getLegalMinimumDays() + ")")
                .build();
        recordAudit(audit);
        logger.info("[ADM-01 DataGov] Created retention policy [{}] retentionDays={} legalMin={}",
                policy.getPolicyCode(), policy.getRetentionDays(), policy.getLegalMinimumDays());
        return policy;
    }

    public List<DataRetentionPolicy> listRetentionPolicies() {
        return new ArrayList<>(retentionPolicies.values());
    }

    /**
     * Create a data classification entry. Versioned and audited (CSV Line 33).
     */
    public DataClassification createDataClassification(DataClassification classification) {
        if (classification.getClassCode() == null || classification.getClassCode().isEmpty()) {
            throw new MalformedPayloadException("classCode is required");
        }
        // Uniqueness check
        for (DataClassification existing : dataClassifications.values()) {
            if (existing.getClassCode().equalsIgnoreCase(classification.getClassCode())) {
                throw new InstituteAlreadyExistsException("DataClassification with classCode '" + classification.getClassCode() + "' already exists");
            }
        }
        classification.setId(UUID.randomUUID().toString());
        dataClassifications.put(classification.getId(), classification);

        AuditEvent audit = AuditEvent.builder()
                .action("DATA_CLASSIFICATION_CREATED")
                .principalId("SUPER_ADMIN").principalRole("SUPER_ADMIN")
                .resourceType("DATA_CLASSIFICATION").resourceId(classification.getId())
                .status("SUCCESS")
                .description("Created data classification: " + classification.getClassCode() + " (sensitivity: " + classification.getSensitivity() + ", export: " + classification.getExportRules() + ")")
                .build();
        recordAudit(audit);
        logger.info("[ADM-01 DataGov] Created classification [{}] sensitivity={} exportRules={}",
                classification.getClassCode(), classification.getSensitivity(), classification.getExportRules());
        return classification;
    }

    public List<DataClassification> listDataClassifications() {
        return new ArrayList<>(dataClassifications.values());
    }

    /**
     * Create an export request. expiresAt is mandatory (CSV Line 34).
     */
    public ExportRequest createExportRequest(ExportRequest request) {
        if (request.getExpiresAt() <= 0) {
            throw new MalformedPayloadException("expiresAt is mandatory for export requests");
        }
        if (request.getRequestedBy() == null || request.getRequestedBy().isEmpty()) {
            throw new MalformedPayloadException("requestedBy is required");
        }
        request.setId(UUID.randomUUID().toString());
        request.setStatus("PENDING");
        exportRequests.put(request.getId(), request);

        AuditEvent audit = AuditEvent.builder()
                .action("EXPORT_REQUESTED")
                .principalId(request.getRequestedBy()).principalRole("DATA_ADMIN")
                .resourceType("EXPORT_REQUEST").resourceId(request.getId())
                .status("SUCCESS")
                .description("Export request created: scope=" + request.getDataScope() + " format=" + request.getFormat() + " purpose=" + request.getPurpose())
                .build();
        recordAudit(audit);
        logger.info("[ADM-01 DataGov] Export request [{}] created by [{}] scope={}",
                request.getId(), request.getRequestedBy(), request.getDataScope());
        return request;
    }

    /**
     * Approve an export request. Checks data classification exportRules (CSV Line 34).
     */
    public ExportRequest approveExportRequest(String requestId, String approvedBy) {
        ExportRequest request = exportRequests.get(requestId);
        if (request == null) {
            throw new InstituteNotFoundException("ExportRequest", requestId);
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new SecurityViolationException("Export request " + requestId + " is not in PENDING state (current: " + request.getStatus() + ")");
        }
        // Check data classification exportRules
        if (request.getDataClass() != null) {
            for (DataClassification dc : dataClassifications.values()) {
                if (dc.getClassCode().equalsIgnoreCase(request.getDataClass())
                        && "PROHIBITED".equalsIgnoreCase(dc.getExportRules())) {
                    throw new SecurityViolationException(
                            "Export prohibited: data class '" + request.getDataClass() + "' has exportRules=PROHIBITED");
                }
            }
        }
        request.setStatus("APPROVED");
        request.setApprovedBy(approvedBy);

        AuditEvent audit = AuditEvent.builder()
                .action("EXPORT_APPROVED")
                .principalId(approvedBy).principalRole("DATA_GOVERNANCE_OFFICER")
                .resourceType("EXPORT_REQUEST").resourceId(requestId)
                .status("SUCCESS")
                .description("Export request approved by " + approvedBy)
                .build();
        recordAudit(audit);
        logger.info("[ADM-01 DataGov] Export request [{}] APPROVED by [{}]", requestId, approvedBy);
        return request;
    }

    /**
     * Complete an export. Sets objectRef + checksum and emits ExportCompleted event (CSV Line 34).
     */
    public ExportRequest completeExport(String requestId, String objectRef, String checksum) {
        ExportRequest request = exportRequests.get(requestId);
        if (request == null) {
            throw new InstituteNotFoundException("ExportRequest", requestId);
        }
        if (!"APPROVED".equals(request.getStatus())) {
            throw new SecurityViolationException("Export request " + requestId + " must be APPROVED before completion (current: " + request.getStatus() + ")");
        }
        request.setStatus("COMPLETED");
        request.setObjectRef(objectRef);
        request.setChecksum(checksum);
        request.setCompletedAt(System.currentTimeMillis());

        emitOutboxEvent("ExportCompleted", request.getId(), "PLATFORM",
                "{\"objectRef\":\"" + objectRef + "\",\"checksum\":\"" + checksum + "\"}");

        AuditEvent audit = AuditEvent.builder()
                .action("EXPORT_COMPLETED")
                .principalId(request.getRequestedBy()).principalRole("DATA_ADMIN")
                .resourceType("EXPORT_REQUEST").resourceId(requestId)
                .status("SUCCESS")
                .description("Export completed: objectRef=" + objectRef + " checksum=" + checksum)
                .build();
        recordAudit(audit);
        logger.info("[ADM-01 DataGov] Export [{}] COMPLETED objectRef={}", requestId, objectRef);
        return request;
    }

    public List<ExportRequest> listExportRequests() {
        return new ArrayList<>(exportRequests.values());
    }

    /**
     * Search audit trail by correlationId (traceId). Returns all entries matching the trace (CSV Line 37).
     */
    public List<Map<String, Object>> searchAuditByCorrelationId(String correlationId) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> entry : auditTrail) {
            Object traceId = entry.get("traceId");
            if (traceId != null && traceId.toString().equalsIgnoreCase(correlationId)) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Search audit trail by actor (principalId) within a date range (CSV Line 37).
     */
    public List<Map<String, Object>> searchAuditByActor(String actorId, long fromDate, long toDate) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> entry : auditTrail) {
            Object principal = entry.get("principalId");
            Object timestamp = entry.get("timestamp");
            if (principal != null && principal.toString().equalsIgnoreCase(actorId) && timestamp != null) {
                long ts = Long.parseLong(timestamp.toString());
                if (ts >= fromDate && ts <= toDate) {
                    results.add(entry);
                }
            }
        }
        return results;
    }
}
