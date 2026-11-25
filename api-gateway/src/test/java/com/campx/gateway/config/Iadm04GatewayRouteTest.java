package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * IADM-04 S1 — gateway prefix /api/v1/iadm/04 routes to port 8102 without clashing admin.
 */
public class Iadm04GatewayRouteTest {

    @Test
    public void iadm04RouteRegistered() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/iadm/04"));
        assertEquals("http://localhost:8102/api/v1/iadm/04", routeTable.get("/api/v1/iadm/04"));
        assertTrue("legacy admin catch-all must remain", routeTable.containsKey("/api/v1/admin"));
        assertTrue("IADM-01 sibling must remain", routeTable.containsKey("/api/v1/iadm/01"));
        assertTrue("IADM-02 sibling must remain", routeTable.containsKey("/api/v1/iadm/02"));
        assertTrue("IADM-03 sibling must remain", routeTable.containsKey("/api/v1/iadm/03"));
    }
}
