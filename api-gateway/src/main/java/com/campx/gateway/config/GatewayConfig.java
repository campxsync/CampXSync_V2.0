package com.campx.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the CampXSync API Gateway.
 * Registers route dispatch prefixes to downstream microservices
 * as specified in the CampXSync API Gateway Architecture Documentation.
 */
public class GatewayConfig {

    private int port = 8080;
    private final Map<String, String> routeTable = new LinkedHashMap<>();

    public GatewayConfig() {
        // Core V2.0 / V3.0 User Story Ingress Routes
        routeTable.put("/api/v1/admin", "http://localhost:8081/api/v1/admin");
        routeTable.put("/api/v1/college-admin", "http://localhost:8082/api/v1/college-admin");

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
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, String> getRouteTable() {
        return routeTable;
    }

    public void addRoute(String pathPrefix, String targetBaseUrl) {
        this.routeTable.put(pathPrefix, targetBaseUrl);
    }
}
