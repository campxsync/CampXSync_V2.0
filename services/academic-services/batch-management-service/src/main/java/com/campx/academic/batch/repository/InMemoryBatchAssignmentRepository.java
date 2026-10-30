package com.campx.academic.batch.repository;

import com.campx.academic.batch.exception.BatchForbiddenException;
import com.campx.academic.batch.exception.BatchNotFoundException;
import com.campx.academic.batch.security.UserSecurityContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory thread-safe implementation of {@link BatchAssignmentRepository} for isolated unit testing.
 * Enforces tenant isolation, idempotency on duplicate assignments, and audit logging.
 */
public class InMemoryBatchAssignmentRepository implements BatchAssignmentRepository {

    public static class InMemoryAuditEntry {
        private final UUID tenantId;
        private final UUID collegeId;
        private final String action;
        private final UUID principalId;
        private final UUID resourceId;
        private final String status;
        private final String detail;

        public InMemoryAuditEntry(UUID tenantId, UUID collegeId, String action, UUID principalId, UUID resourceId, String status, String detail) {
            this.tenantId = tenantId;
            this.collegeId = collegeId;
            this.action = action;
            this.principalId = principalId;
            this.resourceId = resourceId;
            this.status = status;
            this.detail = detail;
        }

        public UUID getTenantId() { return tenantId; }
        public UUID getCollegeId() { return collegeId; }
        public String getAction() { return action; }
        public UUID getPrincipalId() { return principalId; }
        public UUID getResourceId() { return resourceId; }
        public String getStatus() { return status; }
        public String getDetail() { return detail; }
    }

    private static class BatchMeta {
        final UUID id;
        final UUID tenantId;
        final String name;
        final String code;
        final UUID collegeId;

        BatchMeta(UUID id, UUID tenantId, String name, String code, UUID collegeId) {
            this.id = id;
            this.tenantId = tenantId;
            this.name = name;
            this.code = code;
            this.collegeId = collegeId;
        }
    }

    private static class StudentMeta {
        final UUID id;
        final UUID tenantId;
        final String enrollmentNo;
        final String displayName;
        final UUID collegeId;

        StudentMeta(UUID id, UUID tenantId, String enrollmentNo, String displayName, UUID collegeId) {
            this.id = id;
            this.tenantId = tenantId;
            this.enrollmentNo = enrollmentNo;
            this.displayName = displayName;
            this.collegeId = collegeId;
        }
    }

    private final Map<String, BatchAssignmentEntity> assignments = new ConcurrentHashMap<>();
    private final Map<String, BatchMeta> batches = new ConcurrentHashMap<>();
    private final Map<String, StudentMeta> students = new ConcurrentHashMap<>();
    private final List<InMemoryAuditEntry> auditLogs = new CopyOnWriteArrayList<>();

    private volatile boolean simulateFailure = false;

    public InMemoryBatchAssignmentRepository() {
        seedDefaults();
    }

    private void seedDefaults() {
        UUID defaultTenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID collegeA = UUID.fromString("11111111-c011-0000-0000-000000000001");
        UUID batch1 = UUID.fromString("11111111-ba5c-0000-0000-000000000001");
        UUID batch2 = UUID.fromString("11111111-ba5c-0000-0000-000000000002");
        UUID student1 = UUID.fromString("11111111-5700-0000-0000-000000000001");
        UUID student2 = UUID.fromString("11111111-5700-0000-0000-000000000002");

        registerBatch(defaultTenant, batch1, "Computer Science Batch 2024-A", "CSE_2024_A", collegeA);
        registerBatch(defaultTenant, batch2, "Computer Science Batch 2024-B", "CSE_2024_B", collegeA);
        registerStudent(defaultTenant, student1, "2024-CSE-001", "Student One", collegeA);
        registerStudent(defaultTenant, student2, "2024-CSE-002", "Student Two", collegeA);

        // Foreign tenant seed for cross-tenant rejection testing
        UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID collegeB = UUID.fromString("22222222-c011-0000-0000-000000000001");
        UUID batchForeign = UUID.fromString("22222222-ba5c-0000-0000-000000000001");
        UUID studentForeign = UUID.fromString("22222222-5700-0000-0000-000000000001");
        registerBatch(tenantB, batchForeign, "Foreign Batch", "FOR_2024", collegeB);
        registerStudent(tenantB, studentForeign, "2024-FOR-001", "Foreign Student", collegeB);
    }

    public void registerBatch(UUID tenantId, UUID batchId, String name, String code, UUID collegeId) {
        batches.put(tenantId + ":" + batchId, new BatchMeta(batchId, tenantId, name, code, collegeId));
    }

