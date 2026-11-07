package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GuardianGatewayRouteTest {

    @Test
    public void guardianRoutesTargetStm05() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/guardians"));
        assertEquals("http://localhost:8097/api/v1/guardians", routeTable.get("/api/v1/guardians"));
        assertEquals("http://localhost:8097/api/v1/guardians", routeTable.get("/v1/guardians"));
    }
}
