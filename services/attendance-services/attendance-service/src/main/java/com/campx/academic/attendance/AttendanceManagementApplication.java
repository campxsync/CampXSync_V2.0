package com.campx.academic.attendance;

import com.campx.academic.attendance.server.AttendanceServer;
import com.campx.academic.attendance.service.AttendanceDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.IOException;

/**
 * Bootstrap application entry point for ACD-06: Attendance Management Service.
 */
public class AttendanceManagementApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AttendanceManagementApplication.class);
    public static final int DEFAULT_PORT = 8088;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid CLI port argument '{}', falling back to default port {}", args[0], DEFAULT_PORT);
            }
        } else {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.trim().isEmpty()) {
                try {
                    port = Integer.parseInt(envPort.trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid PORT env variable '{}', falling back to default port {}", envPort, DEFAULT_PORT);
                }
            }
        }

        try {
            String mode = System.getProperty("campx.persistence.mode");
            if (mode == null || mode.trim().isEmpty()) {
                mode = System.getenv("CAMPX_PERSISTENCE_MODE");
            }
            if (mode == null || mode.trim().isEmpty()) {
                logger.error("FATAL: Missing required persistence configuration. 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be set explicitly to 'postgres' (or 'in_memory' for local testing).");
                throw new IllegalStateException("Missing required persistence configuration: 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be explicitly configured.");
            }

            AttendanceDomainService domainService = new AttendanceDomainService();
            AttendanceServer server = new AttendanceServer(port, domainService);

            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            logger.info("ACD-06 Attendance Management Service is running on port {} with persistence mode: {}", port, mode);
        } catch (IOException e) {
            logger.error("Failed to start ACD-06 Attendance Management Service on port {}: {}", port, e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Failed to start ACD-06 Attendance Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
