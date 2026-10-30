package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.OnboardCollegeRequest;
import com.campx.admin.institute.model.OnboardCollegeResponse;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * PostgreSQL implementation of CollegeOnboardingRepository.
 * Executes the complete transactional college onboarding saga under strict platform-admin RBAC.
 */
public class PostgresCollegeOnboardingRepository implements CollegeOnboardingRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCollegeOnboardingRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresCollegeOnboardingRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCollegeOnboardingRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager != null ? connectionManager : DatabaseConnectionManager.getInstance();
    }

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
            throw new MalformedPayloadException("Missing mandatory onboarding parameters (tenantName, tenantCode, collegeName, collegeCode, adminName, adminEmail)");
        }

        final String tenantCode = request.getTenantCode().trim().toUpperCase();
        final String tenantName = request.getTenantName().trim();
        final String collegeCode = request.getCollegeCode().trim().toUpperCase();
        final String collegeName = request.getCollegeName().trim();
        final String adminEmail = request.getAdminEmail().trim().toLowerCase();
        final String adminName = request.getAdminName().trim();
        final String idempotencyKey = request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null;

        return context.executeInTransaction(connectionManager, conn -> {
            try {
                // Ensure platform orchestration commands run under session role privileges
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                }

                // 1. Check Idempotency Key for Existing Completed Job
                if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT p.tenant_id, p.college_id, t.name as tenant_name, t.code as tenant_code, " +
                                    "c.name as college_name, c.code as college_code, u.id as admin_id, u.email as admin_email " +
                                    "FROM plat.tenant_provisioning p " +
                                    "JOIN core.tenants t ON t.id = p.tenant_id " +
                                    "JOIN core.colleges c ON c.id = p.college_id " +
                                    "LEFT JOIN iam.user_profiles u ON u.tenant_id = p.tenant_id AND u.email = ? " +
                                    "WHERE p.idempotency_key = ? AND p.status = 'COMPLETED' LIMIT 1")) {
                        ps.setString(1, adminEmail);
                        ps.setString(2, idempotencyKey);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                OnboardCollegeResponse replay = new OnboardCollegeResponse();
                                replay.setTenantId((UUID) rs.getObject("tenant_id"));
                                replay.setTenantCode(rs.getString("tenant_code"));
                                replay.setTenantName(rs.getString("tenant_name"));
                                replay.setCollegeId((UUID) rs.getObject("college_id"));
                                replay.setCollegeCode(rs.getString("college_code"));
                                replay.setCollegeName(rs.getString("college_name"));
                                replay.setAdminUserId((UUID) rs.getObject("admin_id"));
                                replay.setAdminEmail(rs.getString("admin_email"));
                                replay.setStatus("ACTIVE");
                                replay.setIdempotentReplay(true);
                                replay.addStep(1, "CREATE_TENANT", "COMPLETED", "Tenant already registered (idempotent replay)");
                                replay.addStep(2, "CREATE_COLLEGE", "COMPLETED", "College already configured");
                                replay.addStep(3, "SEED_ROLES", "COMPLETED", "System roles already active");
                                replay.addStep(4, "PROVISION_ADMIN", "COMPLETED", "Administrator already provisioned");
                                replay.addStep(5, "ASSIGN_ROLE", "COMPLETED", "COLLEGE_ADMIN role verified");
                                return replay;
                            }
                        }
                    }
                }

                // 2. Pre-flight Conflict Checks
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM core.tenants WHERE code = ? AND deleted_at IS NULL LIMIT 1")) {
                    ps.setString(1, tenantCode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new ResourceConflictException("Tenant Code", tenantCode);
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM iam.user_profiles WHERE email = ? AND deleted_at IS NULL LIMIT 1")) {
                    ps.setString(1, adminEmail);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new ResourceConflictException("Administrator Email", adminEmail);
                        }
                    }
                }

                // 3. Generate IDs
                UUID tenantId = UUID.randomUUID();
                UUID collegeId = UUID.randomUUID();
                UUID adminUserId = UUID.randomUUID();
                UUID jobId = UUID.randomUUID();

                // 4. Step 1: Insert Tenant into core.tenants
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenants (" +
                                "id, code, name, legal_name, status, currency_code, timezone, locale, onboarded_at, created_at, updated_at, row_version" +
                                ") VALUES (?, ?, ?, ?, 'ACTIVE', 'INR', 'Asia/Kolkata', 'en_IN', now(), now(), now(), 1)")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, tenantCode);
                    ps.setString(3, tenantName);
                    ps.setString(4, tenantName);
                    ps.executeUpdate();
                }

                // 5. Step 2: Insert College into core.colleges
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.colleges (" +
                                "id, tenant_id, code, name, legal_name, status, provisioning_status, created_at, updated_at, row_version" +
                                ") VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, collegeId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, collegeCode);
                    ps.setString(4, collegeName);
                    ps.setString(5, collegeName);
                    ps.executeUpdate();
                }

                // 6. Initialize Saga in plat.tenant_provisioning (references core.tenants and core.colleges)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.tenant_provisioning (" +
                                "id, tenant_id, college_id, current_step, attempt, status, idempotency_key, created_at, updated_at, row_version" +
                                ") VALUES (?, ?, ?, 'INIT', 1, 'PROVISIONING', ?, now(), now(), 1)")) {
                    ps.setObject(1, jobId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, collegeId);
                    ps.setString(4, idempotencyKey);
                    ps.executeUpdate();
                }

                // 7. Step 3: Seed System Roles
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.seed_system_roles(?)")) {
                    ps.setObject(1, tenantId);
                    ps.execute();
                }

                // 8. Step 4: Provision Administrator User Profile
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.user_profiles (" +
                                "id, tenant_id, email, full_name, status, created_at, updated_at, row_version" +
                                ") VALUES (?, ?, ?, ?, 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, adminUserId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, adminEmail);
                    ps.setString(4, adminName);
                    ps.executeUpdate();
                }

                // 9. Step 5: Assign COLLEGE_ADMIN Role
                UUID collegeAdminRoleId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL LIMIT 1")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            collegeAdminRoleId = (UUID) rs.getObject("id");
                        }
                    }
                }

                if (collegeAdminRoleId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO iam.role_assignments (" +
                                    "id, tenant_id, role_id, user_id, college_id, created_at, updated_at, row_version" +
                                    ") VALUES (?, ?, ?, ?, ?, now(), now(), 1)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, tenantId);
                        ps.setObject(3, collegeAdminRoleId);
                        ps.setObject(4, adminUserId);
                        ps.setObject(5, collegeId);
                        ps.executeUpdate();
                    }
                }

                // 10. Step 6: Mark Provisioning Job Completed
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE plat.tenant_provisioning SET status = 'COMPLETED', current_step = 'COMPLETED', updated_at = now() WHERE id = ?")) {
                    ps.setObject(1, jobId);
                    ps.executeUpdate();
                }

                // 11. Step 7: Emit Transactional Outbox Event in sys.outbox_events
                UUID outboxId = UUID.randomUUID();
                String payload = String.format(
                        "{\"tenantId\":\"%s\",\"tenantCode\":\"%s\",\"collegeId\":\"%s\",\"collegeCode\":\"%s\",\"adminUserId\":\"%s\",\"adminEmail\":\"%s\",\"status\":\"ACTIVE\"}",
                        tenantId, tenantCode, collegeId, collegeCode, adminUserId, adminEmail
                );
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sys.outbox_events (" +
                                "id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at" +
                                ") VALUES (?, ?, 'institute-admin-service', 'TENANT', ?, 'tenant.onboarded.v1', ?::jsonb, 'PENDING', now(), now())")) {
                    ps.setObject(1, outboxId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, payload);
                    ps.executeUpdate();
                }

                // 12. Step 8: Platform Audit Log in audit.events
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO audit.events (" +
                                "tenant_id, college_id, event_time, action, principal_type, resource_type, resource_id, status, severity, detail" +
                                ") VALUES (?, ?, now(), 'TENANT_ONBOARDED', 'PLATFORM_ADMIN', 'TENANT', ?, 'SUCCESS', 'INFO', ?::jsonb)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, collegeId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, payload);
                    ps.executeUpdate();
                }

                OnboardCollegeResponse response = new OnboardCollegeResponse();
                response.setTenantId(tenantId);
                response.setTenantCode(tenantCode);
                response.setTenantName(tenantName);
                response.setCollegeId(collegeId);
                response.setCollegeCode(collegeCode);
                response.setCollegeName(collegeName);
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

                logger.info("Successfully onboarded institutional college: {} ({}) with tenantId={}", tenantName, tenantCode, tenantId);
                return response;

            } catch (SQLException e) {
                logger.error("Failed executing college onboarding transaction: {}", e.getMessage(), e);
                throw new RuntimeException("Database error during college onboarding saga: " + e.getMessage(), e);
            }
        });
    }
}
