# CampXSync Enterprise Logger Utility (Version 2.0)

> **Architectural Component**: Platform Common Core  
> **Target System**: CampXSync College ERP (Distributed Microservices)  
> **Language**: Java (JDK 8+ compatible, zero mandatory external runtime dependencies)

The `campx-logger` utility is an enterprise-grade, asynchronous logging and execution-flow analysis framework engineered specifically for the CampXSync College ERP ecosystem. It serves as both an in-process Java library for backend microservices (`student-service`, `examination-service`, `attendance-service`, `faculty-service`, etc.) and a centralized HTTP ingestion & runtime management server.

---

## Architecture Highlights

1. **Non-Blocking Asynchronous Core**:
   Logging calls dispatch to a dedicated ring buffer (`AsyncLogProcessor`) backed by a background daemon worker thread. ERP database transactions and student requests are never blocked by file or console I/O.

2. **Dual-Channel File Generation**:
   - **`logs/campx-app.log`**: Human-readable, colorized pattern formatting with timestamp, thread, logger name, and correlation IDs (`traceId`, `tenantId`, `userId`).
   - **`logs/campx-flow.jsonl`**: High-performance structured JSON Lines designed for code flow reconstruction, distributed tracing, and log shippers (Grafana Loki, ELK, Splunk).

3. **Code Flow & Latency Tracing**:
   `FlowTracker` (implements `AutoCloseable`) traces end-to-end execution paths, records milestone steps, computes millisecond latency, and detects performance bottlenecks.

4. **Security & PII Masking**:
   Sanitizes sensitive information (passwords, auth tokens, student national IDs, phone numbers, and payment cards) before disk persistence.

5. **Dual API Exposure**:
   - **Java Client API**: Fluent, type-safe API for Java microservices.
   - **Embedded HTTP REST API**: Built-in HTTP server (`port 9898`) providing live status checks, runtime log-level adjustments without service restarts, and log ingestion for external services (e.g. Python AI service, Node frontend gateway).

6. **Built-in Flow Analyzer**:
   `LogAnalyzer` parses JSON flow logs, reconstructs multi-step execution flows, and flags operations exceeding latency thresholds.

---

## Directory Structure

```
logger/
├── pom.xml                                       # Maven build (Java 8+ compatible)
├── README.md                                     # Architecture & usage manual
├── src/
│   ├── main/
│   │   ├── java/com/campx/logger/
│   │   │   ├── CampXLogger.java                  # Main interface
│   │   │   ├── CampXLoggerFactory.java           # Factory entrypoint
│   │   │   ├── api/
│   │   │   │   ├── ILogger.java                  # Core logger contract
│   │   │   │   ├── LogLevel.java                 # TRACE, DEBUG, INFO, WARN, ERROR, FATAL, AUDIT
│   │   │   │   ├── LogEvent.java                 # Immutable structured log record
│   │   │   │   ├── FlowTracker.java              # AutoCloseable flow/latency tracker
│   │   │   │   ├── FluentLogBuilder.java         # Fluent contextual builder
│   │   │   │   └── AuditEvent.java               # Compliance and audit event
│   │   │   ├── context/
│   │   │   │   ├── LogContext.java               # ThreadLocal correlation context (MDC)
│   │   │   │   └── SecurityMasker.java           # PII and credential sanitizer
│   │   │   ├── core/
│   │   │   │   ├── AsyncLogProcessor.java        # Background queue worker
│   │   │   │   ├── LogManager.java               # Singleton lifecycle coordinator
│   │   │   │   └── LoggerImpl.java               # Logger implementation
│   │   │   ├── appender/
│   │   │   │   ├── LogAppender.java              # Appender interface
│   │   │   │   ├── ConsoleAppender.java          # Console output
│   │   │   │   ├── RollingFileAppender.java      # Size and daily rolling appender
│   │   │   │   └── JsonFileAppender.java         # Structured JSON lines appender
│   │   │   ├── formatter/
│   │   │   │   ├── LogFormatter.java             # Formatter contract
│   │   │   │   ├── PatternFormatter.java         # Human-readable text pattern
│   │   │   │   └── JsonFormatter.java            # High-speed JSON serializer
│   │   │   ├── config/
│   │   │   │   ├── LoggerConfig.java             # Strongly-typed configuration
│   │   │   │   └── ConfigManager.java            # Configuration loader
│   │   │   ├── server/
│   │   │   │   └── LoggerApiServer.java          # Embedded HTTP REST management server
│   │   │   └── analysis/
│   │   │       ├── LogAnalysisSummary.java       # Metric summary model
│   │   │       └── LogAnalyzer.java              # Flow analysis and diagnostic tool
│   │   └── resources/
│   │       ├── campx-logger.properties           # Production configuration
│   │       └── campx-logger-dev.properties       # Development profile
│   └── test/
│       └── java/com/campx/logger/
│           ├── CampXLoggerTest.java              # Functional and context tests
│           ├── FlowTracingTest.java              # Flow execution & latency tests
│           ├── SecurityMaskingTest.java          # PII redaction tests
│           └── LoggerApiServerTest.java          # HTTP REST API tests
```

