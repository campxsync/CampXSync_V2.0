package com.campx.gateway.config;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that the API Gateway registers the canonical Student Domain routes
 * and correctly maps them to the Student Service downstream endpoint on port 8093.
 */
public class StudentGatewayRouteTest {

    @Test
    public void testStudentDomainRoutesRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue("Gateway must map /api/v1/students", routeTable.containsKey("/api/v1/students"));
        assertEquals("http://localhost:8093/api/v1/students", routeTable.get("/api/v1/students"));

        assertTrue("Gateway must map /v1/students", routeTable.containsKey("/v1/students"));
        assertEquals("http://localhost:8093/api/v1/students", routeTable.get("/v1/students"));

        assertTrue("Gateway must map /v1/student-roster", routeTable.containsKey("/v1/student-roster"));
        assertEquals("http://localhost:8093/api/v1/students", routeTable.get("/v1/student-roster"));
    }
}
