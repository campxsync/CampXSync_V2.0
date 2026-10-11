package com.campx.academic.calendar;

import com.campx.academic.calendar.server.CalendarServer;
import com.campx.academic.calendar.service.CalendarDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Bootstrap entry point for CampXSync ACD-07 Academic Calendar Service.
 * Defaults to port 8089 unless overridden via environment variable PORT or CLI argument.
 */
public class AcademicCalendarApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AcademicCalendarApplication.class);
    private static final int DEFAULT_PORT = 8089;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid CLI port argument '{}', using default {}", args[0], DEFAULT_PORT);
            }
        } else if (System.getenv("PORT") != null) {
            try {
                port = Integer.parseInt(System.getenv("PORT"));
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT env variable '{}', using default {}", System.getenv("PORT"), DEFAULT_PORT);
            }
        }

        try {
            CalendarServer server = new CalendarServer(port, new CalendarDomainService());
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping Academic Calendar Service...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("CampXSync ACD-07 Academic Calendar Service running on port {}", port);
        } catch (Exception ex) {
            logger.error("Failed to start ACD-07 Academic Calendar Service: {}", ex.getMessage(), ex);
            System.exit(1);
        }
    }
}
