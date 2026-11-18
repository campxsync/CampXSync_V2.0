package com.campx.admissions.integration;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * Best-effort POST {@code /api/v1/students/{id}/provision-iam} on student-service.
 * Mirrors {@code HttpStudentAccountAccessPort} (lifecycle → student) header and env conventions.
 */
public final class HttpAccountProvisioningPort implements AccountProvisioningPort {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(HttpAccountProvisioningPort.class);

    private final String baseUrl;

    public HttpAccountProvisioningPort(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public static AccountProvisioningPort fromEnvironment() {
        String base = System.getenv("STM01_SERVICE_URL");
        if (base == null || base.trim().isEmpty()) {
            base = System.getenv("STUDENT_SERVICE_URL");
        }
        if (base == null || base.trim().isEmpty()) {
            return new NoOpAccountProvisioningPort();
        }
        return new HttpAccountProvisioningPort(base.trim());
    }

    @Override
    public void requestLoginAfterHandover(UUID tenantId, UUID studentId, String correlationId) {
        if (baseUrl.isEmpty() || tenantId == null || studentId == null) {
            return;
        }
        try {
            URL url = new URL(baseUrl + "/api/v1/students/" + studentId + "/provision-iam");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Tenant-Id", tenantId.toString());
            conn.setRequestProperty("X-User-Id", "00000000-0000-0000-0000-000000000001");
            conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
            if (correlationId != null && !correlationId.trim().isEmpty()) {
                conn.setRequestProperty("X-Correlation-Id", correlationId);
            }
            conn.getOutputStream().write("{}".getBytes("UTF-8"));
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.warn("Admission handover provisioning notify HTTP {} for student {}", code, studentId);
            } else {
                logger.info("Admission handover requested IAM provisioning for student {}", studentId);
            }
        } catch (Exception e) {
            logger.warn("Admission handover provisioning notify failed for student {}: {}", studentId, e.getMessage());
        }
    }
}
