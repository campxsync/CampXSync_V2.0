package com.campx.document.storage;

/**
 * Owner-approved config keys (D11): CAMPX_SUPABASE_URL, CAMPX_SUPABASE_SERVICE_ROLE_KEY,
 * CAMPX_STORAGE_BUCKET (default student-documents). Never log key values.
 */
public final class DocumentStorageConfig {

    public static final String ENV_SUPABASE_URL = "CAMPX_SUPABASE_URL";
    public static final String ENV_SERVICE_ROLE_KEY = "CAMPX_SUPABASE_SERVICE_ROLE_KEY";
    public static final String ENV_STORAGE_BUCKET = "CAMPX_STORAGE_BUCKET";
    public static final String DEFAULT_BUCKET = "student-documents";

    public final String supabaseUrl;
    public final String serviceRoleKey;
    public final String bucket;
    public final boolean configured;

    private DocumentStorageConfig(String supabaseUrl, String serviceRoleKey, String bucket, boolean configured) {
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.bucket = bucket;
        this.configured = configured;
    }

    public static DocumentStorageConfig fromEnvironment() {
        String url = trimToNull(System.getenv(ENV_SUPABASE_URL));
        String key = trimToNull(System.getenv(ENV_SERVICE_ROLE_KEY));
        String bucket = trimToNull(System.getenv(ENV_STORAGE_BUCKET));
        if (bucket == null) {
            bucket = DEFAULT_BUCKET;
        }
        boolean configured = url != null && key != null;
        return new DocumentStorageConfig(url, key, bucket, configured);
    }

    public static DocumentStorageConfig unconfigured() {
        return new DocumentStorageConfig(null, null, DEFAULT_BUCKET, false);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
