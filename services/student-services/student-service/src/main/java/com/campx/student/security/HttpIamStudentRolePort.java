package com.campx.student.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP client for STM-07 student role assign/revoke against institute-admin IAM endpoints.
 */
public final class HttpIamStudentRolePort implements IamStudentRolePort {

    public static final String ASSIGN_PATH = "/api/v1/admin/internal/roles/student-assign";
    public static final String REVOKE_PATH = "/api/v1/admin/internal/roles/student-revoke";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(HttpIamStudentRolePort.class);

    private final String baseUrl;
    private final String hmacSecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpIamStudentRolePort(String baseUrl, String hmacSecret) {
        this(baseUrl, hmacSecret, 3000, 5000);
    }

    public HttpIamStudentRolePort(String baseUrl, String hmacSecret, int connectTimeoutMs, int readTimeoutMs) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("IAM student-role base URL is required");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.baseUrl = trimmed;
        this.hmacSecret = hmacSecret;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public boolean assignStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId, UUID collegeId) {
        if (tenantId == null || actorUserId == null || targetUserId == null) {
            return false;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"userId\":\"").append(targetUserId).append("\",\"roleCode\":\"R25\"");
        if (collegeId != null) {
            json.append(",\"collegeId\":\"").append(collegeId).append("\"");
        }
        json.append("}");
        return post(ASSIGN_PATH, tenantId, actorUserId, json.toString());
    }

    @Override
    public boolean revokeStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId) {
        if (tenantId == null || actorUserId == null || targetUserId == null) {
            return false;
        }
        String json = "{\"userId\":\"" + targetUserId + "\",\"roleCode\":\"R25\"}";
        return post(REVOKE_PATH, tenantId, actorUserId, json);
    }

    private boolean post(String path, UUID tenantId, UUID actorUserId, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            URL url = new URL(baseUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, actorUserId.toString());
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, tenantId.toString());
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ROLE, "SYSTEM_ADMIN");

            if (hmacSecret != null && !hmacSecret.trim().isEmpty()
                    && GatewayHmacSecurityConfig.isInternalAuthEnabled()) {
                String timestamp = Long.toString(System.currentTimeMillis());
                String bodySha = GatewayHmacProtocol.sha256Hex(bodyBytes);
                String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                        timestamp, "POST", path, actorUserId.toString(), tenantId.toString(), bodySha);
                String signature = GatewayHmacProtocol.calculateHmac(canonical, hmacSecret);
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);
            }

            conn.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = readFully(stream);
            if (status != 200) {
                logger.warn("IAM student-role call fail-closed status={} path={} tenantId={} body={}",
                        status, path, tenantId, body);
                return false;
            }
            return body != null && body.contains("\"success\":true");
        } catch (Exception e) {
            logger.warn("IAM student-role call unavailable (fail-closed): {}", e.getMessage());
            return false;
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
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int n;
        while ((n = in.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
