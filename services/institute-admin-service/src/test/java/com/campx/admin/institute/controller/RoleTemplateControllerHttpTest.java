package com.campx.admin.institute.controller;

import com.campx.admin.institute.model.InstituteModels.OutboxEvent;
import com.campx.admin.institute.repository.InMemoryRoleTemplateRepository;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Before;
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
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * HTTP Integration tests for ADM-01 Step 2: Role Template Management HTTP Endpoints.
 * Verifies REST endpoints on embedded {@link HttpServer}:
 * <ul>
 * <li>POST /api/v1/admin/role-templates (Create)</li>
 * <li>GET /api/v1/admin/role-templates (List)</li>
 * <li>GET /api/v1/admin/role-templates/{id} (Get by ID or Code)</li>
 * <li>PUT /api/v1/admin/role-templates/{id} (Update)</li>
 * <li>DELETE /api/v1/admin/role-templates/{id} (Soft-Delete)</li>
 * <li>POST /api/v1/admin/role-templates/{code}/permissions (Bind permission)</li>
 * <li>GET /api/v1/admin/role-templates/{code}/permissions (List permissions)</li>
 * <li>DELETE /api/v1/admin/role-templates/{code}/permissions/{permissionCode} (Unbind permission)</li>
 * <li>Platform administrator authorization enforcement (403 for tenant admins)</li>
 * <li>Transactional outbox event propagation</li>
 * <li>RFC 7807 error responses and ETag / Location header contracts</li>
 * </ul>
 */
public class RoleTemplateControllerHttpTest {

    private static int TEST_PORT;
    private static HttpServer server;
    private static InstituteAdminDomainService domainService;
    private static InMemoryRoleTemplateRepository roleTemplateRepository;

    private static final String PLATFORM_ADMIN_USER_ID = UUID.randomUUID().toString();
    private static final String REGULAR_USER_ID = UUID.randomUUID().toString();
    private static final String TENANT_ID = "0c914bb2-f63b-472c-a99a-39977112935d";

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
        roleTemplateRepository = new InMemoryRoleTemplateRepository();

        InstituteAdminController controller = new InstituteAdminController(
                domainService, null, roleTemplateRepository,
                new GatewayHmacVerifier(false, null, 60));

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

