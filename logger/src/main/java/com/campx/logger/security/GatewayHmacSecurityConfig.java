package com.campx.logger.security;

/**
 * Configuration resolver for platform internal request authentication and Supabase JWT verification.
 * <p>
 * Follows strict security requirements:
 * <ul>
 *   <li>Internal authentication is ENABLED by default.</li>
 *   <li><b>NO default or hardcoded secrets</b>: {@code CAMPX_INTERNAL_SECRET} and {@code SUPABASE_JWT_SECRET}
 *       must be explicitly provided via environment variables or JVM system properties.</li>
 *   <li>If a required secret is missing when security is enabled, startup fails fast with a descriptive
 *       {@link IllegalStateException}.</li>
 * </ul>
 * </p>
 */
public final class GatewayHmacSecurityConfig {

    public static final String PROP_INTERNAL_SECRET = "campx.internal.secret";
    public static final String ENV_INTERNAL_SECRET = "CAMPX_INTERNAL_SECRET";

    public static final String PROP_INTERNAL_AUTH_ENABLED = "campx.internal.auth.enabled";
    public static final String ENV_INTERNAL_AUTH_ENABLED = "CAMPX_INTERNAL_AUTH_ENABLED";

    public static final String PROP_REPLAY_WINDOW = "campx.internal.replay.window.seconds";
    public static final String ENV_REPLAY_WINDOW = "CAMPX_INTERNAL_REPLAY_WINDOW_SECONDS";

    public static final String PROP_BIND_HOST = "campx.bind.host";
    public static final String ENV_BIND_HOST = "CAMPX_BIND_HOST";

    public static final String PROP_SUPABASE_JWT_SECRET = "campx.supabase.jwt.secret";
    public static final String ENV_SUPABASE_JWT_SECRET = "CAMPX_SUPABASE_JWT_SECRET";
    public static final String ENV_SUPABASE_JWT_SECRET_LEGACY = "SUPABASE_JWT_SECRET";

    public static final String PROP_SUPABASE_JWT_ISSUER = "campx.supabase.jwt.issuer";
    public static final String ENV_SUPABASE_JWT_ISSUER = "CAMPX_SUPABASE_JWT_ISSUER";
    public static final String ENV_SUPABASE_JWT_ISSUER_LEGACY = "SUPABASE_JWT_ISSUER";

    public static final long DEFAULT_REPLAY_WINDOW_SECONDS = 60L;
    public static final String DEFAULT_BIND_HOST = "127.0.0.1";

    private GatewayHmacSecurityConfig() {}

    /**
     * Determines whether platform internal Gateway -> downstream request authentication is enabled.
     * Enabled by default unless explicitly disabled via system property or environment variable.
     */
    public static boolean isInternalAuthEnabled() {
        String prop = System.getProperty(PROP_INTERNAL_AUTH_ENABLED);
        if (prop != null && !prop.trim().isEmpty()) {
            return "true".equalsIgnoreCase(prop.trim());
        }
        String env = System.getenv(ENV_INTERNAL_AUTH_ENABLED);
        if (env != null && !env.trim().isEmpty()) {
            return "true".equalsIgnoreCase(env.trim());
        }
        return true;
    }

    /**
     * Resolves the internal shared secret used for HMAC-SHA256 request authentication.
     *
     * @return resolved secret string
     * @throws IllegalStateException if internal authentication is enabled and no secret is configured
     */
    public static String getInternalSecret() {
        String secret = System.getProperty(PROP_INTERNAL_SECRET);
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }
        secret = System.getenv(ENV_INTERNAL_SECRET);
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }

        if (isInternalAuthEnabled()) {
            throw new IllegalStateException("CAMPX_INTERNAL_SECRET is required when platform internal auth is enabled. "
                    + "Set environment variable CAMPX_INTERNAL_SECRET or JVM system property campx.internal.secret.");
        }
        return null;
    }

    /**
     * Resolves the maximum allowed replay window in seconds (default: 60 seconds).
     */
    public static long getReplayWindowSeconds() {
        String prop = System.getProperty(PROP_REPLAY_WINDOW);
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                return Long.parseLong(prop.trim());
            } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv(ENV_REPLAY_WINDOW);
        if (env != null && !env.trim().isEmpty()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_REPLAY_WINDOW_SECONDS;
    }

    /**
     * Resolves the downstream HTTP service bind address (default: 127.0.0.1 for local dev).
     */
    public static String getBindHost() {
        String prop = System.getProperty(PROP_BIND_HOST);
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        String env = System.getenv(ENV_BIND_HOST);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return DEFAULT_BIND_HOST;
    }

    public static final String PROP_SUPABASE_JWKS_URL = "campx.supabase.jwks.url";
    public static final String ENV_SUPABASE_JWKS_URL = "CAMPX_SUPABASE_JWKS_URL";

    /**
     * Resolves the Supabase JWT secret. Optional for current ES256/JWKS asymmetric verification.
     *
     * @return resolved Supabase JWT secret, or {@code null} if not configured
     */
    public static String getSupabaseJwtSecret() {
        String secret = System.getProperty(PROP_SUPABASE_JWT_SECRET);
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }
        secret = System.getenv(ENV_SUPABASE_JWT_SECRET);
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }
        secret = System.getenv(ENV_SUPABASE_JWT_SECRET_LEGACY);
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }
        return null;
    }

    /**
     * Resolves the Supabase JWKS URL, defaulting to deriving it from the configured issuer.
     *
     * @param issuer Supabase project issuer URL
     * @return resolved JWKS URL or {@code null}
     */
    public static String getSupabaseJwksUrl(String issuer) {
        String prop = System.getProperty(PROP_SUPABASE_JWKS_URL);
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        String env = System.getenv(ENV_SUPABASE_JWKS_URL);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        if (issuer != null && !issuer.trim().isEmpty()) {
            String clean = issuer.trim().replaceAll("/+$", "");
            return clean + "/.well-known/jwks.json";
        }
        return null;
    }

    /**
     * Resolves the expected Supabase JWT issuer (e.g. https://<project-ref>.supabase.co/auth/v1).
     *
     * @return resolved issuer string
     * @throws IllegalStateException if Gateway security is enabled and no issuer is configured
     */
    public static String getSupabaseJwtIssuer() {
        String issuer = System.getProperty(PROP_SUPABASE_JWT_ISSUER);
        if (issuer != null && !issuer.trim().isEmpty()) {
            return issuer.trim();
        }
        issuer = System.getenv(ENV_SUPABASE_JWT_ISSUER);
        if (issuer != null && !issuer.trim().isEmpty()) {
            return issuer.trim();
        }
        issuer = System.getenv(ENV_SUPABASE_JWT_ISSUER_LEGACY);
        if (issuer != null && !issuer.trim().isEmpty()) {
            return issuer.trim();
        }

        if (isInternalAuthEnabled()) {
            throw new IllegalStateException("CAMPX_SUPABASE_JWT_ISSUER is required when Gateway security is enabled. "
                    + "Set environment variable CAMPX_SUPABASE_JWT_ISSUER or JVM system property campx.supabase.jwt.issuer.");
        }
        return null;
    }
}
