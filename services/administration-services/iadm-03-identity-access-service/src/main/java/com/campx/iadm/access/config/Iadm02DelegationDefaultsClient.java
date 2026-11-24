package com.campx.iadm.access.config;

import com.campx.iadm.access.exception.InstituteAdminException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-03-US-029 — read {@code iam.delegation.max_validity_days} from IADM-02 consumer contract.
 * Never hard-codes 90. Fail closed when unavailable.
 */
public class Iadm02DelegationDefaultsClient {

    public static final String KEY = "iam.delegation.max_validity_days";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm02DelegationDefaultsClient.class);

    private final String gatewayBase;
    private final String supabaseUrl;
    private final String anonKey;
    private final String consumerEmail;
    private final String consumerPassword;

    public Iadm02DelegationDefaultsClient() {
        Map<String, String> env = loadEnvFile();
        Map<String, String> seed = loadDevSeed();
        String gw = firstNonEmpty(System.getenv("CAMPX_GATEWAY_BASE"), "http://127.0.0.1:8080");
        this.gatewayBase = gw.replaceAll("/$", "");
        this.supabaseUrl = firstNonEmpty(System.getenv("CAMPX_SUPABASE_URL"), env.get("CAMPX_SUPABASE_URL"));
        this.anonKey = firstNonEmpty(System.getenv("NEXT_PUBLIC_SUPABASE_ANON_KEY"),
                firstNonEmpty(env.get("NEXT_PUBLIC_SUPABASE_ANON_KEY"), env.get("CAMPX_SUPABASE_ANON_KEY")));
        this.consumerEmail = firstNonEmpty(System.getenv("IADM02_CONSUMER_EMAIL"),
                firstNonEmpty(seed.get("email"), "iadm02.consumer.dev@campx.local"));
        this.consumerPassword = firstNonEmpty(System.getenv("IADM02_CONSUMER_PASSWORD"), seed.get("password"));
    }

    /** Days (1–365). Throws 503 if IADM-02 cannot supply the key. */
    public int requireMaxValidityDays() {
        String raw = requireKey(KEY);
        try {
            int days = Integer.parseInt(raw.trim());
            if (days < 1 || days > 365) {
                throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_INVALID",
                        "Platform default " + KEY + " is out of range.");
            }
            return days;
        } catch (NumberFormatException ex) {
            throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_INVALID",
                    "Platform default " + KEY + " is not an integer.");
        }
    }

    private String requireKey(String key) {
        if (supabaseUrl == null || anonKey == null || consumerPassword == null || consumerPassword.isEmpty()) {
            throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                    "IADM-02 consumer credentials are not configured; delegation refused (fail closed).");
        }
        String token = passwordGrant();
        String path = gatewayBase + "/api/v1/iadm/02/consumer/effective-config?domains=iam,security";
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
            if (status != 200) {
                throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                        "IADM-02 consumer read failed; delegation refused (fail closed).");
            }
            String value = extractKeyValue(body, key);
            if (value == null || value.trim().isEmpty()) {
                throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_MISSING",
                        "Mandatory platform default '" + key + "' is not present; delegation refused.");
            }
            return value.trim();
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("IADM-02 delegation default fetch failed: {}", ex.getMessage());
            throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                    "Unable to reach IADM-02 consumer contract; delegation refused (fail closed).");
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
                throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                        "IADM-02 consumer auth failed; delegation refused.");
            }
            String token = extractJsonString(body, "access_token");
            if (token == null || token.isEmpty()) {
                throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                        "IADM-02 consumer auth returned no token.");
            }
            return token;
        } catch (InstituteAdminException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InstituteAdminException(503, "IADM03_DELEGATION_DEFAULT_UNAVAILABLE",
                    "IADM-02 consumer auth transport failed.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String extractKeyValue(String body, String key) {
        String needle = "\"" + key + "\"";
        int i = body.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = body.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int p = colon + 1;
        while (p < body.length() && Character.isWhitespace(body.charAt(p))) {
            p++;
        }
        if (p < body.length() && body.charAt(p) == '"') {
            int e = body.indexOf('"', p + 1);
            return e > p ? body.substring(p + 1, e) : null;
        }
        int e = p;
        while (e < body.length() && ",}".indexOf(body.charAt(e)) < 0) {
            e++;
        }
        return body.substring(p, e).trim();
    }

    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return null;
        }
        return json.substring(q1 + 1, q2);
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
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private static Map<String, String> loadDevSeed() {
        Map<String, String> out = new HashMap<String, String>();
        try {
            Path seed = resolveRepoFile("docs/iadm/iadm-02/evidence/.local/seed_accounts.json");
            if (seed == null || !Files.isRegularFile(seed)) {
                return out;
            }
            String json = new String(Files.readAllBytes(seed), StandardCharsets.UTF_8);
            int p = json.indexOf("\"consumer\"");
            if (p < 0) {
                return out;
            }
            int brace = json.indexOf('{', p);
            int end = json.indexOf('}', brace);
            if (brace < 0 || end < 0) {
                return out;
            }
            String block = json.substring(brace, end + 1);
            String email = extractJsonString(block, "email");
            String password = extractJsonString(block, "password");
            if (email != null) {
                out.put("email", email);
            }
            if (password != null) {
                out.put("password", password);
            }
        } catch (Exception ignored) {
            /* optional */
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
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] bases = new Path[]{
                cwd,
                cwd.getParent() != null ? cwd.getParent() : cwd,
                Paths.get("D:\\CampXSync\\CampXSync_V2.0")
        };
        for (Path base : bases) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
