package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnrollmentGatewayRouteTest {

    @Test
    public void enrollmentRoutesTargetStm03() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/enrollment"));
        assertEquals("http://localhost:8095/api/v1/enrollment", routeTable.get("/api/v1/enrollment"));
        assertEquals("http://localhost:8095/api/v1/enrollment", routeTable.get("/v1/enrollment"));
    }
}
