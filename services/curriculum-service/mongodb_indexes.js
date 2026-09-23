/**
 * CampXSync ACD-02 Curriculum Management Service
 * Production MongoDB Index Definitions & Sharding Directives
 * 
 * Target Database: campx_curriculum
 * Target Collections: curricula, curriculum_versions, outbox_events, idempotency_records, audit_history
 */

const db = db.getSiblingDB("campx_curriculum");

print("Creating indexes for collection: curricula...");

// 1. Multi-Tenant Unique Constraint on Course + Academic Pattern (BR-01, Story 01)
db.curricula.createIndex(
    { tenantId: 1, courseId: 1, academicPattern: 1 },
    { unique: true, name: "uniq_tenant_course_pattern", background: true }
);

// 2. Multi-Tenant Lifecycle Status Index (Story 10 - List Active Curricula)
db.curricula.createIndex(
    { tenantId: 1, status: 1 },
    { name: "idx_tenant_status", background: true }
);

// 3. Multi-Tenant Department Filtering (Story 02 - Department Scoping)
db.curricula.createIndex(
    { tenantId: 1, departmentId: 1 },
    { name: "idx_tenant_department", background: true }
);

// 4. Academic Year & Batch Querying (Story 08 - Search & Pagination)
db.curricula.createIndex(
    { tenantId: 1, academicYear: 1 },
    { name: "idx_tenant_academic_year", background: true }
);

// 5. Version Lookup & Sub-resource Traversals (Story 12 - Version Management)
db.curriculum_versions.createIndex(
    { curriculumId: 1, versionNo: 1 },
    { unique: true, name: "uniq_curriculum_version", background: true }
);

db.curriculum_versions.createIndex(
    { tenantId: 1, status: 1 },
    { name: "idx_version_tenant_status", background: true }
);

// 6. Outbox Polling Index for Asynchronous Relay Daemon (Story 51)
db.outbox_events.createIndex(
    { status: 1, occurredAt: 1 },
    { name: "idx_outbox_polling", background: true }
);

db.outbox_events.createIndex(
    { eventId: 1 },
    { unique: true, name: "uniq_outbox_event_id", background: true }
);

// 7. Idempotency Key TTL Index (Story 57 - Auto-expiry after 24 hours)
db.idempotency_records.createIndex(
    { tenantId: 1, idempotencyKey: 1 },
    { unique: true, name: "uniq_tenant_idempotency_key", background: true }
);

db.idempotency_records.createIndex(
    { createdAt: 1 },
    { expireAfterSeconds: 86400, name: "ttl_idempotency_24h", background: true }
);

// 8. Dead-Letter Queue Management Index (Story 54, 71)
db.dead_letter_events.createIndex(
    { status: 1, createdAt: 1 },
    { name: "idx_dlq_status_created", background: true }
);

// 9. Compliance Audit Trail Monotonic Sequence Index (Story 68)
db.audit_history.createIndex(
    { curriculumId: 1, sequenceNo: 1 },
    { unique: true, name: "uniq_history_curriculum_seq", background: true }
);

db.audit_history.createIndex(
    { tenantId: 1, occurredAt: 1 },
    { name: "idx_history_tenant_occurred", background: true }
);

print("All MongoDB indexes for ACD-02 Curriculum Management Service successfully configured.");
