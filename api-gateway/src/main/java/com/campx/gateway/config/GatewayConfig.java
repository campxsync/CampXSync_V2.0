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
 *   <li><b>Academic Tier (ACD-02)</b>: Curriculum Management Service on port 8084</li>
 *   <li><b>Academic Tier (ACD-03)</b>: Subject Management Service on port 8085</li>
 *   <li><b>Academic Tier (ACD-04)</b>: Batch Management Service on port 8086</li>
 *   <li><b>Academic Tier (ACD-05)</b>: Timetable Management Service on port 8087</li>
 *   <li><b>Academic Tier (ACD-06)</b>: Attendance Management Service on port 8088</li>
 *   <li><b>Academic Tier (ACD-07)</b>: Academic Calendar Service on port 8089</li>
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
        routeTable.put("/api/v1/subjects/metrics", "http://localhost:8085/metrics");
        routeTable.put("/api/v1/academics/subjects/metrics", "http://localhost:8085/metrics");
        routeTable.put("/api/v1/subjects", "http://localhost:8085/api/v1/subjects");
        routeTable.put("/api/v1/academics/subjects", "http://localhost:8085/api/v1/academics/subjects");

        // Academic Tier -> Batch Management Service (Port 8086)
        routeTable.put("/api/v1/batches/metrics", "http://localhost:8086/metrics");
        routeTable.put("/api/v1/academics/batches/metrics", "http://localhost:8086/metrics");
        routeTable.put("/api/v1/batches", "http://localhost:8086/api/v1/academics/batches");
        routeTable.put("/api/v1/academics/batches", "http://localhost:8086/api/v1/academics/batches");

        // Academic Tier -> Timetable Management Service (Port 8087)
        routeTable.put("/api/v1/timetables/metrics", "http://localhost:8087/metrics");
        routeTable.put("/api/v1/academics/timetables/metrics", "http://localhost:8087/metrics");
        routeTable.put("/api/v1/timetables", "http://localhost:8087/api/v1/academics/timetables");
        routeTable.put("/api/v1/academics/timetables", "http://localhost:8087/api/v1/academics/timetables");
        routeTable.put("/api/v1/export", "http://localhost:8087/api/v1/academics/timetables/export");

        // Academic Tier -> Attendance Management Service (Port 8088)
        routeTable.put("/api/v1/attendance/metrics", "http://localhost:8088/metrics");
        routeTable.put("/api/v1/academics/attendance/metrics", "http://localhost:8088/metrics");
        routeTable.put("/api/v1/attendance", "http://localhost:8088/api/v1/academics/attendance");
        routeTable.put("/api/v1/academics/attendance", "http://localhost:8088/api/v1/academics/attendance");

        // Academic Tier -> Academic Calendar Service (Port 8089)
        routeTable.put("/api/v1/calendars/metrics", "http://localhost:8089/metrics");
        routeTable.put("/api/v1/academics/calendars/metrics", "http://localhost:8089/metrics");
        routeTable.put("/api/v1/calendars", "http://localhost:8089/api/v1/academics/calendars");
        routeTable.put("/api/v1/academics/calendars", "http://localhost:8089/api/v1/academics/calendars");
        routeTable.put("/api/v1/terms", "http://localhost:8089/api/v1/academics/terms");
        routeTable.put("/api/v1/academics/terms", "http://localhost:8089/api/v1/academics/terms");
        routeTable.put("/api/v1/events", "http://localhost:8089/api/v1/academics/events");
        routeTable.put("/api/v1/academics/events", "http://localhost:8089/api/v1/academics/events");

        // Canonical Documented Gateway Route Prefixes
        // Platform Tier -> Institute Admin Service (Port 8081)
        routeTable.put("/v1/institutes", "http://localhost:8081/api/v1/admin/institutes");
        routeTable.put("/v1/platform-configs", "http://localhost:8081/api/v1/admin/configuration");
        routeTable.put("/v1/platform-roles", "http://localhost:8081/api/v1/admin/roles");
        routeTable.put("/v1/billing-accounts", "http://localhost:8081/api/v1/admin/billing/plans");
        routeTable.put("/v1/platform-audit-logs", "http://localhost:8081/api/v1/admin/audit-logs");

        routeTable.put("/v1/college-profile", "http://localhost:8082/api/v1/college-admin/profile");
        routeTable.put("/v1/departments", "http://localhost:8082/api/v1/college-admin/departments");
        routeTable.put("/v1/programs", "http://localhost:8082/api/v1/college-admin/programs");
        routeTable.put("/v1/college-configs", "http://localhost:8082/api/v1/college-admin/settings");
        routeTable.put("/v1/imports", "http://localhost:8082/api/v1/college-admin/imports");
        routeTable.put("/v1/documents", "http://localhost:8082/api/v1/college-admin/documents");
        routeTable.put("/v1/college-workflows", "http://localhost:8082/api/v1/college-admin/workflows");
        routeTable.put("/v1/batch-approvals", "http://localhost:8082/api/v1/college-admin/workflows/batch-approvals");
        routeTable.put("/v1/college-approvals", "http://localhost:8082/api/v1/college-admin/approvals");
        routeTable.put("/v1/college-roles", "http://localhost:8082/api/v1/college-admin/roles");
        routeTable.put("/v1/college-permissions", "http://localhost:8082/api/v1/college-admin/permissions");
        routeTable.put("/v1/college-audit-logs", "http://localhost:8082/api/v1/college-admin/audit-logs");

        // Academic Tier -> Course Management Service (Port 8083)
        routeTable.put("/v1/courses", "http://localhost:8083/api/v1/courses");
        routeTable.put("/v1/course-catalog", "http://localhost:8083/api/v1/courses/catalog");

        // Academic Tier -> Curriculum Management Service (Port 8084)
        routeTable.put("/v1/curricula", "http://localhost:8084/api/v1/curricula");
        routeTable.put("/v1/curriculum-catalog", "http://localhost:8084/api/v1/curricula/active");

        // Academic Tier -> Subject Management Service (Port 8085)
        routeTable.put("/v1/subjects", "http://localhost:8085/api/v1/subjects");
        routeTable.put("/v1/subject-catalog", "http://localhost:8085/api/v1/academics/subjects/catalog");

        // Academic Tier -> Batch Management Service (Port 8086)
        routeTable.put("/v1/batches", "http://localhost:8086/api/v1/academics/batches");
        routeTable.put("/v1/batch-catalog", "http://localhost:8086/api/v1/academics/batches");

        // Academic Tier -> Timetable Management Service (Port 8087)
        routeTable.put("/v1/timetables", "http://localhost:8087/api/v1/academics/timetables");
        routeTable.put("/v1/timetable-catalog", "http://localhost:8087/api/v1/academics/timetables");

        // Academic Tier -> Attendance Management Service (Port 8088)
        routeTable.put("/v1/attendance", "http://localhost:8088/api/v1/academics/attendance");
        routeTable.put("/v1/attendance-sessions", "http://localhost:8088/api/v1/academics/attendance/sessions");
        routeTable.put("/v1/attendance-summaries", "http://localhost:8088/api/v1/academics/attendance/summaries");
        routeTable.put("/v1/attendance-reports", "http://localhost:8088/api/v1/academics/attendance/report");

        // Academic Tier -> Academic Calendar Service (Port 8089)
        routeTable.put("/v1/calendars", "http://localhost:8089/api/v1/academics/calendars");
        routeTable.put("/v1/calendar-terms", "http://localhost:8089/api/v1/academics/terms");
        routeTable.put("/v1/calendar-events", "http://localhost:8089/api/v1/academics/events");
        routeTable.put("/v1/calendar-catalog", "http://localhost:8089/api/v1/academics/calendars");
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
