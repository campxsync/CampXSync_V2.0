package com.campx.admin.institute.controller;

import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Task 4.3: Unit / HTTP integration tests asserting RBAC security on IADM-04 Subscription & Billing endpoints:
 * 1. College Admin receives 403 Forbidden on every billing route.
 * 2. Principal receives 403 Forbidden on billing routes.
 * 3. Institution Admin holding adm01.manage succeeds on billing routes.
 * 4. Non-platform admin creating/publishing a plan receives 403 Forbidden.
 * 5. Platform Admin succeeds creating a plan.
 */
public class BillingRbacSecurityHttpTest {

    private static int TEST_PORT;
    private static HttpServer server;
    private static InstituteAdminDomainService domainService;

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @BeforeClass
    public static void startServer() throws Exception {
        TEST_PORT = findFreePort();
        domainService = new InstituteAdminDomainService();
        InstituteAdminController controller = new InstituteAdminController(
                domainService, null, new GatewayHmacVerifier(false, null, 60));

        server = HttpServer.create(new InetSocketAddress(TEST_PORT), 0);
        server.createContext("/api/v1/admin", controller);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpURLConnection openConnection(String method, String path, String role) throws IOException {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (role != null) {
            conn.setRequestProperty("X-User-Role", role);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", "a0000000-0000-0000-0000-000000000001");
        conn.setRequestProperty("X-User-Id", "ba77b8f7-ab32-46bd-85d2-5aade3526880");
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    @Test
    public void testCollegeAdminDeniedOnAllBillingRoutes() throws Exception {
        String role = "COLLEGE_ADMIN";

        // 1. GET /subscriptions
        HttpURLConnection getSubs = openConnection("GET", "/api/v1/admin/billing/subscriptions", role);
        assertEquals(403, getSubs.getResponseCode());
        assertTrue(readResponse(getSubs).contains("ADM01_FORBIDDEN"));

        // 2. POST /subscriptions
        HttpURLConnection postSubs = openConnection("POST", "/api/v1/admin/billing/subscriptions", role);
        postSubs.setDoOutput(true);
        try (OutputStream os = postSubs.getOutputStream()) {
            os.write("{\"tenantId\":\"t1\",\"planId\":\"p1\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(403, postSubs.getResponseCode());

        // 3. GET /invoices
        HttpURLConnection getInvs = openConnection("GET", "/api/v1/admin/billing/invoices", role);
        assertEquals(403, getInvs.getResponseCode());

        // 4. GET /transactions
        HttpURLConnection getTxns = openConnection("GET", "/api/v1/admin/billing/transactions", role);
        assertEquals(403, getTxns.getResponseCode());

        // 5. POST /plans
        HttpURLConnection postPlan = openConnection("POST", "/api/v1/admin/billing/plans", role);
        postPlan.setDoOutput(true);
        try (OutputStream os = postPlan.getOutputStream()) {
            os.write("{\"planCode\":\"P1\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(403, postPlan.getResponseCode());
    }

    @Test
    public void testPrincipalDeniedOnBillingRoutes() throws Exception {
        String role = "PRINCIPAL";

        HttpURLConnection getSubs = openConnection("GET", "/api/v1/admin/billing/subscriptions", role);
        assertEquals(403, getSubs.getResponseCode());
        assertTrue(readResponse(getSubs).contains("ADM01_FORBIDDEN"));

        HttpURLConnection getInvs = openConnection("GET", "/api/v1/admin/billing/invoices", role);
        assertEquals(403, getInvs.getResponseCode());
    }

    @Test
    public void testInstitutionAdminCanAccessBillingButCannotCreatePlans() throws Exception {
        String role = "INSTITUTION_ADMIN";

        // 1. Can list subscriptions
        HttpURLConnection getSubs = openConnection("GET", "/api/v1/admin/billing/subscriptions", role);
        assertEquals(200, getSubs.getResponseCode());
        assertTrue(readResponse(getSubs).contains("\"subscriptions\":"));

        // 2. Can create subscription
        HttpURLConnection postSubs = openConnection("POST", "/api/v1/admin/billing/subscriptions", role);
        postSubs.setDoOutput(true);
        try (OutputStream os = postSubs.getOutputStream()) {
            os.write("{\"tenantId\":\"a0000000-0000-0000-0000-000000000001\",\"planId\":\"ENTERPRISE_CAMPUS_2026\",\"seatCount\":50}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postSubs.getResponseCode());

        // 3. Can list invoices
        HttpURLConnection getInvs = openConnection("GET", "/api/v1/admin/billing/invoices", role);
        assertEquals(200, getInvs.getResponseCode());

        // 4. Cannot create plan (platform-admin only)
        HttpURLConnection postPlan = openConnection("POST", "/api/v1/admin/billing/plans", role);
        postPlan.setDoOutput(true);
        try (OutputStream os = postPlan.getOutputStream()) {
            os.write("{\"planCode\":\"UNAUTHORIZED_PLAN\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(403, postPlan.getResponseCode());
        assertTrue(readResponse(postPlan).contains("PLATFORM_ADMIN_REQUIRED"));
    }

    @Test
    public void testPlatformAdminCanCreateAndPublishPlans() throws Exception {
        String role = "PLATFORM_ADMIN";

        String planCode = "PLAT_TEST_PLAN_" + System.currentTimeMillis();
        HttpURLConnection postPlan = openConnection("POST", "/api/v1/admin/billing/plans", role);
        postPlan.setDoOutput(true);
        try (OutputStream os = postPlan.getOutputStream()) {
            os.write(("{\"planCode\":\"" + planCode + "\",\"name\":\"Platform Plan\",\"price\":1000.0}").getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postPlan.getResponseCode());

        HttpURLConnection pubPlan = openConnection("POST", "/api/v1/admin/billing/plans/" + planCode + "/publish", role);
        assertEquals(200, pubPlan.getResponseCode());
        assertTrue(readResponse(pubPlan).contains("\"published\":true"));
    }
}
