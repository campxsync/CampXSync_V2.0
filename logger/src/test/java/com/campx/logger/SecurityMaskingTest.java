package com.campx.logger;

import com.campx.logger.context.SecurityMasker;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying sensitive credential, token, PAN, and PII masking via {@link SecurityMasker}.
 */
public class SecurityMaskingTest {

    /**
     * Verifies that plain text and JSON-encoded password values are properly masked with asterisks.
     */
    @Test
    public void testPasswordMasking() {
        String msg1 = "User login attempt with password=SecretPassword123 and username=admin";
        String masked1 = SecurityMasker.mask(msg1);
        assertFalse(masked1.contains("SecretPassword123"));
        assertTrue(masked1.contains("password=******"));

        String msg2 = "{\"username\": \"student1\", \"password\": \"mySuperP@ss\"}";
        String masked2 = SecurityMasker.mask(msg2);
        assertFalse(masked2.contains("mySuperP@ss"));
        assertTrue(masked2.contains("\"password\": \"******\""));
    }

    /**
     * Verifies that HTTP Bearer authentication tokens are redacted while preserving the Bearer scheme header.
     */
    @Test
    public void testBearerTokenMasking() {
        String msg = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("doNotLeakThisSignature"));
        assertTrue(masked.contains("Bearer ******"));
    }

    /**
     * Verifies that 16-digit credit and debit card PAN numbers are replaced with asterisks.
     */
    @Test
    public void testCardNumberMasking() {
        String msg = "Fee payment transaction using card 4111-2222-3333-4444 completed";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("4111-2222-3333-4444"));
        assertTrue(masked.contains("****-****-****-****"));
    }

    /**
     * Verifies that 12-digit national identification numbers (Aadhaar) mask the leading 8 digits.
     */
    @Test
    public void testNationalIdMasking() {
        String msg = "Student Aadhaar verification for 1234 5678 9012 submitted";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("1234 5678"));
        assertTrue(masked.contains("XXXX-XXXX-9012"));
    }

    @Test
    public void testLeastPrivilegeEnforcement() {
        // Unmasked password must be detected
        assertTrue(SecurityMasker.containsSensitiveData("{\"password\": \"PlainPassword123\"}"));

        // Already masked credentials must NOT be falsely flagged
        assertFalse(SecurityMasker.containsSensitiveData("{\"password\": \"******\"}"));
        assertFalse(SecurityMasker.containsSensitiveData("{\"apiKey\": \"******\"}"));
        assertFalse(SecurityMasker.containsSensitiveData("{\"secret\": \"[REDACTED]\"}"));
        assertFalse(SecurityMasker.containsSensitiveData("Authorization: Bearer ******"));

        // assertLeastPrivilege succeeds on sanitized payload
        SecurityMasker.assertLeastPrivilege("{\"password\": \"******\", \"username\": \"admin\"}");
        SecurityMasker.assertLeastPrivilege("{\"secret\": \"[REDACTED]\"}");
    }

    @Test(expected = SecurityException.class)
    public void testAssertLeastPrivilegeRejectsUnmaskedPassword() {
        SecurityMasker.assertLeastPrivilege("{\"password\": \"mySecretPassword\"}");
    }

    @Test(expected = SecurityException.class)
    public void testAssertLeastPrivilegeRejectsUnmaskedApiKey() {
        SecurityMasker.assertLeastPrivilege("{\"apiKey\": \"ak_live_9988776655443322\"}");
    }

    @Test
    public void testMaskingIdempotency() {
        String original = "User login attempt with password=SecretPassword123 and username=admin";
        String maskedOnce = SecurityMasker.mask(original);
        String maskedTwice = SecurityMasker.mask(maskedOnce);
        org.junit.Assert.assertEquals(maskedOnce, maskedTwice);
    }

    @Test
    public void testUuidAndEpochTimestampPreservation() {
        String uuid = "11111111-1111-1111-1111-111111111111";
        String epochMs = "1719876543210";
        String payload = "{\"tenantId\":\"" + uuid + "\",\"timestamp\":" + epochMs + "}";

        String masked = SecurityMasker.mask(payload);
        assertTrue(masked.contains(uuid));
        assertTrue(masked.contains(epochMs));
        assertFalse(SecurityMasker.containsSensitiveData(payload));
    }
}
