package com.campx.admissions.security;

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