package com.campx.enrollment;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * STM-03-EN-004: OpenAPI service contract must document gateway-facing enrolment paths.
 */
public class EnrollmentOpenApiContractTest {

    @Test
    public void stm03En004_openapiDocumentsGatewayFacingEnrollmentContracts() throws Exception {
        Path openApi = resolveOpenApi();
        assertTrue("openapi.yaml must exist for STM-03", Files.isRegularFile(openApi));
        String yaml = new String(Files.readAllBytes(openApi), StandardCharsets.UTF_8);

        assertTrue(yaml.contains("openapi: 3."));
        assertTrue(yaml.contains("url: http://localhost:8080"));
        assertTrue(yaml.contains("url: http://localhost:8095"));
        assertTrue(yaml.contains("/api/v1/enrollment/handovers"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}/register"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}/eligibility"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}/status"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}/history"));
        assertTrue(yaml.contains("/api/v1/enrollment/enrolments/{enrolmentId}/catalogue"));
        assertTrue(yaml.contains("/api/v1/enrollment/alerts"));
        assertTrue(yaml.contains("X-Tenant-Id"));
        assertTrue(yaml.contains("X-User-Id"));
        assertTrue(yaml.contains("X-User-Role"));
    }

    private Path resolveOpenApi() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd.resolve("openapi.yaml"),
                cwd.resolve("services/student-services/enrollment-service/openapi.yaml"),
                cwd.getParent() != null ? cwd.getParent().resolve("openapi.yaml") : null,
                cwd.resolve("../../openapi.yaml").normalize()
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        // Last resort: walk up looking for enrollment-service/openapi.yaml
        Path cursor = cwd;
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path hit = cursor.resolve("services/student-services/enrollment-service/openapi.yaml");
            if (Files.isRegularFile(hit)) {
                return hit;
            }
            cursor = cursor.getParent();
        }
        assertNotNull("Could not locate enrollment-service openapi.yaml from " + cwd, null);
        return cwd.resolve("openapi.yaml");
    }
}
