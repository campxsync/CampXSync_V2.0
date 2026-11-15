package com.campx.lifecycle.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Contract tests for Path C {@link HttpIamDelegationPort} against a fake IAM HTTP responder.
 */
public class HttpIamDelegationPortTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> responseJson = new AtomicReference<String>("{\"allowed\":true}");
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger hits = new AtomicInteger(0);

    @Before
    public void startFakeIam() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        hits.set(0);
        responseStatus.set(200);
        responseJson.set("{\"allowed\":true,\"reason\":\"ACTIVE_R31_DELEGATION\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/v1/admin/internal/delegations/active-check", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                hits.incrementAndGet();
                byte[] bytes = responseJson.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();
        System.setProperty("campx.internal.auth.enabled", "false");
    }

    @After
    public void stopFakeIam() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("campx.internal.auth.enabled");
    }

    @Test
    public void allowsWhenIamReturnsAllowedTrue() {
        HttpIamDelegationPort portClient = new HttpIamDelegationPort("http://127.0.0.1:" + port, null);
        assertTrue(portClient.hasActiveDelegation(
                UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "STM-04.LIFECYCLE.A"));
        assertEquals(1, hits.get());
    }

    @Test
    public void deniesWhenIamReturnsAllowedFalse() {
        responseJson.set("{\"allowed\":false,\"reason\":\"NO_ACTIVE_REGISTRAR_DELEGATION\"}");
        HttpIamDelegationPort portClient = new HttpIamDelegationPort("http://127.0.0.1:" + port, null);
        assertFalse(portClient.hasActiveDelegation(
                UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "STM-04.LIFECYCLE.A"));
    }

    @Test
    public void failClosedWhenIamUnavailable() {
        responseStatus.set(503);
        responseJson.set("{\"error\":\"down\"}");
        HttpIamDelegationPort portClient = new HttpIamDelegationPort("http://127.0.0.1:" + port, null);
        assertFalse(portClient.hasActiveDelegation(
                UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "STM-04.LIFECYCLE.A"));
    }

    @Test
    public void failClosedWhenHostUnreachable() {
        HttpIamDelegationPort portClient = new HttpIamDelegationPort("http://127.0.0.1:1", null, 200, 200);
        assertFalse(portClient.hasActiveDelegation(
                UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "STM-04.LIFECYCLE.A"));
    }

    @Test
    public void factoryFailClosedWithoutBaseUrl() {
        System.clearProperty("campx.iam.delegation.baseUrl");
        DelegationPort port = DelegationPortFactory.fromEnvironment();
        assertTrue(port instanceof FailClosedDelegationPort);
    }

    @Test
    public void noCacheEachCallHitsIam() {
        HttpIamDelegationPort portClient = new HttpIamDelegationPort("http://127.0.0.1:" + port, null);
        UUID tenant = UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d");
        UUID user = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        portClient.hasActiveDelegation(tenant, user, "STM-04.LIFECYCLE.A");
        portClient.hasActiveDelegation(tenant, user, "STM-04.LIFECYCLE.A");
        assertEquals(2, hits.get());
    }
}
