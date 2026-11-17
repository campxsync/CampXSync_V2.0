package com.campx.lifecycle.integration;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Best-effort PATCH of student status on student-service so STM-07 revoke runs.
 * Requires {@code STM01_SERVICE_URL} or {@code STUDENT_SERVICE_URL}. Fail-soft on errors.
 */
public final class HttpStudentAccountAccessPort implements StudentAccountAccessPort {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(HttpStudentAccountAccessPort.class);

    private final String baseUrl;

    public HttpStudentAccountAccessPort(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public static StudentAccountAccessPort fromEnvironment() {
        String base = System.getenv("STM01_SERVICE_URL");
        if (base == null || base.trim().isEmpty()) {
            base = System.getenv("STUDENT_SERVICE_URL");
        }
        if (base == null || base.trim().isEmpty()) {
            return new NoOpStudentAccountAccessPort();
        }
        return new HttpStudentAccountAccessPort(base.trim());
    }

    @Override
    public void onAccessCuttingStatus(UUID tenantId, UUID studentId, String newStatus, String correlationId) {
        if (baseUrl.isEmpty() || tenantId == null || studentId == null || newStatus == null) {
            return;
        }
        String norm = newStatus.trim().toUpperCase(Locale.ROOT);
        if (!("SUSPENDED".equals(norm) || "DETAINED".equals(norm) || "WITHDRAWN".equals(norm))) {
            return;
        }
        try {
            URL url = new URL(baseUrl + "/api/v1/students/" + studentId + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Tenant-Id", tenantId.toString());
            conn.setRequestProperty("X-User-Id", "00000000-0000-0000-0000-000000000001");
            conn.setRequestProperty("X-User-Role", "SYSTEM_ADMIN");
            if (correlationId != null && !correlationId.trim().isEmpty()) {
                conn.setRequestProperty("X-Correlation-Id", correlationId);
            }
            byte[] body = ("{\"status\":\"" + norm + "\",\"reason\":\"STM-04 lifecycle access cut\"}")
                    .getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.warn("Student account access notify HTTP {} for student {}", code, studentId);
            }
        } catch (Exception e) {
            logger.warn("Student account access notify failed for student {}: {}", studentId, e.getMessage());
        }
    }
}
