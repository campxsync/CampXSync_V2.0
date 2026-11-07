package com.campx.enrollment.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Safety guard for STM-03 live PostgreSQL tests. Only azulbaxszsqxymqmlwfe is permitted.
 */
public final class TestEnvironmentGuard {

    public static final String APPROVED_DEV_PROJECT_REF = "azulbaxszsqxymqmlwfe";
    public static final String PRODUCTION_PROJECT_REF = "ekspahbsbwdpraaqfqui";
    public static final String PROHIBITED_LEGACY_STAGING_REF = "kpwdvvcschankgggitnj";

    private TestEnvironmentGuard() {
    }

    public static void validateStagingConfiguration(String jdbcUrl, String username) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("Missing JDBC URL for live STM-03 tests");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Missing DB username for live STM-03 tests");
        }
        String trimmedUrl = jdbcUrl.trim();
        String trimmedUser = username.trim();
        if (trimmedUrl.contains(PRODUCTION_PROJECT_REF) || trimmedUser.contains(PRODUCTION_PROJECT_REF)) {
            throw new IllegalStateException("Production project ref is forbidden");
        }
        if (trimmedUrl.contains(PROHIBITED_LEGACY_STAGING_REF) || trimmedUser.contains(PROHIBITED_LEGACY_STAGING_REF)) {
            throw new IllegalStateException("Legacy staging project ref is forbidden");
        }
        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Malformed PostgreSQL JDBC URL");
        }
        String host;
        try {
            host = new URI(trimmedUrl.substring(5)).getHost();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Malformed JDBC URL: " + e.getReason());
        }
        if (host == null) {
            throw new IllegalStateException("Unable to extract JDBC host");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || "127.0.0.1".equals(lowerHost) || lowerHost.startsWith("127.")) {
            throw new IllegalStateException("Localhost JDBC targets are forbidden for live tests");
        }
        if (lowerHost.endsWith(".supabase.co")) {
            String prefix = lowerHost.substring(0, lowerHost.length() - ".supabase.co".length());
            if (prefix.startsWith("db.")) {
                prefix = prefix.substring(3);
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException("Unauthorized Supabase project host: " + prefix);
            }
            return;
        }
        if (lowerHost.contains("pooler.supabase.")) {
            int dotIdx = trimmedUser.lastIndexOf('.');
            if (dotIdx <= 0) {
                throw new IllegalStateException("Pooler username must be role.projectRef");
            }
            String userProjectRef = trimmedUser.substring(dotIdx + 1);
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException("Unauthorized pooler project ref: " + userProjectRef);
            }
            return;
        }
        throw new IllegalStateException("Unrecognized JDBC host for live tests: " + host);
    }
}
