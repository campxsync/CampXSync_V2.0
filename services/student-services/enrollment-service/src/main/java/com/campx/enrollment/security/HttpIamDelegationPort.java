package com.campx.enrollment.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Path C client — authoritative IAM active-delegation check (no cache). Fail closed on any error.
 */
public final class HttpIamDelegationPort implements DelegationPort {

    public static final String CHECK_PATH = "/api/v1/admin/internal/delegations/active-check";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(HttpIamDelegationPort.class);

    private final String baseUrl;
    private final String hmacSecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpIamDelegationPort(String baseUrl, String hmacSecret) {
        this(baseUrl, hmacSecret, 3000, 5000);
    }

    public HttpIamDelegationPort(String baseUrl, String hmacSecret, int connectTimeoutMs, int readTimeoutMs) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("IAM delegation base URL is required");
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
    public boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey) {
        if (tenantId == null || userId == null || permissionKey == null || permissionKey.trim().isEmpty()) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            String encodedUser = URLEncoder.encode(userId.toString(), "UTF-8");
            String encodedKey = URLEncoder.encode(permissionKey.trim().toUpperCase(Locale.ROOT), "UTF-8");
            String pathWithQuery = CHECK_PATH + "?userId=" + encodedUser + "&permissionKey=" + encodedKey;
            URL url = new URL(baseUrl + pathWithQuery);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, userId.toString());
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, tenantId.toString());
            conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ROLE, "REGISTRAR");

            byte[] emptyBody = new byte[0];
            if (hmacSecret != null && !hmacSecret.trim().isEmpty()
                    && GatewayHmacSecurityConfig.isInternalAuthEnabled()) {
                String timestamp = Long.toString(System.currentTimeMillis());
                String bodySha = GatewayHmacProtocol.sha256Hex(emptyBody);
                String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                        timestamp, "GET", CHECK_PATH, userId.toString(), tenantId.toString(), bodySha);
                String signature = GatewayHmacProtocol.calculateHmac(canonical, hmacSecret);
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = readFully(stream);
            if (status != 200) {
                logger.warn("IAM delegation check fail-closed status={} tenantId={} permissionKey={}",
                        status, tenantId, permissionKey.trim().toUpperCase(Locale.ROOT));
                return false;
            }
            boolean allowed = body != null && body.contains("\"allowed\":true");
            if (!allowed) {
                logger.info("IAM delegation check denied tenantId={} permissionKey={}",
                        tenantId, permissionKey.trim().toUpperCase(Locale.ROOT));
            }
            return allowed;
        } catch (Exception e) {
            logger.warn("IAM delegation check unavailable (fail-closed): {}", e.getMessage());
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
