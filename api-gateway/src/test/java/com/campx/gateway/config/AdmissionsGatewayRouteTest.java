package com.campx.gateway.config;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that the API Gateway registers the canonical Admissions Domain routes
 * and correctly maps them to the Admissions Service downstream endpoint on port 8094.
 */
public class AdmissionsGatewayRouteTest {

    @Test
    public void testAdmissionsDomainRoutesRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue("Gateway must map /api/v1/admissions", routeTable.containsKey("/api/v1/admissions"));
        assertEquals("http://localhost:8094/api/v1/admissions", routeTable.get("/api/v1/admissions"));

        assertTrue("Gateway must map /v1/admissions", routeTable.containsKey("/v1/admissions"));
        assertEquals("http://localhost:8094/api/v1/admissions", routeTable.get("/v1/admissions"));

        assertTrue("Gateway must map /api/v1/applications", routeTable.containsKey("/api/v1/applications"));
        assertEquals("http://localhost:8094/api/v1/admissions/applications", routeTable.get("/api/v1/applications"));

        assertTrue("Gateway must map /v1/applications", routeTable.containsKey("/v1/applications"));
        assertEquals("http://localhost:8094/api/v1/admissions/applications", routeTable.get("/v1/applications"));
    }
}

