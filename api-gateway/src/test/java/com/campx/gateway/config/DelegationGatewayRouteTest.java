package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies Path C IAM delegation check route is registered on the API Gateway.
 */
public class DelegationGatewayRouteTest {

    @Test
    public void testDelegationActiveCheckRouteRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue(routeTable.containsKey("/api/v1/admin/internal/delegations"));
        assertEquals("http://localhost:8081/api/v1/admin/internal/delegations",
                routeTable.get("/api/v1/admin/internal/delegations"));
        assertTrue("Prefix /api/v1/admin must remain for institute-admin",
                routeTable.containsKey("/api/v1/admin"));
    }
}
