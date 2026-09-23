package com.campx.logger;

import com.campx.logger.api.LogLevel;
import com.campx.logger.core.LogManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration test suite for the embedded {@link com.campx.logger.server.LoggerApiServer}.
 * <p>
 * Verifies live HTTP endpoints:
 * <ul>
 *   <li>Status check endpoint ({@code GET /api/v1/logger/status})</li>
 *   <li>Runtime severity level modification ({@code POST /api/v1/logger/level})</li>
 *   <li>Remote cross-service JSON log ingestion ({@code POST /api/v1/logs})</li>
 * </ul>
 */
public class LoggerApiServerTest {

    private static int serverPort = 9898;

    /**
     * Bootstraps the singleton {@link LogManager} and queries the configured API server port.
     */
    @BeforeClass
    public static void setup() {
        // Ensure LogManager and its API server are initialized
        LogManager.getInstance();
        serverPort = LogManager.getInstance().getConfig().getApiServerPort();
    }

    /**
     * Tests the status check endpoint, validating HTTP 200 response and JSON payload integrity.
     *
     * @throws Exception if network connection fails
     */
    @Test
    public void testGetStatusEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + serverPort + "/api/v1/logger/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);

        String responseBody = readResponse(conn);
        assertTrue(responseBody.contains("\"status\":\"UP\""));
        assertTrue(responseBody.contains("\"service\":\"CampXSync-Logger\""));
    }

    /**
     * Tests dynamic log level reconfiguration via HTTP POST and confirms internal configuration updates.
     *
     * @throws Exception if network connection fails
     */
    @Test
    public void testUpdateLevelEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + serverPort + "/api/v1/logger/level?level=WARN");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);

        String responseBody = readResponse(conn);
        assertTrue(responseBody.contains("\"level\":\"WARN\""));
        assertEquals(LogLevel.WARN, LogManager.getInstance().getConfig().getRootLevel());

        // Reset back to DEBUG for remaining tests
        LogManager.getInstance().setRootLevel(LogLevel.DEBUG);
    }

    /**
     * Tests the remote ingestion HTTP endpoint, confirming acceptance of external JSON-formatted events.
     *
     * @throws Exception if network connection fails
     */
    @Test
    public void testLogIngestionEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + serverPort + "/api/v1/logs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String payload = "{"
                + "\"level\":\"INFO\","
                + "\"logger\":\"python-ai-service\","
                + "\"message\":\"Student facial recognition attendance verified\","
                + "\"flowId\":\"FLOW-FACE-REC-99\","
                + "\"operation\":\"verifyStudentFace\""
                + "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        assertEquals(202, responseCode);

        String responseBody = readResponse(conn);
        assertTrue(responseBody.contains("\"status\":\"ACCEPTED\""));
    }

    /**
     * Reads the entire response string from an active HTTP connection.
     *
     * @param conn active HTTP connection
     * @return response payload string
     * @throws Exception if reading fails
     */
    private String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
