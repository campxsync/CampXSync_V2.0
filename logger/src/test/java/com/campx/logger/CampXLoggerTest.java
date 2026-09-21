package com.campx.logger;

import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.context.LogContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CampXLoggerTest {

    private static CampXLogger logger;

    @BeforeClass
    public static void setup() {
        CampXLoggerFactory.setRootLevel(LogLevel.DEBUG);
        logger = CampXLoggerFactory.getLogger(CampXLoggerTest.class);
    }

    @AfterClass
    public static void teardown() {
        CampXLoggerFactory.flush();
    }

    @Test
    public void testBasicLoggingAndFileCreation() throws Exception {
        String testId = "TEST-EVENT-" + System.currentTimeMillis();
        logger.info("Initiating basic logger test with id: {}", testId);
        logger.debug("Debug level payload verification for id: {}", testId);
        logger.warn("Warning level verification for id: {}", testId);

        // Contextual logging
        try (AutoCloseable scope = LogContext.withContext(LogContext.KEY_TENANT_ID, "TENANT_CAMPX_01")) {
            LogContext.setUserId("ADMIN_USER_42");
            logger.info("Executing tenant operation with context id: {}", testId);
        }

        CampXLoggerFactory.flush();

        // Check if log file exists
        File logFile = new File("logs/campx-app.log");
        assertTrue("Log file should exist", logFile.exists());

        // Check content
        boolean foundTestId = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(testId)) {
                    foundTestId = true;
                    break;
                }
            }
        }
        assertTrue("Log file should contain emitted test event", foundTestId);
    }

    @Test
    public void testAuditEvent() throws Exception {
        String auditAction = "SEMESTER_GRADE_UPDATE_" + System.currentTimeMillis();
        AuditEvent audit = AuditEvent.builder()
                .action(auditAction)
                .principalId("FACULTY_PROF_JOHN")
                .principalRole("PROFESSOR")
                .resourceType("STUDENT_GRADE")
                .resourceId("STU_99482")
                .clientIp("192.168.1.105")
                .status("SUCCESS")
                .description("Updated CS101 final examination grade from B+ to A")
                .addMetadata("courseCode", "CS101")
                .build();

        logger.audit(audit);
        CampXLoggerFactory.flush();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        File logFile = new File("logs/campx-app.log");
        boolean foundAudit = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(auditAction)) {
                    foundAudit = true;
                    assertTrue("Audit log should contain principal ID", line.contains("FACULTY_PROF_JOHN"));
                    break;
                }
            }
        }
        assertTrue("Log file should contain emitted audit event", foundAudit);
    }

    @Test
    public void testFluentLogBuilder() {
        logger.atInfo()
                .tag("ATTENDANCE_SYNC")
                .withContext("department", "COMPUTER_SCIENCE")
                .withOperation("syncBiometricLogs")
                .log("Successfully synced attendance for {} students", 120);
        CampXLoggerFactory.flush();
    }
}
