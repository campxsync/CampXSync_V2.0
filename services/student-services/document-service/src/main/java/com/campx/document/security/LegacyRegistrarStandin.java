package com.campx.document.security;

/**
 * D12d temporary staging-only deviation: when ON, COLLEGE_ADMIN / INSTITUTION_ADMIN
 * may still satisfy Registrar checks. Default OFF. Expiry to be set by owner (first
 * real college onboarding). Never enable in production without owner approval.
 */
public final class LegacyRegistrarStandin {

    public static final String ENV_FLAG = "CAMPX_STM_LEGACY_REGISTRAR_STANDIN";
    public static final String SYSTEM_PROPERTY = "campx.stm.legacyRegistrarStandin";

    private LegacyRegistrarStandin() {
    }

    public static boolean isEnabled() {
        String env = System.getenv(ENV_FLAG);
        if (env != null && "true".equalsIgnoreCase(env.trim())) {
            return true;
        }
        String prop = System.getProperty(SYSTEM_PROPERTY);
        return prop != null && "true".equalsIgnoreCase(prop.trim());
    }
}
