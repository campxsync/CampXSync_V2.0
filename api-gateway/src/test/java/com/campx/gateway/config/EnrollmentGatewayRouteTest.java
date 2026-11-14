package com.campx.gateway.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * STM-03-EN-004: API Gateway routes for Enrolment & Registration must target :8095.
 */
public class EnrollmentGatewayRouteTest {

    @Test
    public void enrollmentRoutesTargetStm03() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/enrollment"));
        assertEquals("http://localhost:8095/api/v1/enrollment", routeTable.get("/api/v1/enrollment"));
        assertEquals("http://localhost:8095/api/v1/enrollment", routeTable.get("/v1/enrollment"));
    }

    @Test
    public void stm03En004_gatewayMapsCanonicalAndLegacyEnrollmentPrefixes() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        String expected = "http://localhost:8095/api/v1/enrollment";
        assertEquals(expected, routeTable.get("/api/v1/enrollment"));
        assertEquals(expected, routeTable.get("/v1/enrollment"));
        assertFalse("Enrollment must not share the documents downstream",
                "http://localhost:8098/api/v1/documents".equals(routeTable.get("/api/v1/enrollment")));
        assertFalse("Enrollment must not share the lifecycle downstream",
                "http://localhost:8096/api/v1/lifecycle".equals(routeTable.get("/api/v1/enrollment")));
    }

    @Test
    public void stm03En004_enrollmentRouteDoesNotCollideWithStudentOrAdmissions() {
        Map<String, String> routeTable = new GatewayConfig().getRouteTable();
        assertTrue(routeTable.containsKey("/api/v1/students") || routeTable.containsKey("/api/v1/student"));
        assertTrue(routeTable.containsKey("/api/v1/admissions") || routeTable.containsKey("/api/v1/applications")
                || routeTable.containsKey("/api/v1/enrollment"));
        assertEquals("http://localhost:8095/api/v1/enrollment", routeTable.get("/api/v1/enrollment"));
    }
}
