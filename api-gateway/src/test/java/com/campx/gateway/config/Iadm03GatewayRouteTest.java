package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * IADM-03-US-002 — gateway prefix /api/v1/iadm/03 routes to port 8101 without clashing admin.
 */
public class Iadm03GatewayRouteTest {

    @Test
    public void iadm03RouteRegistered() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/iadm/03"));
        assertEquals("http://localhost:8101/api/v1/iadm/03", routeTable.get("/api/v1/iadm/03"));
        assertTrue("legacy admin catch-all must remain", routeTable.containsKey("/api/v1/admin"));
        assertTrue("IADM-01 sibling must remain", routeTable.containsKey("/api/v1/iadm/01"));
        assertTrue("IADM-02 sibling must remain", routeTable.containsKey("/api/v1/iadm/02"));
        assertTrue("IADM-04 sibling must remain", routeTable.containsKey("/api/v1/iadm/04"));
    }
}
