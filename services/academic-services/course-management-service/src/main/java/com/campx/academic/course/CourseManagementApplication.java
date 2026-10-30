package com.campx.academic.course;

import com.campx.academic.course.server.CourseServer;
import com.campx.academic.course.service.CourseDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Standalone entrypoint for launching ACD-01: Course Management Service.
 * Binds to port 8083, wires up the domain service, and manages service lifecycle.
 */
public class CourseManagementApplication {

    /**
     * Structured logger instance for application bootstrap.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CourseManagementApplication.class);

    /**
     * Default TCP port (8083) for the Course Management HTTP server.
     */
    private static final int DEFAULT_PORT = 8083;

    /**
     * Launches the Course Management Service on the specified or default port.
     *
     * @param args optional command-line arguments; args[0] may provide an alternative port number
     */
    public static void main(String[] args) {
        try {
            int port = DEFAULT_PORT;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            CourseServer server = new CourseServer(port, new CourseDomainService());
            server.start();

            logger.info("CampXSync ACD-01 Course Management Service is operational on port {}", server.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down CampXSync ACD-01 Course Management Service...");
                server.stop();
                CampXLoggerFactory.shutdown();
            }));

        } catch (Exception e) {
            logger.error("Failed to start ACD-01 Course Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