---

## How Other Services Use the Java API

### 1. Basic Logging
```java
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class StudentService {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentService.class);

    public void registerStudent(String studentId, String name) {
        logger.info("Registering new student: {} with ID: {}", name, studentId);
        try {
            // business logic
        } catch (Exception e) {
            logger.error("Failed to register student {}: {}", studentId, e.getMessage(), e);
        }
    }
}
```

### 2. Distributed Tracing & User Context (`LogContext`)
```java
import com.campx.logger.context.LogContext;

// In a Filter or Interceptor upon receiving an HTTP request:
LogContext.initTraceId();
LogContext.setTenantId("CAMPUS_NORTH");
LogContext.setUserId("STUDENT_9981");
LogContext.setUserRole("STUDENT");

try {
    // All logs emitted within this thread automatically carry traceId, tenantId, and userId
    logger.info("Accessing course syllabus");
} finally {
    LogContext.clear();
}
```

### 3. Execution Flow & Latency Tracking (`FlowTracker`)
```java
import com.campx.logger.api.FlowTracker;

public void processSemesterResults(String semesterId) {
    try (FlowTracker flow = logger.flow("processSemesterResults", "FLOW-SEM-" + semesterId)) {
        
        // Milestone 1
        validateExamPapers();
        flow.step("ValidateExamPapers");

        // Milestone 2
        calculateGradePoints();
        flow.step("CalculateGradePoints");

        // Milestone 3
        publishLedger();
        flow.step("PublishLedger");

    } // Automatically logs completion and total execution time in milliseconds
}
```

### 4. Regulatory Audit Logging (`AuditEvent`)
```java
import com.campx.logger.api.AuditEvent;

AuditEvent audit = AuditEvent.builder()
        .action("GRADE_OVERRIDE")
        .principalId("FACULTY_DEAN_401")
        .principalRole("DEAN")
        .resourceType("STUDENT_GRADE")
        .resourceId("STU_1002")
        .clientIp("10.0.1.45")
        .status("SUCCESS")
        .description("Dean approved re-evaluation grade revision from B to A-")
        .addMetadata("courseId", "MATH201")
        .build();

logger.audit(audit);
```

---

## Embedded HTTP REST API Reference

The logger includes a lightweight HTTP server (default port `9898`) for runtime administration and cross-service ingestion.

| Endpoint | Method | Description | Example Request / Response |
| :--- | :--- | :--- | :--- |
| `/api/v1/logger/status` | `GET` | Health status, queue metrics, active log file paths | `{"status":"UP","rootLevel":"INFO","queueSize":0}` |
| `/api/v1/logger/level` | `GET` | Current root log level | `{"rootLevel":"INFO"}` |
| `/api/v1/logger/level?level=DEBUG` | `POST` | Change log level dynamically at runtime | `{"status":"SUCCESS","level":"DEBUG"}` |
| `/api/v1/logger/rotate` | `POST` | Trigger immediate log file rotation | `{"status":"SUCCESS","message":"Log rotation executed"}` |
| `/api/v1/logs` | `POST` | Ingest log from external services (AI service, etc.) | Payload: `{"level":"INFO","logger":"ai-service","message":"..."}` |

---

## Code Flow Analysis Diagnostic Tool

To inspect generated logs, reconstruct code flows, and pinpoint latency bottlenecks, run:

```powershell
# Using the built-in CLI runner (analyzes logs/campx-flow.jsonl with 50ms latency threshold)
java -cp target/campx-logger-2.0.0-SNAPSHOT.jar com.campx.logger.analysis.LogAnalyzer logs/campx-flow.jsonl 50
```

Or programmatically in diagnostic tools / integration tests:
```java
LogAnalysisSummary summary = LogAnalyzer.analyze(new File("logs/campx-flow.jsonl"), 50);
System.out.println(summary.generateReport());
```
