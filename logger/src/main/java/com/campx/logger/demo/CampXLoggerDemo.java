package com.campx.logger.demo;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.analysis.LogAnalysisSummary;
import com.campx.logger.analysis.LogAnalyzer;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.api.LogLevel;
import com.campx.logger.context.LogContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * End-to-End Live Demonstration and Verification Runner for the CampXSync College ERP Logger Subsystem.
 * <p>
 * Exercises all primary capabilities in sequence:
 * <ol>
 *   <li>Dynamic log level adjustment via factory facade</li>
 *   <li>Multi-service execution flow tracing and step latency measurement</li>
 *   <li>Failure recording and error cascade tracking</li>
 *   <li>Automated sensitive data, PII, and credential masking</li>
 *   <li>Regulatory compliance audit event generation</li>
 *   <li>Embedded REST HTTP API status inquiries and remote log ingestion</li>
 *   <li>Automated offline JSONL log analysis and ASCII report generation via {@link LogAnalyzer}</li>
 * </ol>
 *
 * @see CampXLoggerFactory
 * @see FlowTracker
 * @see LogAnalyzer
 */
public class CampXLoggerDemo {

    private static final CampXLogger studentLogger = CampXLoggerFactory.getLogger("StudentAdmissionService");
    private static final CampXLogger feeLogger = CampXLoggerFactory.getLogger("FeeProcessingService");
    private static final CampXLogger examLogger = CampXLoggerFactory.getLogger("ExaminationService");

    /**
     * Entry point executing the comprehensive logging demonstration suite.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("     CAMPXSYNC COLLEGE ERP - LIVE LOGGER TEST & VERIFICATION     ");
        System.out.println("=================================================================\n");

        try {
            // 1. Set log level
            CampXLoggerFactory.setRootLevel(LogLevel.DEBUG);

            // 2. Simulate Student Admission & Fee Payment Workflow (Multi-service flow)
            simulateStudentAdmissionFlow();

            // 3. Simulate Examination Result Publication with Error Handling
            simulateExamResultFlowWithError();

            // 4. Test Security Masking (PII / Credentials in logs)
            simulateSecurityMasking();

            // 5. Test Audit Trail Event
            simulateAuditEvent();

            // 6. Test Embedded REST HTTP APIs
            testRestApiEndpoints();

            // Flush all asynchronous writes to disk
            System.out.println("\n[*] Flushing asynchronous queue to disk...");
            CampXLoggerFactory.flush();
            Thread.sleep(500);

            // 7. Run LogAnalyzer on generated JSON log file to reconstruct code flow
            System.out.println("\n[*] Running LogAnalyzer on generated logs/campx-flow.jsonl...");
            File jsonLog = new File("logs/campx-flow.jsonl");
            LogAnalysisSummary summary = LogAnalyzer.analyze(jsonLog, 15);
            System.out.println(summary.generateReport());

            System.out.println("\n[SUCCESS] All live logger verification tests completed successfully!");

        } catch (Exception e) {
            System.err.println("[FAIL] Verification encountered an error:");
            e.printStackTrace();
        } finally {
            CampXLoggerFactory.shutdown();
        }
    }

    /**
     * Simulates a multi-service student admission and tuition payment flow with step-by-step latency tracking.
     *
     * @throws Exception if thread sleep is interrupted
     */
    private static void simulateStudentAdmissionFlow() throws Exception {
        System.out.println("[TEST 1] Simulating Student Admission Flow with Latency Tracking...");
        String traceId = LogContext.initTraceId();
        LogContext.setTenantId("CAMPUS_BANGALORE");
        LogContext.setUserId("ADMIN_REGISTRAR_01");
        LogContext.setUserRole("REGISTRAR");

        try (FlowTracker admissionFlow = studentLogger.flow("StudentAdmissionWorkflow", "ADM-2026-9042")) {
            studentLogger.info("Initiating admission application for candidate: Aarav Sharma");
            Thread.sleep(25);
            admissionFlow.step("VerifyEligibilityAndDocuments");

            studentLogger.info("Documents verified. Allocating department seat: B.Tech Computer Science");
            Thread.sleep(30);
            admissionFlow.step("AllocateDepartmentSeat");

            // Handover to Fee Service
            feeLogger.info("Initiating semester fee payment of Rs. 85,000 for seat allocation");
            Thread.sleep(40);
            admissionFlow.step("ProcessSemesterFeePayment");

            studentLogger.info("Admission confirmed. Registration Number: CS2026-088 generated.");
        }
    }

