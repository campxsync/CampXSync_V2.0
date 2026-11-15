package com.campx.document.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Resolves live vs fail-closed {@link DelegationPort}.
 * Live {@link HttpIamDelegationPort} is used only when {@code CAMPX_IAM_DELEGATION_BASE_URL} is set.
 */
public final class DelegationPortFactory {

    public static final String ENV_BASE_URL = "CAMPX_IAM_DELEGATION_BASE_URL";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DelegationPortFactory.class);

    private DelegationPortFactory() {
    }

    public static DelegationPort fromEnvironment() {
        String base = System.getenv(ENV_BASE_URL);
        if (base == null || base.trim().isEmpty()) {
            base = System.getProperty("campx.iam.delegation.baseUrl");
        }
        if (base == null || base.trim().isEmpty()) {
            logger.info("IAM delegation base URL unset; using FailClosedDelegationPort");
            return new FailClosedDelegationPort();
        }
        String secret = System.getenv("CAMPX_INTERNAL_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getenv("CAMPX_GATEWAY_HMAC_SECRET");
        }
        logger.info("IAM delegation Path C client enabled baseUrl={}", base.trim());
        return new HttpIamDelegationPort(base.trim(), secret);
    }
}