    private HttpURLConnection openConnection(String path, String method, boolean asPlatformAdmin) throws IOException {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (asPlatformAdmin) {
            conn.setRequestProperty("X-User-Id", PLATFORM_ADMIN_USER_ID);
            conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");
            // Platform admin operates without tenant-id scoping header
        } else {
            conn.setRequestProperty("X-User-Id", REGULAR_USER_ID);
            conn.setRequestProperty("X-Tenant-Id", TENANT_ID);
            conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");
        }
        conn.setRequestProperty("Content-Type", "application/json");
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void sendRequestBody(HttpURLConnection conn, String json) throws IOException {
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // 1. Create Role Template Tests
    // =========================================================================

    @Test
    public void testCreateRoleTemplateSuccess() throws Exception {
        String code = "TPL_HTTP_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{"
                + "\"code\":\"" + code + "\","
                + "\"name\":\"HTTP Test Role\","
                + "\"kind\":\"administrative_staff\","
                + "\"scopeNote\":\"Test scope note\""
                + "}";

        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(conn, payload);

        assertEquals(201, conn.getResponseCode());
        assertNotNull(conn.getHeaderField("Location"));
        assertNotNull(conn.getHeaderField("ETag"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"code\":\"" + code + "\""));
        assertTrue(resp.contains("\"name\":\"HTTP Test Role\""));
        assertTrue(resp.contains("\"rowVersion\":1"));

        // Verify transactional outbox event
        boolean foundOutbox = false;
        for (OutboxEvent evt : domainService.listOutboxEvents()) {
            if ("RoleTemplateCreated".equals(evt.getEventType()) && evt.getPayload().contains(code)) {
                foundOutbox = true;
                break;
            }
        }
        assertTrue("Transactional outbox event RoleTemplateCreated must be emitted", foundOutbox);
    }

    @Test
    public void testCreateRoleTemplateValidationFailure() throws Exception {
        // Missing name
        String payload = "{\"code\":\"TPL_NONAME\"}";

        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(conn, payload);

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM01_MALFORMED_PAYLOAD") || resp.contains("ADM01_VALIDATION_ERROR"));
    }

    @Test
    public void testCreateRoleTemplateForbiddenFields() throws Exception {
        // Attempting to inject server-managed row_version
        String payload = "{\"code\":\"TPL_INJECT\",\"name\":\"Injected\",\"row_version\":10}";

        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(conn, payload);

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
    }

    @Test
    public void testCreateRoleTemplateForbiddenForTenantAdmin() throws Exception {
        String payload = "{\"code\":\"TPL_ATTACK\",\"name\":\"Attack Role\"}";

        // As regular tenant admin (X-Tenant-Id present) -> 403 Forbidden
        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates", "POST", false);
        sendRequestBody(conn, payload);

        assertEquals(403, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM01_ACCESS_DENIED"));
    }

    // =========================================================================
    // 2. Retrieval and List Tests
    // =========================================================================

    @Test
    public void testListRoleTemplates() throws Exception {
        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates", "GET", true);
        assertEquals(200, conn.getResponseCode());

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"roleTemplates\":["));
    }

    @Test
    public void testGetRoleTemplateByIdAndCode() throws Exception {
        String code = "TPL_GET_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Get Role Target\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());
        String location = createConn.getHeaderField("Location");
        assertNotNull(location);

        // Get by ID (Location header path)
        HttpURLConnection getByIdConn = openConnection(location, "GET", true);
        assertEquals(200, getByIdConn.getResponseCode());
        assertNotNull(getByIdConn.getHeaderField("ETag"));
        String respById = readResponse(getByIdConn);
        assertTrue(respById.contains("\"code\":\"" + code + "\""));

        // Get by Code
        HttpURLConnection getByCodeConn = openConnection("/api/v1/admin/role-templates/" + code, "GET", true);
        assertEquals(200, getByCodeConn.getResponseCode());
        String respByCode = readResponse(getByCodeConn);
        assertTrue(respByCode.contains("\"code\":\"" + code + "\""));
    }

    @Test
    public void testGetRoleTemplateNotFound() throws Exception {
        HttpURLConnection conn = openConnection("/api/v1/admin/role-templates/" + UUID.randomUUID(), "GET", true);
        assertEquals(404, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM01_NOT_FOUND") || resp.contains("Not Found"));
    }

    // =========================================================================
    // 3. Update Role Template Tests
    // =========================================================================

    @Test
    public void testUpdateRoleTemplateSuccess() throws Exception {
        String code = "TPL_UPD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Original Name\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());
        String location = createConn.getHeaderField("Location");

        // Update name
        String updatePayload = "{\"name\":\"Updated Name\",\"scopeNote\":\"Updated scope\"}";
        HttpURLConnection updConn = openConnection(location, "PUT", true);
        sendRequestBody(updConn, updatePayload);

        assertEquals(200, updConn.getResponseCode());
        assertEquals("\"2\"", updConn.getHeaderField("ETag"));
        String resp = readResponse(updConn);
        assertTrue(resp.contains("\"name\":\"Updated Name\""));
        assertTrue(resp.contains("\"scopeNote\":\"Updated scope\""));
        assertTrue(resp.contains("\"rowVersion\":2"));

        // Verify outbox event
        boolean foundOutbox = false;
        for (OutboxEvent evt : domainService.listOutboxEvents()) {
            if ("RoleTemplateUpdated".equals(evt.getEventType()) && evt.getPayload().contains("Updated Name")) {
                foundOutbox = true;
                break;
            }
        }
        assertTrue("Transactional outbox event RoleTemplateUpdated must be emitted", foundOutbox);
    }

    @Test
    public void testUpdateRoleTemplateForbiddenForTenantAdmin() throws Exception {
        String code = "TPL_UPDFORBID_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Original Name\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());
        String location = createConn.getHeaderField("Location");

        // Tenant admin attempt
        HttpURLConnection updConn = openConnection(location, "PUT", false);
        sendRequestBody(updConn, "{\"name\":\"Tampered\"}");

        assertEquals(403, updConn.getResponseCode());
    }

    // =========================================================================
    // 4. Delete Role Template Tests
    // =========================================================================

    @Test
    public void testDeleteRoleTemplateSuccess() throws Exception {
        String code = "TPL_DEL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Delete Target\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());
        String location = createConn.getHeaderField("Location");

        // Soft-delete
        HttpURLConnection delConn = openConnection(location, "DELETE", true);
        assertEquals(204, delConn.getResponseCode());

        // Subsequent GET returns 404
        HttpURLConnection getConn = openConnection(location, "GET", true);
        assertEquals(404, getConn.getResponseCode());

        // Verify outbox event
        boolean foundOutbox = false;
        for (OutboxEvent evt : domainService.listOutboxEvents()) {
            if ("RoleTemplateDeleted".equals(evt.getEventType())) {
                foundOutbox = true;
                break;
            }
        }
        assertTrue("Transactional outbox event RoleTemplateDeleted must be emitted", foundOutbox);
    }

    @Test
    public void testDeleteRoleTemplateForbiddenForTenantAdmin() throws Exception {
        String code = "TPL_DELFORBID_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Delete Target\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());
        String location = createConn.getHeaderField("Location");

        HttpURLConnection delConn = openConnection(location, "DELETE", false);
        assertEquals(403, delConn.getResponseCode());
    }

    // =========================================================================
    // 5. Permission Binding Tests
    // =========================================================================

    @Test
    public void testPermissionsLifecycle() throws Exception {
        String code = "TPL_PERM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = "{\"code\":\"" + code + "\",\"name\":\"Permission Target\"}";

        HttpURLConnection createConn = openConnection("/api/v1/admin/role-templates", "POST", true);
        sendRequestBody(createConn, payload);
        assertEquals(201, createConn.getResponseCode());

        // Bind permission
        String permPayload = "{\"permissionCode\":\"campus.lab.manage\"}";
        HttpURLConnection bindConn = openConnection("/api/v1/admin/role-templates/" + code + "/permissions", "POST", true);
        sendRequestBody(bindConn, permPayload);

        assertEquals(201, bindConn.getResponseCode());
        String bindResp = readResponse(bindConn);
        assertTrue(bindResp.contains("\"permissionCode\":\"campus.lab.manage\""));

        // List permissions
        HttpURLConnection listConn = openConnection("/api/v1/admin/role-templates/" + code + "/permissions", "GET", true);
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("campus.lab.manage"));

        // Unbind permission
        HttpURLConnection unbindConn = openConnection(
                "/api/v1/admin/role-templates/" + code + "/permissions/campus.lab.manage", "DELETE", true);
        assertEquals(204, unbindConn.getResponseCode());

        // List permissions after unbind -> empty
        HttpURLConnection listAfterConn = openConnection("/api/v1/admin/role-templates/" + code + "/permissions", "GET", true);
        assertEquals(200, listAfterConn.getResponseCode());
        String listAfterResp = readResponse(listAfterConn);
        assertTrue(listAfterResp.contains("\"permissions\":[]"));

        // Verify outbox events for binding and unbinding
        boolean foundBindOutbox = false;
        boolean foundUnbindOutbox = false;
        for (OutboxEvent evt : domainService.listOutboxEvents()) {
            if ("RoleTemplatePermissionBound".equals(evt.getEventType())) foundBindOutbox = true;
            if ("RoleTemplatePermissionUnbound".equals(evt.getEventType())) foundUnbindOutbox = true;
        }
        assertTrue(foundBindOutbox);
        assertTrue(foundUnbindOutbox);
    }
}
