package com.campx.iadm.lifecycle.auth;

import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supabase Auth Admin client (JDK HttpURLConnection only — no new libraries).
 * <p>
 * Plain language: the backend creates or invites a login; the browser never calls Auth Admin.
 * Credentials come from env {@code CAMPX_SUPABASE_URL} and {@code CAMPX_SUPABASE_SERVICE_ROLE_KEY}
 * (never logged or printed).
 * </p>
 */
public class SupabaseAuthAdminClient {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SupabaseAuthAdminClient.class);
    private static final Pattern UUID_IN_JSON = Pattern.compile(
            "\"id\"\\s*:\\s*\"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\"");

    private final String baseUrl;
    private final String serviceRoleKey;

    public SupabaseAuthAdminClient() {
        this(System.getenv("CAMPX_SUPABASE_URL"), System.getenv("CAMPX_SUPABASE_SERVICE_ROLE_KEY"));
    }

    public SupabaseAuthAdminClient(String baseUrl, String serviceRoleKey) {
        this.baseUrl = baseUrl == null ? null : baseUrl.trim().replaceAll("/+$", "");
        this.serviceRoleKey = serviceRoleKey == null ? null : serviceRoleKey.trim();
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isEmpty()
                && serviceRoleKey != null && !serviceRoleKey.isEmpty();
    }

    /**
     * Invite or create a login for {@code email}. If the user already exists, returns their id (no duplicate).
     */
    public Map<String, Object> inviteOrCreateUser(String email, String displayName) {
        if (!isConfigured()) {
            throw new InstituteAdminException(503, "IADM01_AUTH_ADMIN_UNAVAILABLE",
                    "Supabase Auth admin credential is not configured on the service. Ask the operator to set the approved server-side Auth admin environment variables.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "email is required");
        }
        String normalized = email.trim().toLowerCase();

        Map<String, Object> existing = findUserByEmail(normalized);
        if (existing != null && existing.get("userId") != null) {
            existing.put("created", Boolean.FALSE);
            existing.put("invitationStatus", "LINKED_EXISTING");
            return existing;
        }

        // Prefer invite (sends email via Supabase Auth)
        try {
            Map<String, Object> invited = inviteUser(normalized, displayName);
            invited.put("created", Boolean.TRUE);
            invited.put("invitationStatus", "SENT");
            return invited;
        } catch (InstituteAdminException inviteEx) {
            logger.warn("IADM-01 Auth invite failed code={} — attempting admin createUser", inviteEx.getErrorCode());
            Map<String, Object> created = createUser(normalized, displayName);
            created.put("created", Boolean.TRUE);
            created.put("invitationStatus", "CREATED");
            return created;
        }
    }

    private Map<String, Object> inviteUser(String email, String displayName) {
        String body = "{\"email\":\"" + escape(email) + "\""
                + (displayName == null || displayName.trim().isEmpty()
                ? ""
                : ",\"data\":{\"full_name\":\"" + escape(displayName.trim()) + "\"}")
                + "}";
        HttpResult result = postJson(baseUrl + "/auth/v1/invite", body);
        if (result.status >= 200 && result.status < 300) {
            UUID id = extractUuid(result.body);
            if (id == null) {
                throw new InstituteAdminException(502, "IADM01_AUTH_INVITE_PARSE",
                        "Invitation could not be sent. Retry invitation.");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", id.toString());
            out.put("email", email);
            return out;
        }
        if (result.status == 422 || result.body.toLowerCase().contains("already")) {
            Map<String, Object> existing = findUserByEmail(email);
            if (existing != null) {
                return existing;
            }
        }
        throw new InstituteAdminException(502, "IADM01_AUTH_INVITE_FAILED",
                "Invitation could not be sent. Retry invitation.");
    }

    private Map<String, Object> createUser(String email, String displayName) {
        String body = "{\"email\":\"" + escape(email) + "\",\"email_confirm\":false"
                + (displayName == null || displayName.trim().isEmpty()
                ? ""
                : ",\"user_metadata\":{\"full_name\":\"" + escape(displayName.trim()) + "\"}")
                + "}";
        HttpResult result = postJson(baseUrl + "/auth/v1/admin/users", body);
        if (result.status >= 200 && result.status < 300) {
            UUID id = extractUuid(result.body);
            if (id == null) {
                throw new InstituteAdminException(502, "IADM01_AUTH_CREATE_PARSE",
                        "Invitation could not be sent. Retry invitation.");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", id.toString());
            out.put("email", email);
            return out;
        }
        if (result.status == 422 || result.body.toLowerCase().contains("already")) {
            Map<String, Object> existing = findUserByEmail(email);
            if (existing != null) {
                existing.put("invitationStatus", "LINKED_EXISTING");
                return existing;
            }
        }
        throw new InstituteAdminException(502, "IADM01_AUTH_CREATE_FAILED",
                "Invitation could not be sent. Retry invitation.");
    }

    private Map<String, Object> findUserByEmail(String email) {
        // Admin list filter — never log the key or response PII beyond presence
        String url = baseUrl + "/auth/v1/admin/users?page=1&per_page=50&email=" + urlEncode(email);
        HttpResult result = getJson(url);
        if (result.status < 200 || result.status >= 300) {
            return null;
        }
        if (!result.body.toLowerCase().contains(email.toLowerCase())) {
            return null;
        }
        UUID id = extractUuid(result.body);
        if (id == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", id.toString());
        out.put("email", email);
        return out;
    }

    private HttpResult postJson(String url, String body) {
        return exchange("POST", url, body);
    }

    private HttpResult getJson(String url) {
        return exchange("GET", url, null);
    }

    private HttpResult exchange(String method, String url, String body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + serviceRoleKey);
            conn.setRequestProperty("apikey", serviceRoleKey);
            conn.setRequestProperty("Content-Type", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String responseBody = readFully(stream);
            return new HttpResult(status, responseBody == null ? "" : responseBody);
        } catch (InstituteAdminException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("IADM-01 Auth Admin HTTP failure method={} (credential not logged)", method);
            throw new InstituteAdminException(503, "IADM01_AUTH_ADMIN_UNAVAILABLE",
                    "Invitation could not be sent. Retry invitation.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readFully(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static UUID extractUuid(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = UUID_IN_JSON.matcher(json);
        if (m.find()) {
            return UUID.fromString(m.group(1));
        }
        return null;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
