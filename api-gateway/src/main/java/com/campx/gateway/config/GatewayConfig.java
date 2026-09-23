package com.campx.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the CampXSync API Gateway.
 * <p>
 * Registers route dispatch prefixes to downstream microservices
 * as specified in the CampXSync API Gateway Architecture Documentation:
 * <ul>
 *   <li><b>Platform Tier (ADM-01)</b>: Institute Admin Service on port 8081</li>
 *   <li><b>College Tier (ADM-02)</b>: College Admin Service on port 8082</li>
 *   <li><b>Academic Tier (ACD-01)</b>: Course Management Service on port 8083</li>
 * </ul>
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayConfig {

    /**
     * Listening port for the API Gateway HTTP server. Defaults to 8080.
     */
    private int port = 8080;

    /**
     * In-memory route table mapping URL path prefixes to target base URLs.
     */
    private final Map<String, String> routeTable = new LinkedHashMap<>();

    /**
     * Initializes default route table entries mapping client ingress paths
     * to downstream microservices across Platform, College, and Academic tiers.
     */
    public GatewayConfig() {
        // Core V2.0 / V3.0 User Story Ingress Routes
        routeTable.put("/api/v1/admin", "http://localhost:8081/api/v1/admin");
        routeTable.put("/api/v1/college-admin", "http://localhost:8082/api/v1/college-admin");
        // Academic Tier -> Course Management Service (Port 8083)
        routeTable.put("/api/v1/courses", "http://localhost:8083/api/v1/courses");
        routeTable.put("/api/v1/academics/courses", "http://localhost:8083/api/v1/academics/courses");

        // Academic Tier -> Curriculum Management Service (Port 8084)
        routeTable.put("/api/v1/curricula/metrics", "http://localhost:8084/metrics");
        routeTable.put("/api/v1/curricula", "http://localhost:8084/api/v1/curricula");
        routeTable.put("/api/v1/academics/curricula", "http://localhost:8084/api/v1/academics/curricula");
        routeTable.put("/api/v1/active", "http://localhost:8084/api/v1/curricula/active");

        // Academic Tier -> Subject Management Service (Port 8085)
        routeTable.put("/api/v1/subjects", "http://localhost:8085/api/v1/subjects");
        routeTable.put("/api/v1/academics/subjects", "http://localhost:8085/api/v1/academics/subjects");

        // Canonical Documented Gateway Route Prefixes
        // Platform Tier -> Institute Admin Service (Port 8081)
        routeTable.put("/v1/institutes", "http://localhost:8081/api/v1/admin/institutes");
        routeTable.put("/v1/platform-configs", "http://localhost:8081/api/v1/admin/configuration");
        routeTable.put("/v1/platform-roles", "http://localhost:8081/api/v1/admin/roles");
        routeTable.put("/v1/billing-accounts", "http://localhost:8081/api/v1/admin/billing/plans");
        routeTable.put("/v1/platform-audit-logs", "http://localhost:8081/api/v1/admin/audit-logs");

        // College Tier -> College Admin Service (Port 8082)
        routeTable.put("/v1/college-profile", "http://localhost:8082/api/v1/college-admin/profile");
        routeTable.put("/v1/departments", "http://localhost:8082/api/v1/college-admin/departments");
        routeTable.put("/v1/programs", "http://localhost:8082/api/v1/college-admin/programs");
        routeTable.put("/v1/college-configs", "http://localhost:8082/api/v1/college-admin/settings");
        routeTable.put("/v1/imports", "http://localhost:8082/api/v1/college-admin/imports");
        routeTable.put("/v1/documents", "http://localhost:8082/api/v1/college-admin/documents");

        // Academic Tier -> Course Management Service (Port 8083)
        routeTable.put("/v1/courses", "http://localhost:8083/api/v1/courses");
        routeTable.put("/v1/course-catalog", "http://localhost:8083/api/v1/courses/catalog");

        // Academic Tier -> Curriculum Management Service (Port 8084)
        routeTable.put("/v1/curricula", "http://localhost:8084/api/v1/curricula");
        routeTable.put("/v1/curriculum-catalog", "http://localhost:8084/api/v1/curricula/active");

        // Academic Tier -> Subject Management Service (Port 8085)
        routeTable.put("/v1/subjects", "http://localhost:8085/api/v1/subjects");
        routeTable.put("/v1/subject-catalog", "http://localhost:8085/api/v1/academics/subjects/catalog");
    }

    /**
     * Gets the listening HTTP port.
     *
     * @return The integer port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the listening HTTP port.
     *
     * @param port The integer port number to bind to.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Retrieves an unmodifiable or mutable view of the gateway route table.
     *
     * @return Map where keys are URL prefixes and values are target destination base URLs.
     */
    public Map<String, String> getRouteTable() {
        return routeTable;
    }

    /**
     * Registers a new route mapping dynamically.
     *
     * @param pathPrefix    The ingress path prefix (e.g. "/v1/exams").
     * @param targetBaseUrl The downstream base URL (e.g. "http://localhost:8084/api/v1/exams").
     */
    public void addRoute(String pathPrefix, String targetBaseUrl) {
        this.routeTable.put(pathPrefix, targetBaseUrl);
    }
}
