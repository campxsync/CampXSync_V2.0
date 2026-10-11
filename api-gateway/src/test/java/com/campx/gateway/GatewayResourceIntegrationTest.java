package com.campx.gateway;

import com.campx.academic.resource.model.ResourceModels.CreateResourceRequest;
import com.campx.academic.resource.server.ResourceServer;
import com.campx.academic.resource.service.ResourceDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying API Gateway reverse proxy routing,
 * correlation tracking, and transparent pass-through to ACD-08 Learning Resource Service.
 */
public class GatewayResourceIntegrationTest {

    private static ResourceServer resourceServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8094;
    private static final int RES_PORT = 8095;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-08 Learning Resource Service on port 8095
        ResourceDomainService domainService = new ResourceDomainService();
        CreateResourceRequest seed = new CreateResourceRequest();
        seed.resourceCode = "RES-GW-SEED";
        seed.title = "Seed Resource for Gateway";
        seed.resourceType = "STUDY_MATERIAL";
        seed.departmentId = "DEP_CS";
        seed.storageObjectRef = "materials/gw_seed.pdf";
        seed.mimeType = "application/pdf";
        seed.fileSize = 1000L;
        seed.checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        domainService.createResource(seed, "TENANT-001", "faculty-1", "FACULTY", null, "GW-SEED-TRACE");

        resourceServer = new ResourceServer(RES_PORT, domainService);
        resourceServer.start();

        // 2. Start Gateway on port 8094 configured to proxy to resourceServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/academics/resources/metrics", "http://localhost:" + RES_PORT + "/metrics");
        config.addRoute("/api/v1/resources/metrics", "http://localhost:" + RES_PORT + "/metrics");
        config.addRoute("/api/v1/academics/resources", "http://localhost:" + RES_PORT + "/api/v1/academics/resources");
        config.addRoute("/api/v1/resources", "http://localhost:" + RES_PORT + "/api/v1/academics/resources");
        config.addRoute("/api/v1/academics/versions", "http://localhost:" + RES_PORT + "/api/v1/academics/versions");
        config.addRoute("/v1/resources", "http://localhost:" + RES_PORT + "/api/v1/academics/resources");
        config.addRoute("/v1/resource-catalog", "http://localhost:" + RES_PORT + "/api/v1/academics/resources");
        config.addRoute("/v1/resource-versions", "http://localhost:" + RES_PORT + "/api/v1/academics/versions");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (resourceServer != null) resourceServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testCreateResourceViaGateway() throws Exception {
        String payload = "{"
                + "\"resourceCode\":\"RES-GW-2026\","
                + "\"title\":\"Distributed Systems Course Pack\","
                + "\"resourceType\":\"STUDY_MATERIAL\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"storageObjectRef\":\"materials/ds_pack.pdf\","
                + "\"mimeType\":\"application/pdf\","
                + "\"fileSize\":2048,"
                + "\"checksum\":\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/resources");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-RES-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Id", "faculty-1");
        conn.setRequestProperty("X-User-Role", "FACULTY");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertNotNull(traceHeader);
        assertEquals("TRACE-GW-RES-001", traceHeader);

        String responseBody = readStream(conn.getInputStream());
        assertTrue(responseBody.contains("RES-GW-2026"));
        assertTrue(responseBody.contains("SUCCESS"));
    }

    @Test
    public void testGetCanonicalResourceCatalogViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/resources");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String responseBody = readStream(conn.getInputStream());
        assertTrue(responseBody.contains("RES-GW-SEED"));
    }

    @Test
    public void testMetricsRoutingViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/resources/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String responseBody = readStream(conn.getInputStream());
        assertTrue(responseBody.contains("acd08_resource_registrations_total"));
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
