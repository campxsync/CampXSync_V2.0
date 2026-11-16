package com.campx.admin.institute.controller;

import com.campx.admin.institute.repository.InMemoryStudentRoleAssignmentRepository;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.service.DelegationAuthorizationService;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.admin.institute.service.StudentRoleAssignmentService;
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
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * HTTP contract tests for STM-07 student role assign/revoke endpoints.
 */
public class StudentRoleAssignmentHttpTest {

    private static int TEST_PORT;
    private static HttpServer server;
    private static InMemoryStudentRoleAssignmentRepository repo;

    private static final UUID TENANT = UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d");
    private static final UUID OPERATOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STUDENT_USER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_ROLE = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @BeforeClass
    public static void startServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            TEST_PORT = socket.getLocalPort();
        }
        repo = new InMemoryStudentRoleAssignmentRepository();
        repo.seedStudentRole(TENANT, STUDENT_ROLE);
        StudentRoleAssignmentService roleService = new StudentRoleAssignmentService(repo);
        InstituteAdminController controller = new InstituteAdminController(
                new InstituteAdminDomainService(),
                null,
                null,
                new GatewayHmacVerifier(false, null, 60),
                new DelegationAuthorizationService(),
                roleService);
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

    private HttpURLConnection post(String path, String json) throws IOException {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Id", OPERATOR.toString());
        conn.setRequestProperty("X-Tenant-Id", TENANT.toString());
        conn.setRequestProperty("X-User-Role", "SYSTEM_ADMIN");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        return conn;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    @Test
    public void httpAssignThenRevokeStudentRole() throws Exception {
        HttpURLConnection assign = post(StudentRoleAssignmentService.ASSIGN_PATH,
                "{\"userId\":\"" + STUDENT_USER + "\",\"roleCode\":\"R25\"}");
        assertEquals(200, assign.getResponseCode());
        assertTrue(readBody(assign).contains("ASSIGNED"));
        assertTrue(repo.hasActiveAssignment(TENANT, STUDENT_USER));

        HttpURLConnection revoke = post(StudentRoleAssignmentService.REVOKE_PATH,
                "{\"userId\":\"" + STUDENT_USER + "\",\"roleCode\":\"R25\"}");
        assertEquals(200, revoke.getResponseCode());
        assertTrue(readBody(revoke).contains("REVOKED"));
        assertFalse(repo.hasActiveAssignment(TENANT, STUDENT_USER));
    }

    @Test
    public void httpDeniesNonStudentRole() throws Exception {
        HttpURLConnection conn = post(StudentRoleAssignmentService.ASSIGN_PATH,
                "{\"userId\":\"" + STUDENT_USER + "\",\"roleCode\":\"FACULTY\"}");
        assertEquals(400, conn.getResponseCode());
    }
}
