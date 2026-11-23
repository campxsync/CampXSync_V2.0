package com.campx.iadm.lifecycle.config;

import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-02-US-026 — IADM-01 reads platform defaults through the IADM-02 consumer contract.
 * No local duplicate catalogue. Fail closed when the contract is unavailable or mandatory
 * keys are missing. Cache is bounded by {@link #FRESHNESS_WINDOW_SECONDS} (D8).
 */
public class Iadm02PlatformDefaultsClient {

    public static final int FRESHNESS_WINDOW_SECONDS = 300;

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm02PlatformDefaultsClient.class);

    private final String gatewayBase;
    private final String supabaseUrl;
    private final String anonKey;
    private final String consumerEmail;
    private final String consumerPassword;

    private volatile CacheEntry cache;

    public Iadm02PlatformDefaultsClient() {
        Map<String, String> seed = loadDevSeed();
        Map<String, String> envFile = loadEnvFile();
        String gw = firstNonEmpty(System.getenv("CAMPX_GATEWAY_BASE"), "http://127.0.0.1:8080");
        this.gatewayBase = (gw != null ? gw : "http://127.0.0.1:8080").replaceAll("/$", "");
        this.supabaseUrl = firstNonEmpty(System.getenv("CAMPX_SUPABASE_URL"), envFile.get("CAMPX_SUPABASE_URL"));
        this.anonKey = firstNonEmpty(System.getenv("NEXT_PUBLIC_SUPABASE_ANON_KEY"),
                firstNonEmpty(System.getenv("SUPABASE_ANON_KEY"),
                        envFile.get("NEXT_PUBLIC_SUPABASE_ANON_KEY")));
        this.consumerEmail = firstNonEmpty(System.getenv("IADM02_CONSUMER_EMAIL"),
                firstNonEmpty(seed.get("email"), "iadm02.consumer.dev@campx.local"));
        this.consumerPassword = firstNonEmpty(System.getenv("IADM02_CONSUMER_PASSWORD"), seed.get("password"));
    }

    public Iadm02PlatformDefaultsClient(String gatewayBase, String supabaseUrl, String anonKey,
                                        String consumerEmail, String consumerPassword) {
        this.gatewayBase = gatewayBase != null ? gatewayBase.replaceAll("/$", "") : "http://127.0.0.1:8080";
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
        this.consumerEmail = consumerEmail;
        this.consumerPassword = consumerPassword;
    }

    /**
     * Returns effective platform defaults for onboarding (security + iam domains).
     * Includes {@code iam.delegation.max_validity_days} when granted (IADM-03 prep).
     */
    public Map<String, Object> readEffectiveDefaults() {
        CacheEntry hit = cache;
        if (hit != null && !hit.isStale()) {
            Map<String, Object> out = new LinkedHashMap<String, Object>(hit.payload);
            out.put("cacheStatus", "FRESH");
            return out;
        }
        if (hit != null && hit.isStale()) {
            // D8: do not invent values; attempt refresh. On failure return explicit STALE and fail closed
            // for mandatory consumers (caller decides). We still surface last-known only with STALE flag.
            try {
                return fetchAndCache();
            } catch (InstituteAdminException ex) {
                Map<String, Object> stale = new LinkedHashMap<String, Object>(hit.payload);
                stale.put("cacheStatus", "STALE");
                stale.put("refreshError", ex.getErrorCode());
                return stale;
            }
        }
        return fetchAndCache();
    }

    /** Fail closed for a mandatory key: throws UNAVAILABLE / missing rather than inventing a default. */
    public String requireKey(String key) {
        Map<String, Object> bundle = readEffectiveDefaults();
        if ("STALE".equals(String.valueOf(bundle.get("cacheStatus")))
                && bundle.get("refreshError") != null) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_STALE",
                    "Platform defaults cache is stale and refresh failed; fail closed.");
        }
        if ("UNAVAILABLE".equals(String.valueOf(bundle.get("cacheStatus")))) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                    "Platform defaults are unavailable.");
        }
        Object keysObj = bundle.get("keys");
        if (!(keysObj instanceof Map)) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                    "Platform defaults payload missing keys.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> keys = (Map<String, Object>) keysObj;
        Object val = keys.get(key);
        if (val == null) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_MISSING",
                    "Mandatory platform default '" + key + "' is not present.");
        }
        return String.valueOf(val);
    }

    private Map<String, Object> fetchAndCache() {
        if (supabaseUrl == null || anonKey == null || consumerPassword == null || consumerPassword.isEmpty()) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                    "IADM-02 consumer credentials are not configured.");
        }
        String token = passwordGrant();
        String path = gatewayBase + "/api/v1/iadm/02/consumer/effective-config?domains=security,attendance,iam";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(path).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            String body = readBody(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status == 503 || body.contains("IADM02_CONFIG_UNAVAILABLE")) {
                throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                        "IADM-02 reported no published configuration.");
            }
            if (status != 200) {
                throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                        "IADM-02 consumer read failed with HTTP " + status);
            }
            Map<String, Object> parsed = parseConsumerJson(body);
            parsed.put("cacheStatus", "FRESH");
            parsed.put("freshnessWindowSeconds", Integer.valueOf(FRESHNESS_WINDOW_SECONDS));
            cache = new CacheEntry(parsed, Instant.now());
            return new LinkedHashMap<String, Object>(parsed);
        } catch (InstituteAdminException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("IADM-02 platform defaults fetch failed: {}", e.getMessage());
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                    "Unable to reach IADM-02 consumer contract.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String passwordGrant() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(supabaseUrl.replaceAll("/$", "") + "/auth/v1/token?grant_type=password");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("apikey", anonKey);
            conn.setRequestProperty("Content-Type", "application/json");
            String payload = "{\"email\":\"" + escape(consumerEmail) + "\",\"password\":\""
                    + escape(consumerPassword) + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            String body = readBody(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status != 200) {
                throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                        "Consumer auth failed.");
            }
            String token = extractJsonString(body, "access_token");
            if (token == null || token.isEmpty()) {
                throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                        "Consumer auth returned no access_token.");
            }
            return token;
        } catch (InstituteAdminException e) {
            throw e;
        } catch (Exception e) {
            throw new InstituteAdminException(503, "IADM01_PLATFORM_DEFAULTS_UNAVAILABLE",
                    "Consumer auth transport failed.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseConsumerJson(String body) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("schemaVersion", extractJsonInt(body, "schemaVersion"));
        out.put("configVersionId", extractJsonString(body, "configVersionId"));
        out.put("configVersionLabel", extractJsonString(body, "configVersionLabel"));
        out.put("fetchedAt", extractJsonString(body, "fetchedAt"));
        out.put("consumerId", extractJsonString(body, "consumerId"));
        out.put("consumerName", extractJsonString(body, "consumerName"));
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        int keysIdx = body.indexOf("\"keys\"");
        if (keysIdx >= 0) {
            int lb = body.indexOf('{', keysIdx);
            int rb = findMatchingBrace(body, lb);
            if (lb >= 0 && rb > lb) {
                String inner = body.substring(lb + 1, rb);
                // naive "k":"v" / "k":n pairs
                int i = 0;
                while (i < inner.length()) {
                    int q1 = inner.indexOf('"', i);
                    if (q1 < 0) {
                        break;
                    }
                    int q2 = inner.indexOf('"', q1 + 1);
                    if (q2 < 0) {
                        break;
                    }
                    String k = inner.substring(q1 + 1, q2);
                    int colon = inner.indexOf(':', q2);
                    if (colon < 0) {
                        break;
                    }
                    int p = colon + 1;
                    while (p < inner.length() && Character.isWhitespace(inner.charAt(p))) {
                        p++;
                    }
                    if (p < inner.length() && inner.charAt(p) == '"') {
                        int e = inner.indexOf('"', p + 1);
                        keys.put(k, inner.substring(p + 1, e));
                        i = e + 1;
                    } else {
                        int e = p;
                        while (e < inner.length() && ",}".indexOf(inner.charAt(e)) < 0) {
                            e++;
                        }
                        keys.put(k, inner.substring(p, e).trim());
                        i = e;
                    }
                }
            }
        }
        out.put("keys", keys.isEmpty() ? Collections.emptyMap() : keys);
        return out;
    }

    private static int findMatchingBrace(String s, int open) {
        if (open < 0 || open >= s.length() || s.charAt(open) != '{') {
            return -1;
        }
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return json.substring(q1 + 1, q2);
    }

    private static Integer extractJsonInt(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        int e = p;
        while (e < json.length() && Character.isDigit(json.charAt(e))) {
            e++;
        }
        if (e == p) {
            return null;
        }
        try {
            return Integer.valueOf(json.substring(p, e));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String readBody(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    /** DEV-only: resolve repo root then read consumer seed (never logged). */
    private static Map<String, String> loadDevSeed() {
        Map<String, String> out = new HashMap<String, String>();
        try {
            Path seed = resolveRepoFile("docs/iadm/iadm-02/evidence/.local/seed_accounts.json");
            if (seed == null || !Files.isRegularFile(seed)) {
                return out;
            }
            String json = new String(Files.readAllBytes(seed), StandardCharsets.UTF_8);
            String email = extractNested(json, "consumer", "email");
            String password = extractNested(json, "consumer", "password");
            if (email != null) {
                out.put("email", email);
            }
            if (password != null) {
                out.put("password", password);
            }
        } catch (Exception ignored) {
            /* optional DEV helper */
        }
        return out;
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> out = new HashMap<String, String>();
        try {
            Path[] files = new Path[]{
                    resolveRepoFile("frontend/.env.local"),
                    resolveRepoFile(".env.audit")
            };
            for (Path f : files) {
                if (f == null || !Files.isRegularFile(f)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if (!out.containsKey(k)) {
                        out.put(k, v);
                    }
                }
            }
        } catch (Exception ignored) {
            /* optional */
        }
        return out;
    }

    private static Path resolveRepoFile(String relative) {
        String rootEnv = System.getenv("CAMPX_REPO_ROOT");
        if (rootEnv != null && !rootEnv.trim().isEmpty()) {
            Path p = Paths.get(rootEnv.trim(), relative);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] bases = new Path[]{
                cwd,
                cwd.getParent() != null ? cwd.getParent() : cwd,
                cwd.getParent() != null && cwd.getParent().getParent() != null
                        ? cwd.getParent().getParent() : cwd,
                Paths.get("D:\\CampXSync\\CampXSync_V2.0")
        };
        for (Path base : bases) {
            if (base == null) {
                continue;
            }
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static String extractNested(String json, String parent, String field) {
        int p = json.indexOf("\"" + parent + "\"");
        if (p < 0) {
            return null;
        }
        int brace = json.indexOf('{', p);
        int end = findMatchingBrace(json, brace);
        if (brace < 0 || end < 0) {
            return null;
        }
        return extractJsonString(json.substring(brace, end + 1), field);
    }

    private static final class CacheEntry {
        final Map<String, Object> payload;
        final Instant fetchedAt;

        CacheEntry(Map<String, Object> payload, Instant fetchedAt) {
            this.payload = payload;
            this.fetchedAt = fetchedAt;
        }

        boolean isStale() {
            return Instant.now().isAfter(fetchedAt.plusSeconds(FRESHNESS_WINDOW_SECONDS));
        }
    }
}
