package com.campx.iadm.ops.client;

import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal synchronous HTTP JSON client for owner-service contracts (US-021 / US-022).
 * Plain language: call another Java service on loopback with a freshly signed gateway HMAC
 * for the target path — never open its database.
 */
public final class HttpJsonClient {

    private final String baseUrl;
    private final String pathPrefix;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpJsonClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        String b = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.baseUrl = b;
        // Derive path prefix from base URL (e.g. .../api/v1/iadm/05)
        int idx = b.indexOf("/api/");
        this.pathPrefix = idx >= 0 ? b.substring(idx) : "";
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String get(String pathAndQuery, Map<String, String> headers) {
        return exchange("GET", pathAndQuery, null, headers);
    }

    public String post(String pathAndQuery, String body, Map<String, String> headers) {
        return exchange("POST", pathAndQuery, body, headers);
    }

    private String exchange(String method, String pathAndQuery, String body, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            String rel = pathAndQuery == null ? "" : pathAndQuery;
            if (!rel.startsWith("/")) {
                rel = "/" + rel;
            }
            URL url = new URL(baseUrl + rel);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Accept", "application/json");

            String userId = headers == null ? null : headers.get("X-User-Id");
            String tenantId = headers == null ? null : headers.get("X-Tenant-Id");
            if (tenantId == null) {
                tenantId = "";
            }
            String userRole = headers == null ? null : headers.get("X-User-Role");
            if (userId != null) {
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, userId);
            }
            if (tenantId != null) {
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, tenantId);
            }
            if (userRole != null) {
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ROLE, userRole);
            }

            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            // Sign for the target service path (query string excluded from HMAC path)
            String signPath = pathPrefix + rel.split("\\?", 2)[0];
            if (GatewayHmacSecurityConfig.isInternalAuthEnabled()) {
                String ts = String.valueOf(System.currentTimeMillis() / 1000L);
                String bodyHash = GatewayHmacProtocol.sha256Hex(bodyBytes);
                String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                        ts, method, signPath, userId == null ? "" : userId, tenantId, bodyHash);
                String sig = GatewayHmacProtocol.calculateHmac(canonical,
                        GatewayHmacSecurityConfig.getInternalSecret());
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, ts);
                conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, sig);
            }

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setFixedLengthStreamingMode(bodyBytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bodyBytes);
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = readFully(in);
            if (code >= 500) {
                throw new com.campx.iadm.ops.exception.InstituteAdminException(503, "IADM08_SOURCE_UNAVAILABLE",
                        "Analytics source is temporarily unavailable");
            }
            if (code == 404 || code == 409) {
                throw new com.campx.iadm.ops.exception.InstituteAdminException(code, "IADM08_POLICY_RESOLVE",
                        resp == null || resp.isEmpty() ? "Policy resolution failed" : resp);
            }
            if (code >= 400) {
                throw new com.campx.iadm.ops.exception.InstituteAdminException(code, "IADM08_SOURCE_ERROR",
                        resp == null || resp.isEmpty() ? "Source request failed" : resp);
            }
            return resp;
        } catch (com.campx.iadm.ops.exception.InstituteAdminException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new com.campx.iadm.ops.exception.InstituteAdminException(503, "IADM08_SOURCE_UNAVAILABLE",
                    "Analytics source is temporarily unavailable");
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
