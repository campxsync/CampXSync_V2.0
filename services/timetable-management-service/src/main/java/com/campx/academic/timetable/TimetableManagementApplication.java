package com.campx.academic.timetable;

import com.campx.academic.timetable.server.TimetableServer;
import com.campx.academic.timetable.service.TimetableDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Main application bootstrap for ACD-05 Timetable Management Service.
 */
public class TimetableManagementApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TimetableManagementApplication.class);
    private static final int DEFAULT_PORT = 8087;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        try {
            TimetableDomainService domainService = new TimetableDomainService();
            TimetableServer server = new TimetableServer(port, domainService);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, terminating ACD-05 Timetable Management Service...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("ACD-05 Timetable Management Service is running on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start ACD-05 Timetable Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
