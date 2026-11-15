package com.campx.admin.institute.service;

import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.repository.DelegationRepository;
import com.campx.admin.institute.repository.InMemoryDelegationRepository;
import com.campx.admin.institute.repository.PostgresDelegationRepository;
import com.campx.admin.institute.security.InMemoryRoleCodeLookup;
import com.campx.admin.institute.security.PostgresRoleCodeLookup;
import com.campx.admin.institute.security.RoleCodeLookup;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Path C M1 — central IAM evaluation of active REGISTRAR/R31 role delegation for STM {@code *.A} keys.
 * No caching. Fail closed on missing/invalid inputs or evaluation errors.
 */
public class DelegationAuthorizationService {

    public static final String PATH = "/api/v1/admin/internal/delegations/active-check";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DelegationAuthorizationService.class);

    private static final Set<String> STM_APPROVAL_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "STM-02.ADMISSIONS.A",
            "STM-03.ENROLMENT.A",
            "STM-04.LIFECYCLE.A",
            "STM-05.GUARDIAN.A",
            "STM-06.DOCUMENTS.A"
    )));

    private static final Set<String> REGISTRAR_ROLE_CODES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "R31",
            "REGISTRAR"
    )));

    private final DelegationRepository delegationRepository;
    private final RoleCodeLookup roleCodeLookup;
    private final ClockSource clock;

    public interface ClockSource {
        long nowMillis();
    }

    public DelegationAuthorizationService() {
        this(resolveDefaultDelegationRepository(), resolveDefaultRoleCodeLookup(), new ClockSource() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        });
    }

    public DelegationAuthorizationService(DelegationRepository delegationRepository,
                                          RoleCodeLookup roleCodeLookup) {
        this(delegationRepository, roleCodeLookup, new ClockSource() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        });
    }

    public DelegationAuthorizationService(DelegationRepository delegationRepository,
                                          RoleCodeLookup roleCodeLookup,
                                          ClockSource clock) {
        this.delegationRepository = delegationRepository == null
                ? resolveDefaultDelegationRepository() : delegationRepository;
        this.roleCodeLookup = roleCodeLookup == null
                ? resolveDefaultRoleCodeLookup() : roleCodeLookup;
        this.clock = clock == null ? new ClockSource() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        } : clock;
    }

    public DelegationRepository getDelegationRepository() {
        return delegationRepository;
    }

    public RoleCodeLookup getRoleCodeLookup() {
        return roleCodeLookup;
    }

    /**
     * Evaluates whether {@code subjectUserId} in {@code context.tenant} has an active M1 Registrar delegation
     * covering {@code permissionKey}.
     *
     * @param context       authenticated tenant context (HMAC-verified)
     * @param subjectUserId user whose delegation is checked; must equal context userId
     * @param permissionKey STM approval key (e.g. STM-06.DOCUMENTS.A)
     */
    public DelegationAuthorizationDecision checkActive(UserSecurityContext context,
                                                       String subjectUserId,
                                                       String permissionKey) {
        String tenantStr = context != null && context.getTenantId() != null
                ? context.getTenantId().toString() : "";
        String subject = subjectUserId == null ? "" : subjectUserId.trim();
        String key = permissionKey == null ? "" : permissionKey.trim().toUpperCase(Locale.ROOT);

        if (context == null || context.getTenantId() == null || context.getUserId() == null) {
            return deny(key, subject, tenantStr, "MISSING_SECURITY_CONTEXT");
        }
        if (subject.isEmpty()) {
            return deny(key, subject, tenantStr, "MISSING_SUBJECT_USER");
        }
        if (!subject.equalsIgnoreCase(context.getUserId().toString())) {
            // Prevent checking another user's delegation under a foreign HMAC identity.
            logger.warn("Delegation active-check denied: caller/subject mismatch tenant={}", tenantStr);
            return deny(key, subject, tenantStr, "CALLER_SUBJECT_MISMATCH");
        }
        if (key.isEmpty()) {
            return deny(key, subject, tenantStr, "MISSING_PERMISSION_KEY");
        }
        if (!STM_APPROVAL_KEYS.contains(key)) {
            return deny(key, subject, tenantStr, "UNAUTHORIZED_PERMISSION_KEY");
        }

        try {
            List<Delegation> rows = delegationRepository.listDelegationsForUser(context, subject);
            long now = clock.nowMillis();
            for (Delegation d : rows) {
                if (!isActiveWindow(d, now)) {
                    continue;
                }
                if (!subject.equals(d.getToUserId())) {
                    continue; // only delegated-to rows authorize A/R
                }
                Optional<String> roleCode = roleCodeLookup.findCodeByRoleId(context, d.getRoleId());
                if (!roleCode.isPresent()) {
                    continue;
                }
                if (REGISTRAR_ROLE_CODES.contains(roleCode.get())) {
                    logger.info("Delegation active-check ALLOWED tenant={} permissionKey={} reason=ACTIVE_R31_DELEGATION",
                            tenantStr, key);
                    return new DelegationAuthorizationDecision(true, "ACTIVE_R31_DELEGATION", key, subject, tenantStr);
                }
            }
            return deny(key, subject, tenantStr, "NO_ACTIVE_REGISTRAR_DELEGATION");
        } catch (RuntimeException e) {
            logger.warn("Delegation active-check failed closed tenant={} permissionKey={} err={}",
                    tenantStr, key, e.getMessage());
            return deny(key, subject, tenantStr, "IAM_EVALUATION_ERROR");
        }
    }

    private static boolean isActiveWindow(Delegation d, long now) {
        if (d == null) {
            return false;
        }
        if (d.getDeletedAt() != null) {
            return false;
        }
        String status = d.getStatus() == null ? "" : d.getStatus().trim().toUpperCase(Locale.ROOT);
        if ("REVOKED".equals(status) || "EXPIRED".equals(status)) {
            return false;
        }
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        if (d.getValidFrom() > now) {
            return false;
        }
        if (d.getValidTo() <= now) {
            return false;
        }
        return true;
    }

    private static DelegationAuthorizationDecision deny(String key, String subject, String tenant, String reason) {
        logger.info("Delegation active-check DENIED tenant={} permissionKey={} reason={}",
                tenant, key, reason);
        return new DelegationAuthorizationDecision(false, reason, key, subject, tenant);
    }

    private static boolean isPostgresMode() {
        String mode = System.getProperty("campx.persistence.mode");
        if ("postgres".equalsIgnoreCase(mode) || "true".equalsIgnoreCase(System.getProperty("campx.db.enabled"))) {
            return true;
        }
        return "postgres".equalsIgnoreCase(System.getenv("CAMPX_PERSISTENCE_MODE"));
    }

    private static DelegationRepository resolveDefaultDelegationRepository() {
        if (isPostgresMode()) {
            return new PostgresDelegationRepository();
        }
        return new InMemoryDelegationRepository();
    }

    private static RoleCodeLookup resolveDefaultRoleCodeLookup() {
        if (isPostgresMode()) {
            return new PostgresRoleCodeLookup();
        }
        return new InMemoryRoleCodeLookup();
    }
}
