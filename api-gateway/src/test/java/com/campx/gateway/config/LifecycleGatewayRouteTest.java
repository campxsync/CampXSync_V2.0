package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LifecycleGatewayRouteTest {

    @Test
    public void lifecycleRoutesTargetStm04() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/lifecycle"));
        assertEquals("http://localhost:8096/api/v1/lifecycle", routeTable.get("/api/v1/lifecycle"));
        assertEquals("http://localhost:8096/api/v1/lifecycle", routeTable.get("/v1/lifecycle"));
    }
}
