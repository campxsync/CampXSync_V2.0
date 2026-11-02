package com.campx.admissions;

import com.campx.admissions.controller.ApplicationController;
import com.campx.admissions.exception.*;
import com.campx.admissions.logging.AdmissionsLoggingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class AdmissionsExceptionHandlerTest {

    private AdmissionsExceptionHandler handler;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        handler = new AdmissionsExceptionHandler(objectMapper, new AdmissionsLoggingService("test"));
    }

    @Test
    public void testValidationExceptionMapsTo400ProblemDetails() throws Exception {
        AdmissionsValidationException ex = new AdmissionsValidationException("Invalid applicant age");
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications", "corr-001");

        assertEquals(400, response.statusCode);
        assertEquals("application/problem+json", response.headers.get("Content-Type"));

        JsonNode json = objectMapper.readTree(response.body);
        assertEquals(400, json.get("status").asInt());
        assertEquals("VALIDATION_FAILED", json.get("errorCode").asText());
        assertEquals("Invalid applicant age", json.get("detail").asText());
        assertEquals("corr-001", json.get("correlationId").asText());
        assertEquals("/api/v1/admissions/applications", json.get("instance").asText());
    }

    @Test
    public void testUnauthorizedExceptionMapsTo401() throws Exception {
        AdmissionsUnauthorizedException ex = new AdmissionsUnauthorizedException("Invalid gateway signature");
        ApplicationController.HttpResponse response = handler.handleException(ex, "GET", "/api/v1/admissions/intakes", "corr-002");

        assertEquals(401, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("UNAUTHORIZED", json.get("errorCode").asText());
    }

    @Test
    public void testSecurityViolationMapsTo403() throws Exception {
        AdmissionsSecurityViolationException ex = new AdmissionsSecurityViolationException("SoD violation: Evaluator cannot approve decision");
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications/1/decision", "corr-003");

        assertEquals(403, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("SECURITY_VIOLATION", json.get("errorCode").asText());
        assertTrue(json.get("detail").asText().contains("SoD violation"));
    }

    @Test
    public void testNotFoundExceptionMapsTo404() throws Exception {
        AdmissionsNotFoundException ex = new AdmissionsNotFoundException("Application not found: 123");
        ApplicationController.HttpResponse response = handler.handleException(ex, "GET", "/api/v1/admissions/applications/123", "corr-004");

        assertEquals(404, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("RESOURCE_NOT_FOUND", json.get("errorCode").asText());
    }

    @Test
    public void testConflictExceptionMapsTo409() throws Exception {
        AdmissionsConflictException ex = new AdmissionsConflictException("Intake capacity exhausted (100/100)");
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications/1/offers", "corr-005");

        assertEquals(409, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("CONFLICT", json.get("errorCode").asText());
        assertTrue(json.get("detail").asText().contains("capacity exhausted"));
    }

    @Test
    public void testLifecycleExceptionMapsTo422() throws Exception {
        AdmissionsLifecycleException ex = new AdmissionsLifecycleException("Illegal transition: DRAFT -> HANDED_OVER");
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications/1/submit", "corr-006");

        assertEquals(422, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("LIFECYCLE_TRANSITION_VIOLATION", json.get("errorCode").asText());
    }

    @Test
    public void testRateLimitExceededExceptionMapsTo429WithRetryAfterHeader() throws Exception {
        AdmissionsRateLimitExceededException ex = new AdmissionsRateLimitExceededException("Quota exceeded", 60);
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications", "corr-007");

        assertEquals(429, response.statusCode);
        assertEquals("60", response.headers.get("Retry-After"));
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("RATE_LIMIT_EXCEEDED", json.get("errorCode").asText());
    }

    @Test
    public void testBadGatewayExceptionMapsTo502() throws Exception {
        AdmissionsBadGatewayException ex = new AdmissionsBadGatewayException("STM-01 connection timed out");
        ApplicationController.HttpResponse response = handler.handleException(ex, "POST", "/api/v1/admissions/applications/1/handover", "corr-008");

        assertEquals(502, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("DOWNSTREAM_SERVICE_ERROR", json.get("errorCode").asText());
    }

    @Test
    public void testUnhandledRuntimeExceptionSanitizesMessageTo500() throws Exception {
        RuntimeException ex = new RuntimeException("PostgreSQL connection pool exhausted: jdbc:postgresql://10.0.0.1:5432/db");
        ApplicationController.HttpResponse response = handler.handleException(ex, "GET", "/api/v1/admissions/intakes", "corr-009");

        assertEquals(500, response.statusCode);
        JsonNode json = objectMapper.readTree(response.body);
        assertEquals("INTERNAL_SERVER_ERROR", json.get("errorCode").asText());
        // Internal database connection string should NEVER be leaked in detail
        assertFalse("Raw DB message must be sanitized", json.get("detail").asText().contains("jdbc:postgresql"));
        assertTrue("Detail must include correlationId reference", json.get("detail").asText().contains("corr-009"));
    }
}
