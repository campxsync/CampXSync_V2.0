package com.campx.admin.institute.controller;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.UserProfileModels.*;
import com.campx.admin.institute.repository.UserProfileRepository;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.admin.institute.service.InstituteAdminDomainService;
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
 * HTTP Integration unit tests for ADM-01 User Profile Management REST endpoints.
 * Mounts {@link InstituteAdminController} onto an embedded {@link HttpServer} with a mock
 * repository to test the HTTP contract, RFC 7807 error structures, ETag, Location headers,
 * and security header validations.
 */
public class UserProfileControllerHttpTest {

    private static final int TEST_PORT = 8092;
    private static HttpServer server;
    private static TestUserProfileRepository mockRepository;

    private static final String CALLER_USER_ID = UUID.randomUUID().toString();
    private static final String CALLER_TENANT_ID = UUID.randomUUID().toString();

    @BeforeClass
    public static void startServer() throws Exception {
        mockRepository = new TestUserProfileRepository();
        InstituteAdminController controller = new InstituteAdminController(new InstituteAdminDomainService(), mockRepository,
                new com.campx.admin.institute.security.GatewayHmacVerifier(false, null, 60));

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

    @Before
    public void resetMock() {
        mockRepository.reset();
    }

    // =========================================================================
    // GET /api/v1/admin/users Collection Tests
    // =========================================================================

    @Test
    public void testListUsersSuccess() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users?status=ACTIVE&page=1&limit=10&sort=email&order=asc");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(200, conn.getResponseCode());
        assertEquals("application/json; charset=UTF-8", conn.getHeaderField("Content-Type"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"users\":["));
        assertTrue(resp.contains("\"pagination\":{"));
        assertTrue(resp.contains("\"page\":1"));
        assertTrue(resp.contains("\"limit\":10"));
    }

    @Test
    public void testListUsersMissingSecurityHeaders() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        // No X-User-Id or X-Tenant-Id headers

        assertEquals(403, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_ACCESS_DENIED"));
    }

