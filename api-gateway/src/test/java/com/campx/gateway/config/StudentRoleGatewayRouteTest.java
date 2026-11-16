package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies STM-07 IAM student-role assign/revoke route prefix on the API Gateway.
 */
public class StudentRoleGatewayRouteTest {

    @Test
    public void testStudentRoleInternalRouteRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue(routeTable.containsKey("/api/v1/admin/internal/roles"));
        assertEquals("http://localhost:8081/api/v1/admin/internal/roles",
                routeTable.get("/api/v1/admin/internal/roles"));
        assertTrue(routeTable.containsKey("/api/v1/admin"));
    }
}
