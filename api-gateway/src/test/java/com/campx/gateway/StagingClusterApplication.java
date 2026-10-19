package com.campx.gateway;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.academic.course.server.CourseServer;
import com.campx.academic.course.service.CourseDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Standalone Staging Cluster Application.
 * Boots the unified CampXSync services suite backed by real Supabase Staging:
 * - GatewayServer (8080)
 * - InstituteAdminServer (8081)
 * - CollegeAdminServer (8082)
 * - CourseServer (8083) with PostgreSQL RLS persistence
 */
public class StagingClusterApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StagingClusterApplication.class);

    public static void main(String[] args) {
        try {
            System.setProperty("campx.persistence.mode", "postgres");
            System.setProperty("campx.internal.auth.enabled", "false");

            logger.info("=================================================");
            logger.info("Starting CampXSync Staging Cluster Suite");
            logger.info("Persistence Mode: POSTGRESQL STAGING");
            logger.info("=================================================");

            // 1. Institute Admin Service (Port 8081)
            InstituteAdminServer instituteServer = new InstituteAdminServer(8081, new InstituteAdminDomainService());
            instituteServer.start();
            logger.info("[ADM-01] Institute Admin Service active on port 8081");

            // 2. College Admin Service (Port 8082)
            CollegeAdminServer collegeServer = new CollegeAdminServer(8082, new CollegeAdminDomainService());
            collegeServer.start();
            logger.info("[ADM-02] College Admin Service active on port 8082");

            // 3. Course Management Service (Port 8083)
            com.campx.academic.course.repository.CourseRepository postgresCourseRepo =
                    new com.campx.academic.course.repository.PostgresCourseRepository();
            CourseServer courseServer = new CourseServer(8083, new CourseDomainService(postgresCourseRepo));
            courseServer.start();
            logger.info("[ACD-01] Course Management Service active on port 8083 with PostgresCourseRepository");

            // 4. API Gateway (Port 8080)
            GatewayConfig config = new GatewayConfig();
            config.setPort(8080);
            GatewayServer gatewayServer = new GatewayServer(config);
            gatewayServer.start();
            logger.info("[GATEWAY] CampXSync API Gateway active on port 8080");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down CampXSync Staging Cluster...");
                gatewayServer.stop();
                courseServer.stop();
                collegeServer.stop();
                instituteServer.stop();
                CampXLoggerFactory.flush();
                CampXLoggerFactory.shutdown();
                logger.info("CampXSync Staging Cluster shutdown complete.");
            }));

            logger.info("All CampXSync staging backend services operational. Gateway ready on http://localhost:8080");

            // Keep main thread running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start CampXSync Staging Cluster: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
