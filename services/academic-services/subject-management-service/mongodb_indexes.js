/**
 * CampXSync ACD-03 Subject Management Service
 * Production MongoDB Index Definitions & Sharding Directives
 * 
 * Target Database: campx_subject (Standalone) or CampXSync (Consolidated)
 * Target Collections:
 *   - subjects (ACD03__subjects)
 *   - subject_versions (ACD03__subject_versions)
 *   - subject_metadata (ACD03__subject_metadata)
 *   - subject_prerequisites (ACD03__subject_prerequisites)
 *   - subject_equivalences (ACD03__subject_equivalences)
 *   - subject_history (ACD03__subject_history)
 *   - outbox_events (ACD03__outbox_events)
 *   - idempotency_records (ACD03__idempotency_records)
 *   - dead_letter_events (ACD03__dead_letter_events)
 */

const targetDb = db.getName() === "CampXSync" ? db : db.getSiblingDB("campx_subject");
const prefix = targetDb.getName() === "CampXSync" ? "ACD03__" : "";

function getCol(name) {
  return targetDb.getCollection(prefix + name);
}

print("Creating indexes for ACD-03 Subject Management Service on database: " + targetDb.getName() + " (prefix: '" + prefix + "')...");

// 1. subjects: Unique constraint on tenantId + institutionId + subjectCode (BR-01)
getCol("subjects").createIndex(
  { tenantId: 1, institutionId: 1, subjectCode: 1 },
  { unique: true, partialFilterExpression: { subjectCode: { $exists: true } }, name: "ux_tenant_inst_subject_code", background: true }
);

// 2. subjects: Department + status lookup (catalog & active scoping, BR-08)
getCol("subjects").createIndex(
  { tenantId: 1, departmentId: 1, status: 1 },
  { name: "ix_tenant_dept_subject_status", background: true }
);

// 3. subjects: Cross-listed departments array index
getCol("subjects").createIndex(
  { tenantId: 1, crossListedDepartmentIds: 1 },
  { sparse: true, name: "ix_tenant_cross_listed_depts", background: true }
);

// 4. subjects: Subject taxonomy type query
getCol("subjects").createIndex(
  { tenantId: 1, subjectType: 1 },
  { name: "ix_tenant_subject_type", background: true }
);

// 5. subjects: Canonical business subjectId
getCol("subjects").createIndex(
  { subjectId: 1 },
  { unique: true, sparse: true, name: "ux_subject_id", background: true }
);

// 6. subject_versions: Unique version sequence per subject (BR-06, BR-10)
getCol("subject_versions").createIndex(
  { subjectId: 1, versionNo: 1 },
  { unique: true, partialFilterExpression: { versionNo: { $exists: true } }, name: "ux_subject_version_no", background: true }
);

// 7. subject_versions: Version status lookup
getCol("subject_versions").createIndex(
  { subjectId: 1, status: 1 },
  { name: "ix_subject_version_status", background: true }
);

// 8. subject_versions: Multi-tenant version query
getCol("subject_versions").createIndex(
  { tenantId: 1, status: 1 },
  { name: "ix_subject_version_tenant_status", background: true }
);

// 9. subject_metadata: One-to-one link to subject
getCol("subject_metadata").createIndex(
  { subjectId: 1 },
  { unique: true, partialFilterExpression: { subjectId: { $exists: true } }, name: "ux_subject_metadata_id", background: true }
);

// 10. subject_metadata: Sensitivity classification filtering
getCol("subject_metadata").createIndex(
  { tenantId: 1, sensitivityLevel: 1 },
  { name: "ix_subject_meta_sensitivity", background: true }
);

// 11. subject_metadata: National regulatory identifiers (ABC / AICTE / NPTEL)
getCol("subject_metadata").createIndex(
  { "nationalIdentifiers.identifierValue": 1, "nationalIdentifiers.scheme": 1 },
  { sparse: true, name: "ix_subject_national_id", background: true }
);

// 12. subject_prerequisites: Forward dependency edge unique constraint (DAG, BR-06)
getCol("subject_prerequisites").createIndex(
  { subjectId: 1, prerequisiteSubjectId: 1 },
  { unique: true, partialFilterExpression: { prerequisiteSubjectId: { $exists: true } }, name: "ux_subject_prerequisite_edge", background: true }
);

// 13. subject_prerequisites: Reverse prerequisite dependency traversal
getCol("subject_prerequisites").createIndex(
  { prerequisiteSubjectId: 1 },
  { name: "ix_subject_reverse_prereq", background: true }
);

// 14. subject_prerequisites: Relationship type and status filter
getCol("subject_prerequisites").createIndex(
  { subjectId: 1, relationshipType: 1, status: 1 },
  { name: "ix_subject_prereq_type_status", background: true }
);

// 15. subject_equivalences: Unique equivalence mapping edge
getCol("subject_equivalences").createIndex(
  { sourceSubjectId: 1, targetSubjectId: 1, equivalenceType: 1 },
  { unique: true, partialFilterExpression: { sourceSubjectId: { $exists: true } }, name: "ux_subject_equivalence_edge", background: true }
);

// 16. subject_equivalences: Reverse lookup for incoming transfer evaluation
getCol("subject_equivalences").createIndex(
  { targetSubjectId: 1 },
  { name: "ix_subject_reverse_equivalence", background: true }
);

// 17. subject_equivalences: Multi-tenant equivalence type & status
getCol("subject_equivalences").createIndex(
  { tenantId: 1, equivalenceType: 1, status: 1 },
  { name: "ix_subject_equivalence_type", background: true }
);

// 18. subject_history: Timeline of subject audit mutations (BR-13)
getCol("subject_history").createIndex(
  { subjectId: 1, createdAt: -1 },
  { name: "ix_subject_history_timeline", background: true }
);

// 19. subject_history: Actor audit index
getCol("subject_history").createIndex(
  { tenantId: 1, actorId: 1 },
  { name: "ix_subject_history_actor", background: true }
);

// 20. outbox_events: Polling queue for asynchronous event relay (BR-14)
getCol("outbox_events").createIndex(
  { tenantId: 1, status: 1, createdAt: 1 },
  { name: "ix_outbox_dispatch_queue", background: true }
);

getCol("outbox_events").createIndex(
  { eventId: 1 },
  { unique: true, sparse: true, name: "ux_outbox_event_id", background: true }
);

// 21. idempotency_records: Multi-tenant unique key & TTL auto-expiry (24 hours)
getCol("idempotency_records").createIndex(
  { tenantId: 1, idempotencyKey: 1 },
  { unique: true, sparse: true, name: "ux_tenant_idempotency", background: true }
);

getCol("idempotency_records").createIndex(
  { expiresAt: 1 },
  { expireAfterSeconds: 0, sparse: true, name: "ttl_idempotency", background: true }
);

// 22. dead_letter_events: DLQ replay and error diagnostic queue
getCol("dead_letter_events").createIndex(
  { tenantId: 1, status: 1, createdAt: -1 },
  { name: "ix_dlq_replay_queue", background: true }
);

getCol("dead_letter_events").createIndex(
  { eventId: 1 },
  { unique: true, sparse: true, name: "ux_dead_letter_event_id", background: true }
);

print("ACD-03 indexes successfully declared.");