    @Test
    public void testListUsersInvalidUserIdFormat() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", "not-a-uuid");
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
    }

    @Test
    public void testListUsersInvalidSortField() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users?sort=unwhitelisted_column");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_VALIDATION_ERROR"));
        assertTrue(resp.contains("Invalid sort field"));
    }

    @Test
    public void testListUsersInvalidSortOrder() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users?order=SIDEWAYS");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_VALIDATION_ERROR"));
        assertTrue(resp.contains("Invalid sort order"));
    }

    // =========================================================================
    // GET /api/v1/admin/users/{id} Single Profile Tests
    // =========================================================================

    @Test
    public void testGetUserByIdSuccess() throws Exception {
        UUID targetId = UUID.randomUUID();
        UserProfile p = new UserProfile();
        p.setId(targetId.toString());
        p.setTenantId(CALLER_TENANT_ID);
        p.setFullName("Existing User");
        p.setEmail("existing@example.com");
        p.setStatus("ACTIVE");
        p.setRowVersion(5);
        mockRepository.userMap.put(targetId, p);

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(200, conn.getResponseCode());
        assertEquals("\"5\"", conn.getHeaderField("ETag"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"id\":\"" + targetId + "\""));
        assertTrue(resp.contains("\"fullName\":\"Existing User\""));
        assertTrue(resp.contains("\"rowVersion\":5"));
    }

    @Test
    public void testGetUserByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + nonExistentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(404, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_USER_NOT_FOUND"));
    }

    @Test
    public void testGetUserByIdInvalidUuid() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/not-a-valid-uuid");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_VALIDATION_ERROR"));
    }

    // =========================================================================
    // POST /api/v1/admin/users Create Profile Tests
    // =========================================================================

    @Test
    public void testCreateUserSuccess() throws Exception {
        UUID authUserId = UUID.randomUUID();
        String payload = "{"
                + "\"id\":\"" + authUserId + "\","
                + "\"email\":\"newadmin@campx.edu\","
                + "\"fullName\":\"New Admin\","
                + "\"username\":\"newadmin\","
                + "\"preferences\":{\"theme\":\"dark\"}"
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("\"1\"", conn.getHeaderField("ETag"));
        assertEquals("/api/v1/admin/users/" + authUserId, conn.getHeaderField("Location"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"id\":\"" + authUserId + "\""));
        assertTrue(resp.contains("\"email\":\"newadmin@campx.edu\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    public void testCreateUserForbiddenServerManagedFields() throws Exception {
        String payload = "{"
                + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"email\":\"hacker@campx.edu\","
                + "\"fullName\":\"Hacker\","
                + "\"tenant_id\":\"" + UUID.randomUUID() + "\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
        assertTrue(resp.contains("server-managed"));
    }

    @Test
    public void testCreateUserLegacyDisplayNameWithoutSecurityContextRejected() throws Exception {
        String payload = "{"
                + "\"userId\":\"iam_super_admin_01\","
                + "\"displayName\":\"Super Admin\","
                + "\"email\":\"superadmin@campx.edu\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        // Deliberately omit X-User-Id and X-Tenant-Id headers

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_ACCESS_DENIED"));
        assertTrue(resp.contains("X-User-Id"));
    }

    @Test
    public void testCreateUserWithDisplayNameAndValidSecurityContextFollowsNormalPath() throws Exception {
        UUID authUserId = UUID.randomUUID();
        String payload = "{"
                + "\"id\":\"" + authUserId + "\","
                + "\"email\":\"withdisplayname@campx.edu\","
                + "\"fullName\":\"Valid Full Name\","
                + "\"displayName\":\"Ignored Display Name\","
                + "\"username\":\"validuser\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("\"1\"", conn.getHeaderField("ETag"));
        assertEquals("/api/v1/admin/users/" + authUserId, conn.getHeaderField("Location"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"id\":\"" + authUserId + "\""));
        assertTrue(resp.contains("\"email\":\"withdisplayname@campx.edu\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    public void testCreateUserConflict() throws Exception {
        mockRepository.throwConflictOnCreate = true;

        String payload = "{"
                + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"email\":\"duplicate@campx.edu\","
                + "\"fullName\":\"Duplicate User\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_USER_CONFLICT"));
    }

    // =========================================================================
    // PUT /api/v1/admin/users/{id} Update Profile Tests
    // =========================================================================

    @Test
    public void testUpdateUserSuccessWithBodyRowVersion() throws Exception {
        UUID targetId = UUID.randomUUID();
        UserProfile existing = new UserProfile();
        existing.setId(targetId.toString());
        existing.setTenantId(CALLER_TENANT_ID);
        existing.setFullName("Original Name");
        existing.setEmail("user@example.com");
        existing.setStatus("ACTIVE");
        existing.setRowVersion(2);
        mockRepository.userMap.put(targetId, existing);

        String payload = "{"
                + "\"fullName\":\"Updated Name\","
                + "\"row_version\":2"
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        assertEquals("\"3\"", conn.getHeaderField("ETag"));
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"fullName\":\"Updated Name\""));
        assertTrue(resp.contains("\"rowVersion\":3"));
    }

    @Test
    public void testUpdateUserSuccessWithIfMatchHeader() throws Exception {
        UUID targetId = UUID.randomUUID();
        UserProfile existing = new UserProfile();
        existing.setId(targetId.toString());
        existing.setTenantId(CALLER_TENANT_ID);
        existing.setFullName("Original Name");
        existing.setEmail("user@example.com");
        existing.setStatus("ACTIVE");
        existing.setRowVersion(2);
        mockRepository.userMap.put(targetId, existing);

        String payload = "{"
                + "\"fullName\":\"Updated Name via Header\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("If-Match", "\"2\"");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        assertEquals("\"3\"", conn.getHeaderField("ETag"));
    }

    @Test
    public void testUpdateUserMissingRowVersion() throws Exception {
        UUID targetId = UUID.randomUUID();
        String payload = "{\"fullName\":\"Updated Name\"}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_MALFORMED_PAYLOAD"));
        assertTrue(resp.contains("optimistic lock"));
    }

    @Test
    public void testUpdateUserForbiddenStatusField() throws Exception {
        UUID targetId = UUID.randomUUID();
        String payload = "{"
                + "\"status\":\"SUSPENDED\","
                + "\"row_version\":1"
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
    }

    // =========================================================================
    // PATCH /api/v1/admin/users/{id}/status Status Transition Tests
    // =========================================================================

    @Test
    public void testUpdateUserStatusSuccess() throws Exception {
        UUID targetId = UUID.randomUUID();
        UserProfile existing = new UserProfile();
        existing.setId(targetId.toString());
        existing.setTenantId(CALLER_TENANT_ID);
        existing.setFullName("Active Admin");
        existing.setEmail("admin@example.com");
        existing.setStatus("ACTIVE");
        existing.setRowVersion(1);
        mockRepository.userMap.put(targetId, existing);

        String payload = "{"
                + "\"status\":\"SUSPENDED\","
                + "\"row_version\":1,"
                + "\"reason\":\"Scheduled maintenance review\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId + "/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setMethod(conn, "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        assertEquals("\"2\"", conn.getHeaderField("ETag"));
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"SUSPENDED\""));
        assertTrue(resp.contains("\"rowVersion\":2"));
    }

    @Test
    public void testUpdateUserStatusInvalidTransition() throws Exception {
        UUID targetId = UUID.randomUUID();
        mockRepository.throwInvalidTransition = true;

        String payload = "{"
                + "\"status\":\"LOCKED\","
                + "\"row_version\":1"
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId + "/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setMethod(conn, "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(422, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_INVALID_STATUS_TRANSITION"));
    }

    @Test
    public void testCreateUserLockedStatusRejected() throws Exception {
        String payload = "{"
                + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"email\":\"locked@campx.edu\","
                + "\"fullName\":\"Locked User\","
                + "\"status\":\"LOCKED\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
    }

    @Test
    public void testCreateUserSuspendedStatusRejected() throws Exception {
        String payload = "{"
                + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"email\":\"suspended@campx.edu\","
                + "\"fullName\":\"Suspended User\","
                + "\"status\":\"SUSPENDED\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
    }

    @Test
    public void testUpdateUserStatusReasonExceeds500Chars() throws Exception {
        UUID targetId = UUID.randomUUID();
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 505; i++) longReason.append("x");

        String payload = "{"
                + "\"status\":\"SUSPENDED\","
                + "\"row_version\":1,"
                + "\"reason\":\"" + longReason + "\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId + "/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setMethod(conn, "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_SECURITY_VIOLATION"));
        assertTrue(resp.contains("500 characters"));
    }

    @Test
    public void testUpdateUserStatusLastAdminGuardTriggered() throws Exception {
        UUID targetId = UUID.randomUUID();
        mockRepository.throwLastAdminGuard = true;

        String payload = "{"
                + "\"status\":\"SUSPENDED\","
                + "\"row_version\":1,"
                + "\"reason\":\"Retiring admin\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users/" + targetId + "/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setMethod(conn, "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(422, conn.getResponseCode());
        String resp = readErrorResponse(conn);
        assertTrue(resp.contains("ADM01_INVALID_LIFECYCLE_STATE"));
        assertTrue(resp.contains("last active tenant-wide administrator"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String readResponse(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String readErrorResponse(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getErrorStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static void setMethod(HttpURLConnection conn, String method) throws Exception {
        try {
            conn.setRequestMethod(method);
        } catch (java.net.ProtocolException e) {
            java.lang.reflect.Field methodField = HttpURLConnection.class.getDeclaredField("method");
            methodField.setAccessible(true);
            methodField.set(conn, method);
        }
        if ("PATCH".equalsIgnoreCase(method)) {
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }
    }

    /**
     * In-memory mock repository implementing the contract for HTTP layer unit testing.
     */
    static class TestUserProfileRepository extends UserProfileRepository {

        final Map<UUID, UserProfile> userMap = new HashMap<>();
        boolean throwConflictOnCreate = false;
        boolean throwInvalidTransition = false;
        boolean throwLastAdminGuard = false;

        public TestUserProfileRepository() {
            super(null);
        }

        void reset() {
            userMap.clear();
            throwConflictOnCreate = false;
            throwInvalidTransition = false;
            throwLastAdminGuard = false;
        }

        @Override
        public UserProfile getUserById(UserSecurityContext context, UUID id) {
            UserProfile p = userMap.get(id);
            if (p == null) {
                throw new UserProfileNotFoundException(id.toString(), context.getTenantId().toString());
            }
            return p;
        }

        @Override
        public UserProfilePage listUsers(UserSecurityContext context, UserProfileFilter filter) {
            List<UserProfile> list = new ArrayList<>(userMap.values());
            return new UserProfilePage(list, filter.getPage(), filter.getLimit(), list.size());
        }

        @Override
        public UserProfile createUser(UserSecurityContext context, CreateUserProfileRequest request) {
            if (throwConflictOnCreate) {
                throw new UserProfileConflictException("ADM01_USER_CONFLICT", "Duplicate email: " + request.getEmail());
            }
            UserProfile p = new UserProfile();
            p.setId(request.getId() != null ? request.getId() : UUID.randomUUID().toString());
            p.setTenantId(context.getTenantId().toString());
            p.setEmail(request.getEmail());
            p.setFullName(request.getFullName());
            p.setUsername(request.getUsername());
            p.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
            p.setPreferences(request.getPreferences());
            p.setRowVersion(1);
            p.setCreatedAt("2026-03-01T00:00:00Z");
            p.setUpdatedAt("2026-03-01T00:00:00Z");
            userMap.put(UUID.fromString(p.getId()), p);
            return p;
        }

        @Override
        public UserProfile updateUser(UserSecurityContext context, UUID id, UpdateUserProfileRequest request) {
            UserProfile p = userMap.get(id);
            if (p == null) {
                throw new UserProfileNotFoundException(id.toString(), context.getTenantId().toString());
            }
            if (request.getFullName() != null) p.setFullName(request.getFullName());
            if (request.getEmail() != null) p.setEmail(request.getEmail());
            if (request.getUsername() != null) p.setUsername(request.getUsername());
            p.setRowVersion(p.getRowVersion() + 1);
            return p;
        }

        @Override
        public UserProfile updateUserStatus(UserSecurityContext context, UUID id, UpdateUserStatusRequest request) {
            if (throwInvalidTransition) {
                throw new InvalidStatusTransitionException("ACTIVE", request.getStatus());
            }
            if (throwLastAdminGuard) {
                throw new InvalidTenantStateException("Cannot deactivate or suspend the last active tenant-wide administrator for this tenant");
            }
            UserProfile p = userMap.get(id);
            if (p == null) {
                throw new UserProfileNotFoundException(id.toString(), context.getTenantId().toString());
            }
            p.setStatus(request.getStatus());
            p.setRowVersion(p.getRowVersion() + 1);
            return p;
        }
    }
}
