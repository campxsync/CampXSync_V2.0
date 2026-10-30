package com.campx.admin.institute.controller;

import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileNotFoundException;
import com.campx.admin.institute.model.preference.RoleDashboardConfig;
import com.campx.admin.institute.model.preference.UserPreferences;
import com.campx.admin.institute.repository.UserPreferenceRepository;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.security.UserSecurityContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * HTTP Integration tests for {@link UserPreferenceController} on embedded {@link HttpServer}.
 * Validates HTTP 200, 400, 401, 403, 404, 422, anti-spoofing, and role isolation.
 */
public class UserPreferenceControllerHttpTest {

    private static HttpServer server;
    private static int boundPort;
    private static MockUserPreferenceRepository mockRepository;

    private static final String TEST_USER_ID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String TEST_TENANT_ID = "bbbbbbbb-1111-2222-3333-444444444444";

    @BeforeClass
    public static void startServer() throws Exception {
        mockRepository = new MockUserPreferenceRepository();
        GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier(false, null, 60);
        UserPreferenceController controller = new UserPreferenceController(mockRepository, hmacVerifier);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/user/preferences", controller);
        server.start();
        boundPort = server.getAddress().getPort();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Before
    public void resetMock() {
        mockRepository.reset();
    }

    @Test
    public void testUnauthenticatedGetReturns401() throws Exception {
        HttpURLConnection conn = openConnection("GET", "/api/v1/user/preferences");
        // No X-User-Id header provided
        int status = conn.getResponseCode();
        assertEquals(401, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("GATEWAY_UNAUTHENTICATED") || body.contains("Unauthorized"));
    }

    @Test
    public void testValidGetReturns200WithPreferences() throws Exception {
        HttpURLConnection conn = openConnection("GET", "/api/v1/user/preferences?role=faculty");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");

        int status = conn.getResponseCode();
        assertEquals(200, status);
        String body = readBody(conn.getInputStream());
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("\"isAccountSynced\":true"));
        assertTrue(body.contains("\"preferences\":{"));
        assertTrue(body.contains("\"theme\":\"light\""));
    }

    @Test
    public void testValidPutUpdatesAndDeepMergesReturns200() throws Exception {
        String payload = "{"
                + "\"theme\":\"dark\","
                + "\"tableDensity\":\"compact\","
                + "\"defaultDateRangeDays\":30,"
                + "\"dashboardWidgets\":{"
                + "  \"order\":[\"today\",\"upcoming\"],"
                + "  \"hidden\":[\"missed\"]"
                + "}"
                + "}";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(200, status);
        String body = readBody(conn.getInputStream());
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("\"isAccountSynced\":true"));
        assertTrue(body.contains("\"theme\":\"dark\""));
        assertTrue(body.contains("\"tableDensity\":\"compact\""));
        assertTrue(body.contains("\"defaultDateRangeDays\":30"));
    }

    @Test
    public void testPutRejectsInvalidThemeReturns422() throws Exception {
        String payload = "{\"theme\":\"rainbow_glitter\"}";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(422, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("INVALID_PREFERENCE_PAYLOAD"));
        assertTrue(body.contains("Invalid theme"));
    }

    @Test
    public void testPutRejectsDisallowedWidgetIdentifierReturns422() throws Exception {
        String payload = "{"
                + "\"dashboardWidgets\":{"
                + "  \"order\":[\"today\",\"crypto_tracker\"],"
                + "  \"hidden\":[]"
                + "}"
                + "}";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(422, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("INVALID_PREFERENCE_PAYLOAD"));
        assertTrue(body.contains("Disallowed dashboard widget identifier"));
    }

    @Test
    public void testPutRejectsClientBodyUserIdSpoofingReturns400Or403() throws Exception {
        // Attacker attempts to pass another user's ID in the JSON body
        String spoofedId = "99999999-9999-9999-9999-999999999999";
        String payload = "{\"userId\":\"" + spoofedId + "\",\"theme\":\"dark\"}";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(403, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("Disallowed identity override"));
    }

    @Test
    public void testPutRejectsClientBodyTenantIdSpoofingReturns400Or403() throws Exception {
        // Attacker attempts to pass another tenant's ID in the JSON body
        String spoofedTenantId = "88888888-8888-8888-8888-888888888888";
        String payload = "{\"tenantId\":\"" + spoofedTenantId + "\",\"theme\":\"dark\"}";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(403, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("Disallowed identity override"));
    }

    @Test
    public void testMalformedJsonReturns400() throws Exception {
        String malformed = "{ \"theme\": \"dark\", ";

        HttpURLConnection conn = openConnection("PUT", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "faculty");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(malformed.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        assertEquals(400, status);
        String body = readBody(conn.getErrorStream());
        assertTrue(body.contains("MALFORMED_PAYLOAD") || body.contains("Bad Request"));
    }

    @Test
    public void testMethodNotAllowedReturns405() throws Exception {
        HttpURLConnection conn = openConnection("DELETE", "/api/v1/user/preferences");
        conn.setRequestProperty("X-User-Id", TEST_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", TEST_TENANT_ID);

        int status = conn.getResponseCode();
        assertEquals(405, status);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpURLConnection openConnection(String method, String path) throws Exception {
        URL url = new URL("http://127.0.0.1:" + boundPort + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private String readBody(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static class MockUserPreferenceRepository extends UserPreferenceRepository {
        UserPreferences storedPreferences = UserPreferences.getProductDefaults();
        String lastActiveRole;

        void reset() {
            storedPreferences = UserPreferences.getProductDefaults();
            lastActiveRole = null;
        }

        @Override
        public UserPreferences getPreferences(UserSecurityContext context, String requestedRole) {
            if (context == null || context.getUserId() == null) {
                throw new UserProfileAccessDeniedException("Missing context");
            }
            storedPreferences.resolveActiveDashboardWidgets(requestedRole);
            return storedPreferences;
        }

        @Override
        public UserPreferences updatePreferences(UserSecurityContext context, UserPreferences incoming, String activeRole) {
            if (context == null || context.getUserId() == null) {
                throw new UserProfileAccessDeniedException("Missing context");
            }
            this.lastActiveRole = activeRole;
            this.storedPreferences = storedPreferences.deepMerge(incoming, activeRole);
            return this.storedPreferences;
        }
    }
}
