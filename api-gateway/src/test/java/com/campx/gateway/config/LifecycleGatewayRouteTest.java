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
        String route1 = routeTable.get("/api/v1/lifecycle");
        assertTrue(route1.equals("http://localhost:8096/api/v1/lifecycle") || route1.equals("http://127.0.0.1:8096/api/v1/lifecycle"));
        String route2 = routeTable.get("/v1/lifecycle");
        assertTrue(route2.equals("http://localhost:8096/api/v1/lifecycle") || route2.equals("http://127.0.0.1:8096/api/v1/lifecycle"));
    }
}
