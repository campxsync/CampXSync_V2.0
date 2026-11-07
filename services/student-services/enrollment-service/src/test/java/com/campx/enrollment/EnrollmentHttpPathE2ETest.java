package com.campx.enrollment;

import com.campx.enrollment.security.GatewayHmacVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Local HTTP path hardening for STM-03 register flow without browser auth dependencies.
 */
public class EnrollmentHttpPathE2ETest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private HttpServer server;
    private int port;
    private EnrollmentDomainService domain;
    private UUID tenant;
    private UUID student;
    private UUID registrar;
    private UUID program;
    private UUID faculty;
    private EnrollmentDomainService.Term term;
    private EnrollmentDomainService.Course course;
    private EnrollmentDomainService.Section section;
    private EnrollmentDomainService.Enrolment enrolment;

    @Before
    public void setUp() throws Exception {
        domain = new EnrollmentDomainService(Clock.fixed(NOW, ZoneOffset.UTC));
        tenant = UUID.randomUUID();
        student = UUID.randomUUID();
        registrar = UUID.randomUUID();
        program = UUID.randomUUID();
        faculty = UUID.randomUUID();
        Actor registrarActor = new Actor(tenant, registrar, "R31", "e2e-reg");
        term = domain.createTerm(registrarActor, "2026", "E2E", "E2E Term");
        domain.createWindow(registrarActor, term.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), 1, 12);
        course = domain.addCourse(registrarActor, program, term.id, "CS101", 3, true, java.util.Collections.<UUID>emptyList());
        section = domain.addSection(registrarActor, course.id, 10, faculty);
        enrolment = domain.openFromHandover(registrarActor, UUID.randomUUID(), student, program, term.id);

        final EnrollmentController controller = new EnrollmentController(domain, new GatewayHmacVerifier(null));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Map<String, String> headers = new HashMap<String, String>();
                for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
                    }
                }
                String body = read(exchange.getRequestBody());
                EnrollmentController.Response response = controller.handle(exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(), headers, body);
                byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void registerPathReturnsEnrolledThroughHttp() throws Exception {
        String path = "/api/v1/enrollment/enrolments/" + enrolment.id + "/register";
        String body = "{\"selections\":[{\"courseId\":\"" + course.id + "\",\"sectionId\":\"" + section.id + "\"}]}";
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", tenant.toString());
        conn.setRequestProperty("X-User-Id", student.toString());
        conn.setRequestProperty("X-User-Role", "R25");
        conn.setRequestProperty("X-Correlation-Id", "e2e-http");
        conn.setRequestProperty("Idempotency-Key", "e2e-key-1");
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int status = conn.getResponseCode();
        String response = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        assertEquals(response, 200, status);
        assertTrue(response.contains("\"status\":\"ENROLLED\""));

        HttpURLConnection get = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/v1/enrollment/enrolments/" + enrolment.id).openConnection();
        get.setRequestMethod("GET");
        get.setRequestProperty("X-Tenant-Id", tenant.toString());
        get.setRequestProperty("X-User-Id", student.toString());
        get.setRequestProperty("X-User-Role", "STUDENT");
        String getBody = read(get.getInputStream());
        assertEquals(200, get.getResponseCode());
        assertTrue(getBody.contains("ENROLLED"));
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int n;
        while ((n = input.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
