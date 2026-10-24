package com.campx.gateway.config;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that the API Gateway registers the canonical Timetable Domain routes
 * and correctly maps them to the Timetable Management Service downstream endpoint on port 8087.
 */
public class TimetableGatewayRouteTest {

    @Test
    public void testTimetableDomainRoutesRegistered() {
        GatewayConfig config = new GatewayConfig();
        Map<String, String> routeTable = config.getRouteTable();

        assertTrue("Gateway must map /api/v1/timetables", routeTable.containsKey("/api/v1/timetables"));
        assertEquals("http://localhost:8087/api/v1/academics/timetables", routeTable.get("/api/v1/timetables"));

        assertTrue("Gateway must map /api/v1/academics/timetables", routeTable.containsKey("/api/v1/academics/timetables"));
        assertEquals("http://localhost:8087/api/v1/academics/timetables", routeTable.get("/api/v1/academics/timetables"));
    }
}
