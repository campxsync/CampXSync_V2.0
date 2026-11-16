package com.campx.student.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Resolves live vs fail-closed {@link IamStudentRolePort}.
 * Reuses {@code CAMPX_IAM_DELEGATION_BASE_URL} (institute-admin IAM base).
 */
public final class IamStudentRolePortFactory {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(IamStudentRolePortFactory.class);

    private IamStudentRolePortFactory() {
    }

    public static IamStudentRolePort fromEnvironment() {
        String base = System.getenv(DelegationPortFactory.ENV_BASE_URL);
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("campx.iam.delegation.baseUrl");
        }
        if (base == null || base.trim().isEmpty()) {
            logger.info("IAM student-role base URL unset; using FailClosedIamStudentRolePort");
            return new FailClosedIamStudentRolePort();
        }
        String secret = System.getenv("CAMPX_INTERNAL_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv("CAMPX_GATEWAY_HMAC_SECRET");
        }
        logger.info("IAM student-role Path C client enabled baseUrl={}", base.trim());
        return new HttpIamStudentRolePort(base.trim(), secret);
    }
}
