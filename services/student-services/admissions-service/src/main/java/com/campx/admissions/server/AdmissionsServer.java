package com.campx.admissions.server;

import com.campx.admissions.controller.ApplicationController;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded HTTP server hosting the Admissions Domain Service (STM-02) on port
 * 8094.
 */
public class AdmissionsServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsServer.class);
    public static final int DEFAULT_PORT = 8094;

    private final int port;
    private final ApplicationController controller;
    private HttpServer server;
    private boolean running = false;

    public AdmissionsServer(ApplicationController controller) {
        this(DEFAULT_PORT, controller);
    }

    public AdmissionsServer(int port, ApplicationController controller) {
        this.port = port;
        this.controller = controller;
    }

    public synchronized void start() throws IOException {
        if (running)
            return;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(null);

        server.createContext("/", new AdmissionsHttpHandler(controller));
        server.start();
        running = true;
        logger.info("CampXSync Admissions Domain Service (STM-02) started on 127.0.0.1:{}", port);
    }

    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("CampXSync Admissions Domain Service (STM-02) stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private static class AdmissionsHttpHandler implements HttpHandler {
        private final ApplicationController controller;

        public AdmissionsHttpHandler(ApplicationController controller) {
            this.controller = controller;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
                }
            }

            String body = readBody(exchange.getRequestBody());
            ApplicationController.HttpResponse resp = controller.handleRequest(method, path, headers, body);

            for (Map.Entry<String, String> header : resp.headers.entrySet()) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }

            byte[] bytes = resp.body != null ? resp.body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(resp.statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String readBody(InputStream is) throws IOException {
            if (is == null)
                return "";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
