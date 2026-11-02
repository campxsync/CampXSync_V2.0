package com.campx.admissions;

import com.campx.admissions.logging.AdmissionsLoggingService;
import com.campx.admissions.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class AdmissionsLoggingServiceTest {

    private AdmissionsLoggingService loggingService;

    @Before
    public void setUp() {
        loggingService = new AdmissionsLoggingService("test-admissions");
    }

    @Test
    public void testEmailMasking() {
        String raw = "Applicant email is student.admissions@university.edu for application.";
        String masked = AdmissionsLoggingService.maskPii(raw);
        assertFalse("Original email should not be visible", masked.contains("student.admissions@university.edu"));
        assertTrue("Masked email should preserve first letter and domain", masked.contains("s***s@university.edu"));
    }

    @Test
    public void testPhoneNumberMasking() {
        String raw = "Applicant contact phone is +919876543210 in profile.";
        String masked = AdmissionsLoggingService.maskPii(raw);
        assertFalse("Full phone number should not be visible", masked.contains("987654"));
        assertTrue("Masked phone should retain country prefix and suffix, was: " + masked, masked.contains("+91*****3210"));
    }

    @Test
    public void testSecretAndTokenRedaction() {
        String raw = "Payload has password=\"SuperSecret123!\" and token: 'jwt-abc-12345'";
        String masked = AdmissionsLoggingService.maskPii(raw);
        assertFalse("Raw password must be redacted", masked.contains("SuperSecret123!"));
        assertFalse("Raw token must be redacted", masked.contains("jwt-abc-12345"));
        assertTrue("Masked string should contain [REDACTED]", masked.contains("[REDACTED]"));
    }

    @Test
    public void testCrlfInjectionSanitization() {
        String tainted = "Line1\r\nInjected Log Entry\nLine3";
        String sanitized = AdmissionsLoggingService.sanitize(tainted);
        assertFalse("Carriage returns must be stripped", sanitized.contains("\r"));
        assertFalse("Newlines must be stripped", sanitized.contains("\n"));
        assertTrue(sanitized.contains("Line1__Injected Log Entry_Line3"));
    }

    @Test
    public void testLoggingMethodsExecuteWithoutExceptions() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        String correlationId = "corr-12345";

        loggingService.logRequest("POST", "/api/v1/admissions/applications", correlationId, tenantId.toString(), userId.toString(), "APPLICANT");
        loggingService.logResponse("POST", "/api/v1/admissions/applications", 201, 15L, correlationId);
        loggingService.logResponse("GET", "/api/v1/admissions/applications/invalid", 404, 5L, correlationId);
        loggingService.logResponse("POST", "/api/v1/admissions/applications/crash", 500, 25L, correlationId);

        loggingService.logLifecycleTransition(tenantId, appId, "DRAFT", "SUBMITTED", "SUBMIT", userId);

        UserSecurityContext context = new UserSecurityContext(userId, tenantId, null, null, "APPLICANT", false);
        loggingService.logSecurityViolation(context, "APPROVE_DECISION", "Applicant cannot approve decision", correlationId);

        Map<String, Object> meta = new HashMap<>();
        meta.put("score", 95);
        loggingService.logAudit(context, "SUBMIT_REVIEW", appId.toString(), meta);

        loggingService.logHandover(tenantId, appId, UUID.randomUUID(), "STUDENT_CREATED", 120L);
        loggingService.logError("Downstream call", new RuntimeException("Simulated connection timeout"), correlationId);
    }
}
