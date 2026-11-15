package com.campx.admin.institute.controller;

import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.repository.InMemoryDelegationRepository;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.security.InMemoryRoleCodeLookup;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.admin.institute.service.DelegationAuthorizationService;
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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * HTTP contract tests for Path C active-delegation check endpoint.
 */
public class DelegationActiveCheckHttpTest {

    private static int TEST_PORT;
    private static HttpServer server;
    private static InMemoryDelegationRepository repo;
    private static InMemoryRoleCodeLookup roles;
    private static DelegationAuthorizationService authService;

    private static final UUID TENANT = UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d");
    private static final UUID SUBJECT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FROM = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ROLE_R31 = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @BeforeClass
    public static void startServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            TEST_PORT = socket.getLocalPort();
        }
        repo = new InMemoryDelegationRepository();
        roles = new InMemoryRoleCodeLookup();
        roles.put(ROLE_R31, "R31");
        authService = new DelegationAuthorizationService(repo, roles);
        InstituteAdminController controller = new InstituteAdminController(
                new InstituteAdminDomainService(),
                null,
                null,
                new GatewayHmacVerifier(false, null, 60),
                authService);
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
    public void resetDelegations() {
        // recreate clean repo state by revoking known rows via new service state is hard;
        // seed only in tests that need it; revoke any leftover by listing
        UserSecurityContext ctx = new UserSecurityContext(SUBJECT, TENANT);
        for (Delegation d : repo.listDelegations(ctx)) {
            repo.revokeDelegation(ctx, d.getId());
        }
    }

    private HttpURLConnection get(String path, UUID userId) throws IOException {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Id", userId.toString());
        conn.setRequestProperty("X-Tenant-Id", TENANT.toString());
        conn.setRequestProperty("X-User-Role", "REGISTRAR");
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
    public void httpAllowsActiveDelegation() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(SUBJECT, TENANT);
        Delegation d = new Delegation();
        d.setFromUserId(FROM.toString());
        d.setToUserId(SUBJECT.toString());
        d.setRoleId(ROLE_R31);
        d.setValidFrom(System.currentTimeMillis() - 60_000L);
        d.setValidTo(System.currentTimeMillis() + 3_600_000L);
        d.setStatus("ACTIVE");
        repo.createDelegation(ctx, d);

        HttpURLConnection conn = get(DelegationAuthorizationService.PATH
                + "?userId=" + SUBJECT + "&permissionKey=STM-06.DOCUMENTS.A", SUBJECT);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("\"allowed\":true"));
        assertTrue(body.contains("ACTIVE_R31_DELEGATION"));
    }

    @Test
    public void httpDeniesMissingDelegation() throws Exception {
        HttpURLConnection conn = get(DelegationAuthorizationService.PATH
                + "?userId=" + SUBJECT + "&permissionKey=STM-06.DOCUMENTS.A", SUBJECT);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("\"allowed\":false"));
        assertTrue(body.contains("NO_ACTIVE_REGISTRAR_DELEGATION"));
    }

    @Test
    public void httpDeniesUnauthorizedPermissionKey() throws Exception {
        HttpURLConnection conn = get(DelegationAuthorizationService.PATH
                + "?userId=" + SUBJECT + "&permissionKey=FIN.PAYROLL.A", SUBJECT);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("UNAUTHORIZED_PERMISSION_KEY"));
    }
}
