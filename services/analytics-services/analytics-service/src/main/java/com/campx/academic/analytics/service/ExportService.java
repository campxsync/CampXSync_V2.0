package com.campx.academic.analytics.service;

import com.campx.academic.analytics.exception.AnalyticsExceptions.*;
import com.campx.academic.analytics.model.AnalyticsModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Asynchronous Report Export Service for ACD-10: Reporting & Analytics Service.
 * Generates CSV, XLSX, and PDF exports, applies field-level security policies,
 * manages object storage references and expiries, and records audit logs (ACD10-US-040 through ACD10-US-047).
 */
public class ExportService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ExportService.class);

    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(4);
    private final ConcurrentMap<String, ReportJob> jobs = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLogs = new CopyOnWriteArrayList<>();

    public ReportJob submitJob(String tenantId, ReportType reportType, ReportFormat format,
                              String requesterId, String requesterRole, Map<String, String> filters,
                              String correlationId, List<Map<String, Object>> projectionData) {
        String jobId = "RPT-ACD10-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ReportJob job = new ReportJob();
        job.setJobId(jobId);
        job.setTenantId(tenantId);
        job.setReportType(reportType);
        job.setFormat(format);
        job.setRequesterId(requesterId);
        job.setRequesterRole(requesterRole);
        job.setFilters(filters != null ? filters : Collections.emptyMap());
        job.setStatus(JobStatus.QUEUED);
        job.setPollUri("/api/v1/academics/analytics/export/" + jobId);
        job.setCreatedAt(Instant.now().toString());

        jobs.put(jobId, job);
        recordAudit(tenantId, "EXPORT_REQUESTED", requesterId, requesterRole, reportType.name(), "GRANTED",
                "Export requested: " + reportType + " format=" + format, correlationId);

        // Dispatch async processing
        exportExecutor.submit(() -> {
            try {
                job.setStatus(JobStatus.RUNNING);
                processExport(job, projectionData, correlationId);
            } catch (Exception e) {
                logger.error("Export job {} failed: {}", jobId, e.getMessage(), e);
                job.setStatus(JobStatus.FAILED);
                job.setDiagnosticMessage("Export generation failed: " + e.getMessage());
                recordAudit(tenantId, "EXPORT_FAILED", requesterId, requesterRole, reportType.name(), "FAILED",
                        "Failure diagnostic: " + e.getMessage(), correlationId);
            }
        });

        return job;
    }

    private void processExport(ReportJob job, List<Map<String, Object>> data, String correlationId) {
        // Enforce field-level policy based on role
        List<Map<String, Object>> sanitizedData = sanitizeData(data, job.getRequesterRole());

        String content;
        if (job.getFormat() == ReportFormat.CSV) {
            content = generateCsv(sanitizedData);
        } else if (job.getFormat() == ReportFormat.XLSX) {
            content = generateXlsxText(sanitizedData);
        } else if (job.getFormat() == ReportFormat.PDF) {
            content = generatePdfText(job, sanitizedData);
        } else {
            throw new AnalyticsValidationException("Unsupported format: " + job.getFormat());
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        job.setFileContent(content);
        job.setFileSize(bytes.length);
        job.setRowCount(sanitizedData.size());
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(Instant.now().toString());

        // 24 hour expiry on downloadable reference
        Instant expiry = Instant.now().plus(24, ChronoUnit.HOURS);
        job.setExpiresAt(expiry.toString());
        job.setOutputRef("/api/v1/academics/analytics/export/" + job.getJobId() + "/download?token=" + UUID.randomUUID());

        recordAudit(job.getTenantId(), "EXPORT_COMPLETED", job.getRequesterId(), job.getRequesterRole(),
                job.getReportType().name(), "COMPLETED",
                "Export generated: " + job.getRowCount() + " rows, " + job.getFileSize() + " bytes", correlationId);
    }

    private List<Map<String, Object>> sanitizeData(List<Map<String, Object>> data, String role) {
        List<Map<String, Object>> result = new ArrayList<>();
        boolean isPrivileged = "ACADEMIC_ADMIN".equalsIgnoreCase(role) ||
                "DEPARTMENT_HEAD".equalsIgnoreCase(role) ||
                "MANAGEMENT".equalsIgnoreCase(role) ||
                "OPERATOR".equalsIgnoreCase(role);

        for (Map<String, Object> row : data) {
            Map<String, Object> clean = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String k = entry.getKey();
                // Filter individual student identifiers or restricted classification for non-privileged
                if (!isPrivileged && ("studentId".equalsIgnoreCase(k) || "studentName".equalsIgnoreCase(k) ||
                        "internalRemarks".equalsIgnoreCase(k))) {
                    continue; // Withhold restricted data
                }
                clean.put(k, entry.getValue());
            }
            result.add(clean);
        }
        return result;
    }

    private String generateCsv(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "No data available\n";
        }
        StringBuilder sb = new StringBuilder();
        Set<String> headers = data.get(0).keySet();
        sb.append(String.join(",", headers)).append("\n");

        for (Map<String, Object> row : data) {
            List<String> values = new ArrayList<>();
            for (String h : headers) {
                Object val = row.get(h);
                values.add(val != null ? "\"" + val.toString().replace("\"", "\"\"") + "\"" : "");
            }
            sb.append(String.join(",", values)).append("\n");
        }
        return sb.toString();
    }

    private String generateXlsxText(List<Map<String, Object>> data) {
        // Tab-delimited spreadsheet XML compatible format
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        sb.append("<Worksheet ss:Name=\"AnalyticsExport\">\n<Table>\n");

        if (data != null && !data.isEmpty()) {
            sb.append("<Row>\n");
            for (String h : data.get(0).keySet()) {
                sb.append("<Cell><Data ss:Type=\"String\">").append(h).append("</Data></Cell>\n");
            }
            sb.append("</Row>\n");

            for (Map<String, Object> row : data) {
                sb.append("<Row>\n");
                for (Object val : row.values()) {
                    sb.append("<Cell><Data ss:Type=\"String\">").append(val != null ? val.toString() : "").append("</Data></Cell>\n");
                }
                sb.append("</Row>\n");
            }
        }
        sb.append("</Table>\n</Worksheet>\n</Workbook>\n");
        return sb.toString();
    }

    private String generatePdfText(ReportJob job, List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        sb.append("--- CampXSync ACD-10 Academic Analytics Report ---\n");
        sb.append("Report Type: ").append(job.getReportType()).append("\n");
        sb.append("Tenant: ").append(job.getTenantId()).append("\n");
        sb.append("Generated At: ").append(Instant.now()).append("\n");
        sb.append("Requester: ").append(job.getRequesterId()).append(" (").append(job.getRequesterRole()).append(")\n");
        sb.append("--------------------------------------------------\n");

        if (data != null) {
            for (Map<String, Object> row : data) {
                sb.append(row.toString()).append("\n");
            }
        }
        sb.append("%%EOF\n");
        return sb.toString();
    }

    public ReportJob getJob(String jobId, String tenantId, String requesterId, String requesterRole) {
        ReportJob job = jobs.get(jobId);
        if (job == null) {
            throw new AnalyticsNotFoundException("Export job not found: " + jobId);
        }
        if (!job.getTenantId().equals(tenantId)) {
            throw new AnalyticsForbiddenException("Access to export job from different tenant is forbidden");
        }
        // Requester verification (unless admin/operator)
        if (!"ACADEMIC_ADMIN".equalsIgnoreCase(requesterRole) &&
                !"OPERATOR".equalsIgnoreCase(requesterRole) &&
                !job.getRequesterId().equals(requesterId)) {
            throw new AnalyticsForbiddenException("Not authorized to view another user's export job");
        }
        return job;
    }

    public String downloadArtifact(String jobId, String tenantId, String requesterId, String requesterRole, String correlationId) {
        ReportJob job = getJob(jobId, tenantId, requesterId, requesterRole);
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new AnalyticsValidationException("Export job is not ready for download. Current status: " + job.getStatus());
        }

        // Check expiration
        if (job.getExpiresAt() != null) {
            Instant expiry = Instant.parse(job.getExpiresAt());
            if (Instant.now().isAfter(expiry)) {
                job.setStatus(JobStatus.EXPIRED);
                recordAudit(tenantId, "EXPORT_DOWNLOAD_EXPIRED", requesterId, requesterRole, job.getReportType().name(), "DENIED",
                        "Attempted download of expired artifact", correlationId);
                throw new AnalyticsValidationException("Export download link has expired");
            }
        }

        recordAudit(tenantId, "EXPORT_DOWNLOADED", requesterId, requesterRole, job.getReportType().name(), "GRANTED",
                "Export artifact downloaded: " + job.getFileSize() + " bytes", correlationId);

        return job.getFileContent();
    }

    public void recordAudit(String tenantId, String action, String actorId, String actorRole,
                            String scope, String decision, String details, String correlationId) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setAuditId("AUDIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entry.setTenantId(tenantId);
        entry.setAction(action);
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setScope(scope);
        entry.setDecision(decision);
        entry.setDetails(details);
        entry.setCorrelationId(correlationId);
        entry.setTimestamp(Instant.now().toString());

        auditLogs.add(entry);
        logger.info("AUDIT [{}] actor={} role={} action={} decision={} correlationId={}",
                tenantId, actorId, actorRole, action, decision, correlationId);
    }

    public List<AuditLogEntry> getAuditLogs(String tenantId) {
        List<AuditLogEntry> result = new ArrayList<>();
        for (AuditLogEntry entry : auditLogs) {
            if (entry.getTenantId().equals(tenantId)) {
                result.add(entry);
            }
        }
        return result;
    }

    public void shutdown() {
        exportExecutor.shutdown();
    }
}