    /**
     * Simulates an examination marks moderation flow that records a warning and explicit failure cause.
     *
     * @throws Exception if thread sleep is interrupted
     */
    private static void simulateExamResultFlowWithError() throws Exception {
        System.out.println("\n[TEST 2] Simulating Examination Flow with Error Cascade...");
        LogContext.initTraceId();
        LogContext.setTenantId("CAMPUS_BANGALORE");
        LogContext.setUserId("EXAM_CONTROLLER_02");

        try (FlowTracker examFlow = examLogger.flow("PublishSemesterResults", "EXAM-SEM4-2026")) {
            examLogger.info("Aggregating internal and external marks for Semester 4");
            Thread.sleep(20);
            examFlow.step("AggregateMarks");

            examLogger.warn("Discrepancy found: 3 students missing practical viva marks");
            Thread.sleep(15);
            examFlow.step("ValidateModerationScores");

            // Simulate failure
            examFlow.markFailed(new IllegalStateException("Moderation validation failed for Course CS402"));
        }
    }

    /**
     * Emits sample log events containing passwords, bearer tokens, credit cards, and national IDs to verify redaction.
     */
    private static void simulateSecurityMasking() {
        System.out.println("\n[TEST 3] Testing Security & PII Redaction in Logs...");
        studentLogger.info("Login attempt with username=aarav.s and password=SuperSecretPassword123!");
        studentLogger.info("Received Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token_payload_secret");
        feeLogger.info("Payment processed using card 4111-2222-3333-4444 exp=12/28");
        studentLogger.info("Candidate submitted Aadhaar card: 9876 5432 1098 for identity proof");
    }

    /**
     * Emits an immutable regulatory audit event for tuition scholarship fee concession.
     */
    private static void simulateAuditEvent() {
        System.out.println("\n[TEST 4] Emitting Regulatory Audit Trail Event...");
        AuditEvent audit = AuditEvent.builder()
                .action("FEE_CONCESSION_APPROVAL")
                .principalId("DEAN_ACADEMICS")
                .principalRole("DEAN")
                .resourceType("STUDENT_FEE_ACCOUNT")
                .resourceId("STU_CS2026_088")
                .clientIp("10.14.2.88")
                .status("SUCCESS")
                .description("Approved 25% merit scholarship fee concession for Academic Year 2026")
                .addMetadata("scholarshipType", "MERIT_EXCELLENCE")
                .build();
        studentLogger.audit(audit);
    }

    /**
     * Sends HTTP requests to the embedded REST API server to verify status checks, level changes, and ingestion.
     */
    private static void testRestApiEndpoints() {
        System.out.println("\n[TEST 5] Testing Embedded HTTP REST Server on port 9898...");
        try {
            // Test 1: GET /status
            String statusResp = httpGet("http://localhost:9898/api/v1/logger/status");
            System.out.println("  -> GET /api/v1/logger/status: " + statusResp);

            // Test 2: POST /level
            String levelResp = httpPost("http://localhost:9898/api/v1/logger/level?level=INFO", "");
            System.out.println("  -> POST /api/v1/logger/level: " + levelResp);

            // Test 3: POST /logs (External Service Ingestion)
            String ingestionPayload = "{\"level\":\"INFO\",\"logger\":\"PythonBiometricService\",\"message\":\"Biometric gate attendance registered for student CS2026-088\",\"flowId\":\"BIO-SYNC-101\",\"operation\":\"captureBiometric\"}";
            String ingestResp = httpPost("http://localhost:9898/api/v1/logs", ingestionPayload);
            System.out.println("  -> POST /api/v1/logs (Remote Ingestion): " + ingestResp);

        } catch (Exception e) {
            System.err.println("  [!] REST API test error: " + e.getMessage());
        }
    }

    /**
     * Executes a synchronous HTTP GET request.
     *
     * @param endpoint target URL
     * @return response body string
     * @throws Exception if request fails
     */
    private static String httpGet(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        return readResponse(conn);
    }

    /**
     * Executes a synchronous HTTP POST request with an optional JSON body.
     *
     * @param endpoint target URL
     * @param body     optional request payload
     * @return response body string
     * @throws Exception if request fails
     */
    private static String httpPost(String endpoint, String body) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return readResponse(conn);
    }

    /**
     * Reads the response payload from an {@link HttpURLConnection}.
     *
     * @param conn open HTTP connection
     * @return response string
     * @throws Exception if stream reading encounters an I/O error
     */
    private static String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
