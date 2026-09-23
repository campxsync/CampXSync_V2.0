package com.campx.logger;

import com.campx.logger.analysis.LogAnalysisSummary;
import com.campx.logger.analysis.LogAnalyzer;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.api.LogLevel;
import com.campx.logger.context.LogContext;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test suite verifying distributed execution flow tracking and JSON log reconstruction.
 * <p>
 * Tests step-by-step latency profiling, automatic milestone logging,
 * failure marking with root cause exceptions, and offline report reconstruction via {@link LogAnalyzer}.
 */
public class FlowTracingTest {

    private static CampXLogger logger;

    /**
     * Initializes test logger instance and configures DEBUG severity threshold.
     */
    @BeforeClass
    public static void setup() {
        CampXLoggerFactory.setRootLevel(LogLevel.DEBUG);
        logger = CampXLoggerFactory.getLogger(FlowTracingTest.class);
    }

    /**
     * Verifies end-to-end execution flow tracing, milestone recording, and offline analysis report generation.
     *
     * @throws Exception if file or analysis operations fail
     */
    @Test
    public void testFlowTracingAndAnalysis() throws Exception {
        String customFlowId = "FLOW-ENROLLMENT-" + System.currentTimeMillis();
        LogContext.initTraceId();
        LogContext.setTenantId("CAMPUS_MAIN");
        LogContext.setUserId("STUDENT_2026_001");

        try (FlowTracker flow = logger.flow("EnrollStudentProcess", customFlowId)) {
            // Step 1: Validate Student Documents
            Thread.sleep(15);
            flow.step("ValidateDocuments");

            // Step 2: Course Seat Allocation
            Thread.sleep(20);
            flow.step("AllocateCourseSeat");

            // Step 3: Fee Payment Processing
            Thread.sleep(25);
            flow.step("ProcessInitialFeeReceipt");
        } // Automatically logs COMPLETED FLOW with total execution time

        CampXLoggerFactory.flush();

        // Verify JSON flow log file exists and analyze it
        File jsonLogFile = new File("logs/campx-flow.jsonl");
        assertTrue("JSON flow log file should exist", jsonLogFile.exists());

        LogAnalysisSummary summary = LogAnalyzer.analyze(jsonLogFile, 10);
        assertNotNull(summary);
        assertTrue("Total records parsed should be > 0", summary.getTotalRecordsParsed() > 0);
        assertTrue("Summary should have captured the custom flow", summary.getFlowTimelines().containsKey(customFlowId));

        String report = summary.generateReport();
        assertNotNull(report);
        assertTrue("Report should mention ValidateDocuments", report.contains("ValidateDocuments"));
        assertTrue("Report should mention AllocateCourseSeat", report.contains("AllocateCourseSeat"));
    }

    /**
     * Verifies that flows marked as failed capture root cause exceptions and register in error counts.
     *
     * @throws Exception if log reading fails
     */
    @Test
    public void testFailedFlowTracing() throws Exception {
        String failedFlowId = "FLOW-EXAM-FAIL-" + System.currentTimeMillis();

        try (FlowTracker flow = logger.flow("CompileExamResults", failedFlowId)) {
            flow.step("FetchAnswerSheets");
            flow.markFailed(new IllegalStateException("Missing grading marks for student STU_404"));
        }

        CampXLoggerFactory.flush();

        File jsonLogFile = new File("logs/campx-flow.jsonl");
        LogAnalysisSummary summary = LogAnalyzer.analyze(jsonLogFile, 5);
        assertTrue("Summary should report errors", summary.getErrorCount() > 0);
    }
}
