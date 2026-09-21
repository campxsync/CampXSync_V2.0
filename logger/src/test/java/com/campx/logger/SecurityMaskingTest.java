package com.campx.logger;

import com.campx.logger.context.SecurityMasker;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecurityMaskingTest {

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

    @Test
    public void testBearerTokenMasking() {
        String msg = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("doNotLeakThisSignature"));
        assertTrue(masked.contains("Bearer ******"));
    }

    @Test
    public void testCardNumberMasking() {
        String msg = "Fee payment transaction using card 4111-2222-3333-4444 completed";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("4111-2222-3333-4444"));
        assertTrue(masked.contains("****-****-****-****"));
    }

    @Test
    public void testNationalIdMasking() {
        String msg = "Student Aadhaar verification for 1234 5678 9012 submitted";
        String masked = SecurityMasker.mask(msg);
        assertFalse(masked.contains("1234 5678"));
        assertTrue(masked.contains("XXXX-XXXX-9012"));
    }
}