    public void registerStudent(UUID tenantId, UUID studentId, String enrollmentNo, String displayName, UUID collegeId) {
        students.put(tenantId + ":" + studentId, new StudentMeta(studentId, tenantId, enrollmentNo, displayName, collegeId));
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public List<InMemoryAuditEntry> getAuditLogs() {
        return auditLogs;
    }

    @Override
    public BatchAssignmentEntity assignStudent(UserSecurityContext ctx, BatchAssignmentEntity assignment) {
        assertContext(ctx);
        if (assignment == null) throw new IllegalArgumentException("assignment cannot be null");
        if (assignment.getBatchId() == null) throw new IllegalArgumentException("batchId cannot be null");
        if (assignment.getStudentId() == null) throw new IllegalArgumentException("studentId cannot be null");

        if (simulateFailure) {
            throw new RuntimeException("Simulated database failure during batch assignment");
        }

        UUID tenantId = ctx.getTenantId();
        String batchKey = tenantId + ":" + assignment.getBatchId();
        BatchMeta batch = batches.get(batchKey);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found in tenant workspace: " + assignment.getBatchId());
        }

        String studentKey = tenantId + ":" + assignment.getStudentId();
        StudentMeta student = students.get(studentKey);
        if (student == null) {
            throw new BatchNotFoundException("Student not found in tenant workspace: " + assignment.getStudentId());
        }

        String assignmentKey = tenantId + ":" + assignment.getBatchId() + ":" + assignment.getStudentId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        BatchAssignmentEntity existing = assignments.get(assignmentKey);
        BatchAssignmentEntity result;

        if (existing != null) {
            // Upsert / Reaffirm assignment
            existing.setStatus("ACTIVE");
            if (assignment.getRollNo() != null && !assignment.getRollNo().trim().isEmpty()) {
                existing.setRollNo(assignment.getRollNo().trim());
            }
            existing.setRowVersion(existing.getRowVersion() + 1);
            existing.setUpdatedAt(now);
            result = existing;
        } else {
            // New assignment
            result = new BatchAssignmentEntity();
            result.setId(assignment.getId() != null ? assignment.getId() : UUID.randomUUID());
            result.setTenantId(tenantId);
            result.setBatchId(assignment.getBatchId());
            result.setStudentId(assignment.getStudentId());
            result.setRollNo(assignment.getRollNo() != null ? assignment.getRollNo().trim() : student.enrollmentNo);
            result.setJoinedOn(assignment.getJoinedOn() != null ? assignment.getJoinedOn() : LocalDate.now());
            result.setStatus("ACTIVE");
            result.setRowVersion(1);
            result.setCreatedAt(now);
            result.setUpdatedAt(now);
            assignments.put(assignmentKey, result);
        }

        // Enrich result fields
        result.setBatchName(batch.name);
        result.setBatchCode(batch.code);
        result.setStudentName(student.displayName);
        result.setEnrollmentNo(student.enrollmentNo);
        result.setCollegeId(student.collegeId != null ? student.collegeId : batch.collegeId);

        // Record Audit Event
        String detail = String.format("{\"enrollmentNo\":\"%s\",\"studentName\":\"%s\",\"batchId\":\"%s\",\"batchName\":\"%s\",\"batchCode\":\"%s\",\"rollNo\":\"%s\"}",
                student.enrollmentNo, student.displayName, batch.id, batch.name, batch.code, result.getRollNo());
        auditLogs.add(new InMemoryAuditEntry(tenantId, result.getCollegeId(), "STUDENT_BATCH_ASSIGNED",
                ctx.getUserId(), student.id, "SUCCESS", detail));

        return result;
    }

    @Override
    public Optional<BatchAssignmentEntity> findAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId) {
        assertContext(ctx);
        String key = ctx.getTenantId() + ":" + batchId + ":" + studentId;
        BatchAssignmentEntity entity = assignments.get(key);
        if (entity != null && "ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    public List<BatchAssignmentEntity> listAssignmentsByBatch(UserSecurityContext ctx, UUID batchId) {
        assertContext(ctx);
        List<BatchAssignmentEntity> list = new ArrayList<>();
        String prefix = ctx.getTenantId() + ":" + batchId + ":";
        for (Map.Entry<String, BatchAssignmentEntity> entry : assignments.entrySet()) {
            if (entry.getKey().startsWith(prefix) && "ACTIVE".equalsIgnoreCase(entry.getValue().getStatus())) {
                list.add(entry.getValue());
            }
        }
        return list;
    }

    @Override
    public BatchAssignmentEntity removeAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId, String reason) {
        assertContext(ctx);
        String key = ctx.getTenantId() + ":" + batchId + ":" + studentId;
        BatchAssignmentEntity existing = assignments.get(key);
        if (existing == null || !"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
            throw new BatchNotFoundException("Active assignment not found for student " + studentId + " in batch " + batchId);
        }

        existing.setStatus("ENDED");
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existing.setRowVersion(existing.getRowVersion() + 1);

        auditLogs.add(new InMemoryAuditEntry(ctx.getTenantId(), existing.getCollegeId(), "STUDENT_BATCH_REMOVED",
                ctx.getUserId(), studentId, "SUCCESS", reason != null ? reason : "Student withdrawn from batch"));

        return existing;
    }

    @Override
    public boolean verifyBatchExists(UserSecurityContext ctx, UUID batchId) {
        assertContext(ctx);
        return batches.containsKey(ctx.getTenantId() + ":" + batchId);
    }

    @Override
    public boolean verifyStudentExists(UserSecurityContext ctx, UUID studentId) {
        assertContext(ctx);
        return students.containsKey(ctx.getTenantId() + ":" + studentId);
    }

    private void assertContext(UserSecurityContext ctx) {
        if (ctx == null) {
            throw new BatchForbiddenException("Missing security context for batch assignment operation");
        }
        if (ctx.getTenantId() == null) {
            throw new BatchForbiddenException("Missing tenant context for batch assignment operation");
        }
    }
}
