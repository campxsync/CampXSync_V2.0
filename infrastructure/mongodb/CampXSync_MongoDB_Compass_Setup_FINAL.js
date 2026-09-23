// CampXSync Enterprise MongoDB Bootstrap
// One physical database: CampXSync
// Current scope: 883 domain collections + 3 system collections = 886 total
// ADM reconciliation: payment_transactions -> billing_gateway_transactions;
// ADM-02 academic_calendars -> college_calendar_configuration;
// ADM-02 detailed sections 35-50 re-associated to matching purposes.
// Generated from the supplied ACD, CAM, EXM, FIN, HRM, LMS and ADM/College Admin
// Enterprise MongoDB collection-design documents.
//
// The source documents use database-per-service ownership. This script
// consolidates those logical service boundaries into ONE database while
// preserving ownership and avoiding collection-name collisions through:
//     <SERVICE>__<source_collection>
// Example:
//     ACD01__outbox_events
//     HRM01__employee_outbox
//
// Creates:
//   - all inventoried domain collections, including ADM-01 Institute Admin and ADM-02 College Admin
//   - baseline JSON Schema validation
//   - tenant/lifecycle/timestamp indexes
//   - outbox/idempotency/DLQ/audit-history indexes
//   - SYS__modules
//   - SYS__collection_registry
//   - SYS__schema_migrations
//
// Does not seed business data or create MongoDB/Atlas users.
// Cross-service business invariants remain application/service responsibilities.
//
// Run with mongosh or a MongoDB Compass shell supporting mongosh syntax.

use("CampXSync");

const DB_NAME = "CampXSync";
const dbx = db.getSiblingDB(DB_NAME);

const commonProperties = {
  tenantId: { bsonType: "string", minLength: 1, description: "Tenant isolation key" },
  institutionId: { bsonType: "string", minLength: 1, description: "Institution scope" },
  campusId: { bsonType: "string", minLength: 1 },
  status: { bsonType: "string", minLength: 1 },
  version: { bsonType: ["int", "long"], minimum: 0 },
  schemaVersion: { bsonType: ["string", "int"] },
  createdAt: { bsonType: "date" },
  updatedAt: { bsonType: "date" },
  createdBy: { bsonType: "string" },
  updatedBy: { bsonType: "string" },
  correlationId: { bsonType: "string" },
  sourceSystem: { bsonType: "string" }
};

const commonRequired = ["tenantId", "createdAt", "updatedAt"];

function baselineValidator(extraProperties = {}, extraRequired = []) {
  return {
    $jsonSchema: {
      bsonType: "object",
      required: [...new Set([...commonRequired, ...extraRequired])],
      properties: { ...commonProperties, ...extraProperties },
      additionalProperties: true
    }
  };
}

function createOrUpdateCollection(name, validator) {
  const exists = dbx.getCollectionNames().includes(name);

  if (!exists) {
    dbx.createCollection(name, {
      validator,
      validationLevel: "moderate",
      validationAction: "error"
    });
    print("CREATED  " + name);
  } else {
    dbx.runCommand({
      collMod: name,
      validator,
      validationLevel: "moderate",
      validationAction: "error"
    });
    print("UPDATED  " + name);
  }
}

function addBaselineIndexes(name) {
  const c = dbx.getCollection(name);

  c.createIndex(
    { tenantId: 1, status: 1, updatedAt: -1 },
    { name: "ix_tenant_status_updated" }
  );

  c.createIndex(
    { tenantId: 1, updatedAt: -1 },
    { name: "ix_tenant_updated" }
  );

  // History/audit collections receive the semantically named
  // ix_audit_history_time index in addSpecialIndexes(). Do not create
  // the same key pattern twice under different names.
  const n = (name || "").toLowerCase();
  if (!n.includes("history") && !n.includes("audit")) {
    c.createIndex(
      { tenantId: 1, createdAt: -1 },
      { name: "ix_tenant_created" }
    );
  }
}

function addSpecialIndexes(name, sourceCollection) {
  const c = dbx.getCollection(name);
  const n = sourceCollection.toLowerCase();

  if (n.includes("outbox")) {
    c.createIndex(
      { tenantId: 1, status: 1, createdAt: 1 },
      { name: "ix_outbox_dispatch_queue" }
    );
    c.createIndex(
      { eventId: 1 },
      { name: "ix_outbox_event_id", sparse: true }
    );
  }

  if (n.includes("idempotency")) {
    c.createIndex(
      { tenantId: 1, idempotencyKey: 1 },
      { name: "ux_tenant_idempotency", unique: true, sparse: true }
    );
    c.createIndex(
      { expiresAt: 1 },
      { name: "ttl_idempotency", expireAfterSeconds: 0, sparse: true }
    );
  }

  if (
    n.includes("dead_letter") ||
    n.includes("deadletter") ||
    n.includes("dead_letters")
  ) {
    c.createIndex(
      { tenantId: 1, status: 1, createdAt: -1 },
      { name: "ix_dlq_replay_queue" }
    );
    c.createIndex(
      { expiresAt: 1 },
      { name: "ttl_dead_letter", expireAfterSeconds: 0, sparse: true }
    );
  }

  if (n.includes("audit") || n.includes("history")) {
    // Avoid IndexOptionsConflict when the same key pattern already exists
    // (for example from an earlier partial bootstrap run).
    const existing = c.getIndexes().some(idx =>
      JSON.stringify(idx.key) === JSON.stringify({ tenantId: 1, createdAt: -1 })
    );
    if (!existing) {
      c.createIndex(
        { tenantId: 1, createdAt: -1 },
        { name: "ix_audit_history_time" }
      );
    }
  }
}


const collectionSpecs = [
  {"module": "ACD", "service": "ACD01", "sourceCollection": "courses", "collection": "ACD01__courses"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "course_versions", "collection": "ACD01__course_versions"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "course_prerequisites", "collection": "ACD01__course_prerequisites"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "course_history", "collection": "ACD01__course_history"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "outbox_events", "collection": "ACD01__outbox_events"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "idempotency_records", "collection": "ACD01__idempotency_records"},
  {"module": "ACD", "service": "ACD01", "sourceCollection": "dead_letter_events", "collection": "ACD01__dead_letter_events"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curricula", "collection": "ACD02__curricula"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curriculum_versions", "collection": "ACD02__curriculum_versions"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curriculum_subjects", "collection": "ACD02__curriculum_subjects"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curriculum_outcomes", "collection": "ACD02__curriculum_outcomes"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curriculum_prerequisites", "collection": "ACD02__curriculum_prerequisites"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "curriculum_history", "collection": "ACD02__curriculum_history"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "outbox_events", "collection": "ACD02__outbox_events"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "idempotency_records", "collection": "ACD02__idempotency_records"},
  {"module": "ACD", "service": "ACD02", "sourceCollection": "dead_letter_events", "collection": "ACD02__dead_letter_events"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "subjects", "collection": "ACD03__subjects"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "subject_versions", "collection": "ACD03__subject_versions"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "subject_metadata", "collection": "ACD03__subject_metadata"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "subject_prerequisites", "collection": "ACD03__subject_prerequisites"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "outbox_events", "collection": "ACD03__outbox_events"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "idempotency_records", "collection": "ACD03__idempotency_records"},
  {"module": "ACD", "service": "ACD03", "sourceCollection": "dead_letter_events", "collection": "ACD03__dead_letter_events"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "batches", "collection": "ACD04__batches"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "batch_rosters", "collection": "ACD04__batch_rosters"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "batch_history", "collection": "ACD04__batch_history"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "batch_capacity_overrides", "collection": "ACD04__batch_capacity_overrides"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "outbox_events", "collection": "ACD04__outbox_events"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "idempotency_records", "collection": "ACD04__idempotency_records"},
  {"module": "ACD", "service": "ACD04", "sourceCollection": "dead_letter_events", "collection": "ACD04__dead_letter_events"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "timetables", "collection": "ACD05__timetables"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "timetable_entries", "collection": "ACD05__timetable_entries"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "timetable_versions", "collection": "ACD05__timetable_versions"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "conflict_results", "collection": "ACD05__conflict_results"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "outbox_events", "collection": "ACD05__outbox_events"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "idempotency_records", "collection": "ACD05__idempotency_records"},
  {"module": "ACD", "service": "ACD05", "sourceCollection": "dead_letter_events", "collection": "ACD05__dead_letter_events"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "attendance_sessions", "collection": "ACD06__attendance_sessions"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "attendance_records", "collection": "ACD06__attendance_records"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "attendance_corrections", "collection": "ACD06__attendance_corrections"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "attendance_summaries", "collection": "ACD06__attendance_summaries"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "outbox_events", "collection": "ACD06__outbox_events"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "idempotency_records", "collection": "ACD06__idempotency_records"},
  {"module": "ACD", "service": "ACD06", "sourceCollection": "dead_letter_events", "collection": "ACD06__dead_letter_events"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "academic_calendars", "collection": "ACD07__academic_calendars"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "calendar_terms", "collection": "ACD07__calendar_terms"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "calendar_events", "collection": "ACD07__calendar_events"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "calendar_history", "collection": "ACD07__calendar_history"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "outbox_events", "collection": "ACD07__outbox_events"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "idempotency_records", "collection": "ACD07__idempotency_records"},
  {"module": "ACD", "service": "ACD07", "sourceCollection": "dead_letter_events", "collection": "ACD07__dead_letter_events"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "resources", "collection": "ACD08__resources"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "resource_versions", "collection": "ACD08__resource_versions"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "resource_access", "collection": "ACD08__resource_access"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "resource_history", "collection": "ACD08__resource_history"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "outbox_events", "collection": "ACD08__outbox_events"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "idempotency_records", "collection": "ACD08__idempotency_records"},
  {"module": "ACD", "service": "ACD08", "sourceCollection": "dead_letter_events", "collection": "ACD08__dead_letter_events"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "assessment_structures", "collection": "ACD09__assessment_structures"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "assessment_components", "collection": "ACD09__assessment_components"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "outcome_mappings", "collection": "ACD09__outcome_mappings"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "assessment_history", "collection": "ACD09__assessment_history"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "outbox_events", "collection": "ACD09__outbox_events"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "idempotency_records", "collection": "ACD09__idempotency_records"},
  {"module": "ACD", "service": "ACD09", "sourceCollection": "dead_letter_events", "collection": "ACD09__dead_letter_events"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "analytics_facts", "collection": "ACD10__analytics_facts"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "attendance_metrics", "collection": "ACD10__attendance_metrics"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "academic_metrics", "collection": "ACD10__academic_metrics"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "timetable_metrics", "collection": "ACD10__timetable_metrics"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "audience_metrics", "collection": "ACD10__audience_metrics"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "outbox_events", "collection": "ACD10__outbox_events"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "idempotency_records", "collection": "ACD10__idempotency_records"},
  {"module": "ACD", "service": "ACD10", "sourceCollection": "dead_letter_events", "collection": "ACD10__dead_letter_events"},
  {"module": "CAM", "service": "CAM01", "sourceCollection": "announcement_groups", "collection": "CAM01__announcement_groups"},
  {"module": "CAM", "service": "CAM01", "sourceCollection": "group_memberships", "collection": "CAM01__group_memberships"},
  {"module": "CAM", "service": "CAM01", "sourceCollection": "group_rules", "collection": "CAM01__group_rules"},
  {"module": "CAM", "service": "CAM01", "sourceCollection": "group_history", "collection": "CAM01__group_history"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "messages", "collection": "CAM02__messages"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "message_templates", "collection": "CAM02__message_templates"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "message_campaigns", "collection": "CAM02__message_campaigns"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "message_recipients", "collection": "CAM02__message_recipients"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "message_schedules", "collection": "CAM02__message_schedules"},
  {"module": "CAM", "service": "CAM02", "sourceCollection": "message_history", "collection": "CAM02__message_history"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "providers", "collection": "CAM03__providers"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "provider_credentials", "collection": "CAM03__provider_credentials"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "provider_routes", "collection": "CAM03__provider_routes"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "delivery_requests", "collection": "CAM03__delivery_requests"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "delivery_attempts", "collection": "CAM03__delivery_attempts"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "delivery_callbacks", "collection": "CAM03__delivery_callbacks"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "normalized_delivery_events", "collection": "CAM03__normalized_delivery_events"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "provider_health", "collection": "CAM03__provider_health"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "gateway_history", "collection": "CAM03__gateway_history"},
  {"module": "CAM", "service": "CAM03", "sourceCollection": "idempotency_records", "collection": "CAM03__idempotency_records"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "devices", "collection": "CAM04__devices"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_tokens", "collection": "CAM04__push_tokens"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_subscriptions", "collection": "CAM04__push_subscriptions"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_messages", "collection": "CAM04__push_messages"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_delivery_status", "collection": "CAM04__push_delivery_status"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_delivery_attempts", "collection": "CAM04__push_delivery_attempts"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_provider_callbacks", "collection": "CAM04__push_provider_callbacks"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_topics", "collection": "CAM04__push_topics"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "topic_memberships", "collection": "CAM04__topic_memberships"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_history", "collection": "CAM04__push_history"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "push_outbox", "collection": "CAM04__push_outbox"},
  {"module": "CAM", "service": "CAM04", "sourceCollection": "idempotency_records", "collection": "CAM04__idempotency_records"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "notification_rules", "collection": "CAM05__notification_rules"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "event_subscriptions", "collection": "CAM05__event_subscriptions"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "event_notification_jobs", "collection": "CAM05__event_notification_jobs"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "event_notification_history", "collection": "CAM05__event_notification_history"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "event_inbox", "collection": "CAM05__event_inbox"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "rule_evaluations", "collection": "CAM05__rule_evaluations"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "audience_snapshots", "collection": "CAM05__audience_snapshots"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "notification_dispatches", "collection": "CAM05__notification_dispatches"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "dispatch_attempts", "collection": "CAM05__dispatch_attempts"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "idempotency_records", "collection": "CAM05__idempotency_records"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "event_outbox", "collection": "CAM05__event_outbox"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "Authentication", "collection": "CAM05__Authentication"},
  {"module": "CAM", "service": "CAM05", "sourceCollection": "dead_letter_events", "collection": "CAM05__dead_letter_events"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "surveys", "collection": "CAM06__surveys"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_questions", "collection": "CAM06__survey_questions"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_responses", "collection": "CAM06__survey_responses"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_answers", "collection": "CAM06__survey_answers"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_results", "collection": "CAM06__survey_results"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_audit", "collection": "CAM06__survey_audit"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_outbox", "collection": "CAM06__survey_outbox"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "response_idempotency", "collection": "CAM06__response_idempotency"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "audience_snapshots", "collection": "CAM06__audience_snapshots"},
  {"module": "CAM", "service": "CAM06", "sourceCollection": "survey_jobs", "collection": "CAM06__survey_jobs"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "emergency_alerts", "collection": "CAM07__emergency_alerts"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "emergency_recipients", "collection": "CAM07__emergency_recipients"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "emergency_dispatches", "collection": "CAM07__emergency_dispatches"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "escalation_rules", "collection": "CAM07__escalation_rules"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "emergency_history", "collection": "CAM07__emergency_history"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "dispatch_attempts", "collection": "CAM07__dispatch_attempts"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "alert_acknowledgements", "collection": "CAM07__alert_acknowledgements"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "alert_idempotency", "collection": "CAM07__alert_idempotency"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "alert_outbox", "collection": "CAM07__alert_outbox"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "alert_jobs", "collection": "CAM07__alert_jobs"},
  {"module": "CAM", "service": "CAM07", "sourceCollection": "dead_letter_events", "collection": "CAM07__dead_letter_events"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "communication_metrics", "collection": "CAM08__communication_metrics"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "delivery_metrics", "collection": "CAM08__delivery_metrics"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "engagement_metrics", "collection": "CAM08__engagement_metrics"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "channel_metrics", "collection": "CAM08__channel_metrics"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "audience_metrics", "collection": "CAM08__audience_metrics"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "analytics_events", "collection": "CAM08__analytics_events"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "metric_definitions", "collection": "CAM08__metric_definitions"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "metric_snapshots", "collection": "CAM08__metric_snapshots"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "analytics_idempotency", "collection": "CAM08__analytics_idempotency"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "analytics_outbox", "collection": "CAM08__analytics_outbox"},
  {"module": "CAM", "service": "CAM08", "sourceCollection": "analytics_dead_letters", "collection": "CAM08__analytics_dead_letters"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_registrations", "collection": "EXM01__exam_registrations"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_registration_subjects", "collection": "EXM01__exam_registration_subjects"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_eligibility_snapshots", "collection": "EXM01__exam_eligibility_snapshots"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_registration_corrections", "collection": "EXM01__exam_registration_corrections"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_registration_history", "collection": "EXM01__exam_registration_history"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "exam_registration_locks", "collection": "EXM01__exam_registration_locks"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "registration_workflows", "collection": "EXM01__registration_workflows"},
  {"module": "EXM", "service": "EXM01", "sourceCollection": "registration_sequences", "collection": "EXM01__registration_sequences"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "examinations", "collection": "EXM02__examinations"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "exam_subjects", "collection": "EXM02__exam_subjects"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "exam_sessions", "collection": "EXM02__exam_sessions"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "exam_centres", "collection": "EXM02__exam_centres"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "exam_rooms", "collection": "EXM02__exam_rooms"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "schedule_entries", "collection": "EXM02__schedule_entries"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "invigilator_assignments", "collection": "EXM02__invigilator_assignments"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "schedule_versions", "collection": "EXM02__schedule_versions"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "scheduling_constraints", "collection": "EXM02__scheduling_constraints"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "conflict_logs", "collection": "EXM02__conflict_logs"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "publication_records", "collection": "EXM02__publication_records"},
  {"module": "EXM", "service": "EXM02", "sourceCollection": "audit_logs", "collection": "EXM02__audit_logs"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_tickets", "collection": "EXM03__hall_tickets"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_subjects", "collection": "EXM03__hall_ticket_subjects"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_eligibility", "collection": "EXM03__hall_ticket_eligibility"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_noc", "collection": "EXM03__hall_ticket_noc"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_versions", "collection": "EXM03__hall_ticket_versions"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_generation_jobs", "collection": "EXM03__hall_ticket_generation_jobs"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_documents", "collection": "EXM03__hall_ticket_documents"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_verification", "collection": "EXM03__hall_ticket_verification"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_downloads", "collection": "EXM03__hall_ticket_downloads"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_prints", "collection": "EXM03__hall_ticket_prints"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_publications", "collection": "EXM03__hall_ticket_publications"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_templates", "collection": "EXM03__hall_ticket_templates"},
  {"module": "EXM", "service": "EXM03", "sourceCollection": "hall_ticket_audit", "collection": "EXM03__hall_ticket_audit"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_assignments", "collection": "EXM04__valuation_assignments"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_batches", "collection": "EXM04__valuation_batches"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_sessions", "collection": "EXM04__valuation_sessions"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "answer_scripts", "collection": "EXM04__answer_scripts"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "marks_entries", "collection": "EXM04__marks_entries"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "question_marks", "collection": "EXM04__question_marks"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "marks_components", "collection": "EXM04__marks_components"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_schemes", "collection": "EXM04__valuation_schemes"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "moderation_records", "collection": "EXM04__moderation_records"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "revaluation_requests", "collection": "EXM04__revaluation_requests"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "retotalling_requests", "collection": "EXM04__retotalling_requests"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "marks_corrections", "collection": "EXM04__marks_corrections"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_locks", "collection": "EXM04__valuation_locks"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_history", "collection": "EXM04__valuation_history"},
  {"module": "EXM", "service": "EXM04", "sourceCollection": "valuation_audit", "collection": "EXM04__valuation_audit"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_workflows", "collection": "EXM05__approval_workflows"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_instances", "collection": "EXM05__approval_instances"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_tasks", "collection": "EXM05__approval_tasks"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_decisions", "collection": "EXM05__approval_decisions"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_policies", "collection": "EXM05__approval_policies"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_exceptions", "collection": "EXM05__approval_exceptions"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_delegations", "collection": "EXM05__approval_delegations"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_escalations", "collection": "EXM05__approval_escalations"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "marks_approval_snapshots", "collection": "EXM05__marks_approval_snapshots"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "marks_finalizations", "collection": "EXM05__marks_finalizations"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "approval_audit", "collection": "EXM05__approval_audit"},
  {"module": "EXM", "service": "EXM05", "sourceCollection": "outbox_events", "collection": "EXM05__outbox_events"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_types", "collection": "EXM06__document_types"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_templates", "collection": "EXM06__document_templates"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_template_versions", "collection": "EXM06__document_template_versions"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_requests", "collection": "EXM06__document_requests"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_instances", "collection": "EXM06__document_instances"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_snapshots", "collection": "EXM06__document_snapshots"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_versions", "collection": "EXM06__document_versions"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_subjects", "collection": "EXM06__document_subjects"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_signatures", "collection": "EXM06__document_signatures"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_verifications", "collection": "EXM06__document_verifications"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_print_jobs", "collection": "EXM06__document_print_jobs"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_print_items", "collection": "EXM06__document_print_items"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_reissues", "collection": "EXM06__document_reissues"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_cancellations", "collection": "EXM06__document_cancellations"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_delivery", "collection": "EXM06__document_delivery"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "document_audit", "collection": "EXM06__document_audit"},
  {"module": "EXM", "service": "EXM06", "sourceCollection": "outbox_events", "collection": "EXM06__outbox_events"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "scripts", "collection": "EXM07__scripts"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_barcodes", "collection": "EXM07__script_barcodes"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_packets", "collection": "EXM07__script_packets"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_batches", "collection": "EXM07__script_batches"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_movements", "collection": "EXM07__script_movements"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_scans", "collection": "EXM07__script_scans"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_locations", "collection": "EXM07__script_locations"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "valuation_centres", "collection": "EXM07__valuation_centres"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "centre_receipts", "collection": "EXM07__centre_receipts"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "centre_dispatches", "collection": "EXM07__centre_dispatches"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_reconciliation", "collection": "EXM07__script_reconciliation"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_exceptions", "collection": "EXM07__script_exceptions"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_seals", "collection": "EXM07__script_seals"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_assignments", "collection": "EXM07__script_assignments"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_archives", "collection": "EXM07__script_archives"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "script_audit", "collection": "EXM07__script_audit"},
  {"module": "EXM", "service": "EXM07", "sourceCollection": "outbox_events", "collection": "EXM07__outbox_events"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_rules", "collection": "EXM08__result_rules"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "grade_rules", "collection": "EXM08__grade_rules"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "pass_rules", "collection": "EXM08__pass_rules"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "grace_rules", "collection": "EXM08__grace_rules"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "moderation_rules", "collection": "EXM08__moderation_rules"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_processing_jobs", "collection": "EXM08__result_processing_jobs"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_inputs", "collection": "EXM08__result_inputs"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_snapshots", "collection": "EXM08__result_snapshots"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "results", "collection": "EXM08__results"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_subjects", "collection": "EXM08__result_subjects"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_semesters", "collection": "EXM08__result_semesters"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_backlogs", "collection": "EXM08__result_backlogs"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_grace", "collection": "EXM08__result_grace"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_moderation", "collection": "EXM08__result_moderation"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_approvals", "collection": "EXM08__result_approvals"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_publications", "collection": "EXM08__result_publications"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_versions", "collection": "EXM08__result_versions"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_amendments", "collection": "EXM08__result_amendments"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "result_audit", "collection": "EXM08__result_audit"},
  {"module": "EXM", "service": "EXM08", "sourceCollection": "outbox_events", "collection": "EXM08__outbox_events"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "exam_reports", "collection": "EXM09__exam_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "student_reports", "collection": "EXM09__student_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "subject_reports", "collection": "EXM09__subject_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "programme_reports", "collection": "EXM09__programme_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "result_reports", "collection": "EXM09__result_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "attendance_reports", "collection": "EXM09__attendance_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "valuation_reports", "collection": "EXM09__valuation_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "script_reports", "collection": "EXM09__script_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "document_reports", "collection": "EXM09__document_reports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "dashboard_metrics", "collection": "EXM09__dashboard_metrics"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_templates", "collection": "EXM09__report_templates"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_schedules", "collection": "EXM09__report_schedules"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_executions", "collection": "EXM09__report_executions"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_exports", "collection": "EXM09__report_exports"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_filters", "collection": "EXM09__report_filters"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_access", "collection": "EXM09__report_access"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "report_audit", "collection": "EXM09__report_audit"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "event_checkpoints", "collection": "EXM09__event_checkpoints"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "projection_status", "collection": "EXM09__projection_status"},
  {"module": "EXM", "service": "EXM09", "sourceCollection": "outbox_events", "collection": "EXM09__outbox_events"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_structures", "collection": "FIN01__fee_structures"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_components", "collection": "FIN01__fee_components"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_installments", "collection": "FIN01__fee_installments"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_rules", "collection": "FIN01__fee_rules"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_concessions", "collection": "FIN01__fee_concessions"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "program_fee_mappings", "collection": "FIN01__program_fee_mappings"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_versions", "collection": "FIN01__fee_versions"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "academic_fee_periods", "collection": "FIN01__academic_fee_periods"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_structure_approvals", "collection": "FIN01__fee_structure_approvals"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "fee_structure_outbox", "collection": "FIN01__fee_structure_outbox"},
  {"module": "FIN", "service": "FIN01", "sourceCollection": "idempotency_records", "collection": "FIN01__idempotency_records"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "fee_obligations", "collection": "FIN02__fee_obligations"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "fee_obligation_items", "collection": "FIN02__fee_obligation_items"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_intents", "collection": "FIN02__payment_intents"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_transactions", "collection": "FIN02__payment_transactions"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_allocations", "collection": "FIN02__payment_allocations"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_methods", "collection": "FIN02__payment_methods"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_gateway_transactions", "collection": "FIN02__payment_gateway_transactions"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "refunds", "collection": "FIN02__refunds"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "reconciliation_records", "collection": "FIN02__reconciliation_records"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_settlements", "collection": "FIN02__payment_settlements"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "collection_batches", "collection": "FIN02__collection_batches"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "transaction_outbox", "collection": "FIN02__transaction_outbox"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "idempotency_records", "collection": "FIN02__idempotency_records"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "payment_webhook_events", "collection": "FIN02__payment_webhook_events"},
  {"module": "FIN", "service": "FIN02", "sourceCollection": "collection_adjustments", "collection": "FIN02__collection_adjustments"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_types", "collection": "FIN04__tax_types"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_codes", "collection": "FIN04__tax_codes"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_rates", "collection": "FIN04__tax_rates"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_components", "collection": "FIN04__tax_components"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_rules", "collection": "FIN04__tax_rules"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_jurisdictions", "collection": "FIN04__tax_jurisdictions"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_exemptions", "collection": "FIN04__tax_exemptions"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_transactions", "collection": "FIN04__tax_transactions"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_transaction_items", "collection": "FIN04__tax_transaction_items"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_calculation_snapshots", "collection": "FIN04__tax_calculation_snapshots"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_periods", "collection": "FIN04__tax_periods"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "monthly_tax_sheets", "collection": "FIN04__monthly_tax_sheets"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "yearly_tax_sheets", "collection": "FIN04__yearly_tax_sheets"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_adjustments", "collection": "FIN04__tax_adjustments"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_reconciliations", "collection": "FIN04__tax_reconciliations"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_payments", "collection": "FIN04__tax_payments"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_filing_records", "collection": "FIN04__tax_filing_records"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_documents", "collection": "FIN04__tax_documents"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_outbox", "collection": "FIN04__tax_outbox"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "idempotency_records", "collection": "FIN04__idempotency_records"},
  {"module": "FIN", "service": "FIN04", "sourceCollection": "tax_audit_snapshots", "collection": "FIN04__tax_audit_snapshots"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipts", "collection": "FIN05__receipts"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_items", "collection": "FIN05__receipt_items"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_number_sequences", "collection": "FIN05__receipt_number_sequences"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_templates", "collection": "FIN05__receipt_templates"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_template_versions", "collection": "FIN05__receipt_template_versions"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_documents", "collection": "FIN05__receipt_documents"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_verifications", "collection": "FIN05__receipt_verifications"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_delivery_records", "collection": "FIN05__receipt_delivery_records"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_reissue_requests", "collection": "FIN05__receipt_reissue_requests"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_cancellation_requests", "collection": "FIN05__receipt_cancellation_requests"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_adjustments", "collection": "FIN05__receipt_adjustments"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_generation_jobs", "collection": "FIN05__receipt_generation_jobs"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_outbox", "collection": "FIN05__receipt_outbox"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "idempotency_records", "collection": "FIN05__idempotency_records"},
  {"module": "FIN", "service": "FIN05", "sourceCollection": "receipt_audit_snapshots", "collection": "FIN05__receipt_audit_snapshots"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_programs", "collection": "FIN06__scholarship_programs"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_schemes", "collection": "FIN06__scholarship_schemes"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_versions", "collection": "FIN06__scholarship_versions"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_criteria", "collection": "FIN06__scholarship_criteria"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_benefits", "collection": "FIN06__scholarship_benefits"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_application_periods", "collection": "FIN06__scholarship_application_periods"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_applications", "collection": "FIN06__scholarship_applications"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_application_documents", "collection": "FIN06__scholarship_application_documents"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "eligibility_evaluations", "collection": "FIN06__eligibility_evaluations"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_evaluations", "collection": "FIN06__scholarship_evaluations"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_rankings", "collection": "FIN06__scholarship_rankings"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_approvals", "collection": "FIN06__scholarship_approvals"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_awards", "collection": "FIN06__scholarship_awards"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_award_components", "collection": "FIN06__scholarship_award_components"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_renewals", "collection": "FIN06__scholarship_renewals"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_suspensions", "collection": "FIN06__scholarship_suspensions"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_cancellations", "collection": "FIN06__scholarship_cancellations"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_adjustments", "collection": "FIN06__scholarship_adjustments"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_utilizations", "collection": "FIN06__scholarship_utilizations"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_disbursements", "collection": "FIN06__scholarship_disbursements"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_outbox", "collection": "FIN06__scholarship_outbox"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "idempotency_records", "collection": "FIN06__idempotency_records"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_audit_snapshots", "collection": "FIN06__scholarship_audit_snapshots"},
  {"module": "FIN", "service": "FIN06", "sourceCollection": "scholarship_comments", "collection": "FIN06__scholarship_comments"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_groups", "collection": "FIN07__payroll_groups"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "salary_structures", "collection": "FIN07__salary_structures"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "salary_structure_versions", "collection": "FIN07__salary_structure_versions"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "salary_components", "collection": "FIN07__salary_components"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "employee_payroll_profiles", "collection": "FIN07__employee_payroll_profiles"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_periods", "collection": "FIN07__payroll_periods"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_runs", "collection": "FIN07__payroll_runs"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_inputs", "collection": "FIN07__payroll_inputs"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_input_items", "collection": "FIN07__payroll_input_items"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_calculations", "collection": "FIN07__payroll_calculations"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_calculation_items", "collection": "FIN07__payroll_calculation_items"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_adjustments", "collection": "FIN07__payroll_adjustments"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_approvals", "collection": "FIN07__payroll_approvals"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_finalizations", "collection": "FIN07__payroll_finalizations"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_payment_requests", "collection": "FIN07__payroll_payment_requests"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_payment_records", "collection": "FIN07__payroll_payment_records"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payslips", "collection": "FIN07__payslips"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payslip_documents", "collection": "FIN07__payslip_documents"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_reconciliations", "collection": "FIN07__payroll_reconciliations"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_loans", "collection": "FIN07__payroll_loans"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_advances", "collection": "FIN07__payroll_advances"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_arrears", "collection": "FIN07__payroll_arrears"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_bonuses", "collection": "FIN07__payroll_bonuses"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_outbox", "collection": "FIN07__payroll_outbox"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "idempotency_records", "collection": "FIN07__idempotency_records"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_audit_snapshots", "collection": "FIN07__payroll_audit_snapshots"},
  {"module": "FIN", "service": "FIN07", "sourceCollection": "payroll_comments", "collection": "FIN07__payroll_comments"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_definitions", "collection": "FIN08__report_definitions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_versions", "collection": "FIN08__report_versions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_parameters", "collection": "FIN08__report_parameters"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_dimensions", "collection": "FIN08__report_dimensions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_facts", "collection": "FIN08__report_facts"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "finance_daily_snapshots", "collection": "FIN08__finance_daily_snapshots"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "finance_monthly_snapshots", "collection": "FIN08__finance_monthly_snapshots"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "finance_academic_year_snapshots", "collection": "FIN08__finance_academic_year_snapshots"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "collection_fact_daily", "collection": "FIN08__collection_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "expense_fact_daily", "collection": "FIN08__expense_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "payroll_fact_daily", "collection": "FIN08__payroll_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "scholarship_fact_daily", "collection": "FIN08__scholarship_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "tax_fact_daily", "collection": "FIN08__tax_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "budget_fact_daily", "collection": "FIN08__budget_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "ledger_fact_daily", "collection": "FIN08__ledger_fact_daily"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "dashboard_definitions", "collection": "FIN08__dashboard_definitions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "dashboard_widgets", "collection": "FIN08__dashboard_widgets"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "kpi_definitions", "collection": "FIN08__kpi_definitions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "kpi_values", "collection": "FIN08__kpi_values"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_jobs", "collection": "FIN08__report_jobs"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_exports", "collection": "FIN08__report_exports"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_schedules", "collection": "FIN08__report_schedules"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_subscriptions", "collection": "FIN08__report_subscriptions"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_deliveries", "collection": "FIN08__report_deliveries"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_cache", "collection": "FIN08__report_cache"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "data_refresh_status", "collection": "FIN08__data_refresh_status"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_outbox", "collection": "FIN08__report_outbox"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "idempotency_records", "collection": "FIN08__idempotency_records"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_audit_snapshots", "collection": "FIN08__report_audit_snapshots"},
  {"module": "FIN", "service": "FIN08", "sourceCollection": "report_comments", "collection": "FIN08__report_comments"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_events", "collection": "FIN09__audit_events"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_records", "collection": "FIN09__audit_records"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_event_batches", "collection": "FIN09__audit_event_batches"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_event_failures", "collection": "FIN09__audit_event_failures"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_change_history", "collection": "FIN09__audit_change_history"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "compliance_rules", "collection": "FIN09__compliance_rules"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "compliance_rule_versions", "collection": "FIN09__compliance_rule_versions"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "compliance_evaluations", "collection": "FIN09__compliance_evaluations"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "compliance_violations", "collection": "FIN09__compliance_violations"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "compliance_exceptions", "collection": "FIN09__compliance_exceptions"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_cases", "collection": "FIN09__audit_cases"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_case_items", "collection": "FIN09__audit_case_items"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_case_comments", "collection": "FIN09__audit_case_comments"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_case_actions", "collection": "FIN09__audit_case_actions"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_evidence", "collection": "FIN09__audit_evidence"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "evidence_documents", "collection": "FIN09__evidence_documents"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "evidence_references", "collection": "FIN09__evidence_references"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "evidence_hashes", "collection": "FIN09__evidence_hashes"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "evidence_access_logs", "collection": "FIN09__evidence_access_logs"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "approval_audit_records", "collection": "FIN09__approval_audit_records"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "segregation_duty_rules", "collection": "FIN09__segregation_duty_rules"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "segregation_duty_violations", "collection": "FIN09__segregation_duty_violations"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "data_access_audit", "collection": "FIN09__data_access_audit"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "sensitive_access_audit", "collection": "FIN09__sensitive_access_audit"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "administrative_audit", "collection": "FIN09__administrative_audit"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_report_definitions", "collection": "FIN09__audit_report_definitions"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_report_jobs", "collection": "FIN09__audit_report_jobs"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_report_exports", "collection": "FIN09__audit_report_exports"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_snapshots", "collection": "FIN09__audit_snapshots"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_retention_policies", "collection": "FIN09__audit_retention_policies"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_archives", "collection": "FIN09__audit_archives"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_integrity_records", "collection": "FIN09__audit_integrity_records"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_outbox", "collection": "FIN09__audit_outbox"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "idempotency_records", "collection": "FIN09__idempotency_records"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_comments", "collection": "FIN09__audit_comments"},
  {"module": "FIN", "service": "FIN09", "sourceCollection": "audit_configuration", "collection": "FIN09__audit_configuration"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budgets", "collection": "FIN10__budgets"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_versions", "collection": "FIN10__budget_versions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_periods", "collection": "FIN10__budget_periods"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_lines", "collection": "FIN10__budget_lines"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_categories", "collection": "FIN10__budget_categories"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_dimensions", "collection": "FIN10__budget_dimensions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_allocations", "collection": "FIN10__budget_allocations"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_allocation_versions", "collection": "FIN10__budget_allocation_versions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_transfers", "collection": "FIN10__budget_transfers"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_transfer_items", "collection": "FIN10__budget_transfer_items"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_controls", "collection": "FIN10__budget_controls"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_control_results", "collection": "FIN10__budget_control_results"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_commitments", "collection": "FIN10__budget_commitments"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_reservations", "collection": "FIN10__budget_reservations"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_releases", "collection": "FIN10__budget_releases"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_approvals", "collection": "FIN10__budget_approvals"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_approval_rules", "collection": "FIN10__budget_approval_rules"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_approval_history", "collection": "FIN10__budget_approval_history"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "forecasts", "collection": "FIN10__forecasts"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "forecast_versions", "collection": "FIN10__forecast_versions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "forecast_lines", "collection": "FIN10__forecast_lines"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "forecast_assumptions", "collection": "FIN10__forecast_assumptions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_scenarios", "collection": "FIN10__budget_scenarios"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "scenario_versions", "collection": "FIN10__scenario_versions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "scenario_assumptions", "collection": "FIN10__scenario_assumptions"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "scenario_results", "collection": "FIN10__scenario_results"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_actuals", "collection": "FIN10__budget_actuals"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_variances", "collection": "FIN10__budget_variances"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "variance_explanations", "collection": "FIN10__variance_explanations"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_documents", "collection": "FIN10__budget_documents"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_reports", "collection": "FIN10__budget_reports"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_report_jobs", "collection": "FIN10__budget_report_jobs"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_snapshots", "collection": "FIN10__budget_snapshots"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_outbox", "collection": "FIN10__budget_outbox"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "idempotency_records", "collection": "FIN10__idempotency_records"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_audit_snapshots", "collection": "FIN10__budget_audit_snapshots"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_comments", "collection": "FIN10__budget_comments"},
  {"module": "FIN", "service": "FIN10", "sourceCollection": "budget_configuration", "collection": "FIN10__budget_configuration"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expenses", "collection": "FIN03__expenses"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_items", "collection": "FIN03__expense_items"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_categories", "collection": "FIN03__expense_categories"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "vendors", "collection": "FIN03__vendors"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "vendor_bank_accounts", "collection": "FIN03__vendor_bank_accounts"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_invoices", "collection": "FIN03__expense_invoices"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_approvals", "collection": "FIN03__expense_approvals"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_documents", "collection": "FIN03__expense_documents"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_reimbursements", "collection": "FIN03__expense_reimbursements"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_adjustments", "collection": "FIN03__expense_adjustments"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_payment_requests", "collection": "FIN03__expense_payment_requests"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_outbox", "collection": "FIN03__expense_outbox"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "idempotency_records", "collection": "FIN03__idempotency_records"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_comments", "collection": "FIN03__expense_comments"},
  {"module": "FIN", "service": "FIN03", "sourceCollection": "expense_audit_snapshots", "collection": "FIN03__expense_audit_snapshots"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employees", "collection": "HRM01__employees"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_contacts", "collection": "HRM01__employee_contacts"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_emergency_contacts", "collection": "HRM01__employee_emergency_contacts"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_qualifications", "collection": "HRM01__employee_qualifications"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_experience", "collection": "HRM01__employee_experience"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_assignments", "collection": "HRM01__employee_assignments"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_documents", "collection": "HRM01__employee_documents"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_identifiers", "collection": "HRM01__employee_identifiers"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_history", "collection": "HRM01__employee_history"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "employee_outbox", "collection": "HRM01__employee_outbox"},
  {"module": "HRM", "service": "HRM01", "sourceCollection": "idempotency_records", "collection": "HRM01__idempotency_records"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_types", "collection": "HRM02__leave_types"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_policies", "collection": "HRM02__leave_policies"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_entitlement_rules", "collection": "HRM02__leave_entitlement_rules"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "employee_leave_entitlements", "collection": "HRM02__employee_leave_entitlements"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "employee_leave_balances", "collection": "HRM02__employee_leave_balances"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_applications", "collection": "HRM02__leave_applications"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_application_days", "collection": "HRM02__leave_application_days"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_approvals", "collection": "HRM02__leave_approvals"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_transactions", "collection": "HRM02__leave_transactions"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_cancellations", "collection": "HRM02__leave_cancellations"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "holiday_calendars", "collection": "HRM02__holiday_calendars"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_policy_versions", "collection": "HRM02__leave_policy_versions"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_history", "collection": "HRM02__leave_history"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "leave_outbox", "collection": "HRM02__leave_outbox"},
  {"module": "HRM", "service": "HRM02", "sourceCollection": "idempotency_records", "collection": "HRM02__idempotency_records"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_records", "collection": "HRM03__attendance_records"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_punches", "collection": "HRM03__attendance_punches"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_schedules", "collection": "HRM03__attendance_schedules"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_shift_definitions", "collection": "HRM03__attendance_shift_definitions"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "employee_shift_assignments", "collection": "HRM03__employee_shift_assignments"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_policies", "collection": "HRM03__attendance_policies"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_policy_versions", "collection": "HRM03__attendance_policy_versions"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_calculations", "collection": "HRM03__attendance_calculations"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_regularizations", "collection": "HRM03__attendance_regularizations"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_regularization_approvals", "collection": "HRM03__attendance_regularization_approvals"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_adjustments", "collection": "HRM03__attendance_adjustments"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_holidays", "collection": "HRM03__attendance_holidays"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_daily_summaries", "collection": "HRM03__attendance_daily_summaries"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_monthly_summaries", "collection": "HRM03__attendance_monthly_summaries"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_history", "collection": "HRM03__attendance_history"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_transactions", "collection": "HRM03__attendance_transactions"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "attendance_outbox", "collection": "HRM03__attendance_outbox"},
  {"module": "HRM", "service": "HRM03", "sourceCollection": "idempotency_records", "collection": "HRM03__idempotency_records"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_cycles", "collection": "HRM04__performance_cycles"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_cycle_versions", "collection": "HRM04__performance_cycle_versions"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_plans", "collection": "HRM04__performance_plans"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_goals", "collection": "HRM04__performance_goals"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_kras", "collection": "HRM04__performance_kras"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_kpis", "collection": "HRM04__performance_kpis"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_milestones", "collection": "HRM04__performance_milestones"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_competencies", "collection": "HRM04__performance_competencies"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "competency_assessments", "collection": "HRM04__competency_assessments"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_rating_scales", "collection": "HRM04__performance_rating_scales"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_rating_scale_versions", "collection": "HRM04__performance_rating_scale_versions"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_assessments", "collection": "HRM04__performance_assessments"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_reviews", "collection": "HRM04__performance_reviews"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_feedback", "collection": "HRM04__performance_feedback"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_calibrations", "collection": "HRM04__performance_calibrations"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_improvement_plans", "collection": "HRM04__performance_improvement_plans"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_development_plans", "collection": "HRM04__performance_development_plans"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_history", "collection": "HRM04__performance_history"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_transactions", "collection": "HRM04__performance_transactions"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "performance_outbox", "collection": "HRM04__performance_outbox"},
  {"module": "HRM", "service": "HRM04", "sourceCollection": "idempotency_records", "collection": "HRM04__idempotency_records"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "salary_structures", "collection": "HRM05__salary_structures"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "salary_components", "collection": "HRM05__salary_components"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "salary_component_versions", "collection": "HRM05__salary_component_versions"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "employee_compensations", "collection": "HRM05__employee_compensations"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "compensation_revisions", "collection": "HRM05__compensation_revisions"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_periods", "collection": "HRM05__payroll_periods"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_runs", "collection": "HRM05__payroll_runs"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_inputs", "collection": "HRM05__payroll_inputs"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_earnings", "collection": "HRM05__payroll_earnings"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_deductions", "collection": "HRM05__payroll_deductions"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_adjustments", "collection": "HRM05__payroll_adjustments"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_calculations", "collection": "HRM05__payroll_calculations"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_approvals", "collection": "HRM05__payroll_approvals"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payslips", "collection": "HRM05__payslips"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_transactions", "collection": "HRM05__payroll_transactions"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_history", "collection": "HRM05__payroll_history"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "statutory_rules", "collection": "HRM05__statutory_rules"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "statutory_rule_versions", "collection": "HRM05__statutory_rule_versions"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "payroll_outbox", "collection": "HRM05__payroll_outbox"},
  {"module": "HRM", "service": "HRM05", "sourceCollection": "idempotency_records", "collection": "HRM05__idempotency_records"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_requisitions", "collection": "HRM06__recruitment_requisitions"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_requisition_approvals", "collection": "HRM06__recruitment_requisition_approvals"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "job_descriptions", "collection": "HRM06__job_descriptions"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "job_postings", "collection": "HRM06__job_postings"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_channels", "collection": "HRM06__recruitment_channels"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidates", "collection": "HRM06__candidates"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidate_documents", "collection": "HRM06__candidate_documents"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidate_applications", "collection": "HRM06__candidate_applications"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidate_screenings", "collection": "HRM06__candidate_screenings"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "interview_rounds", "collection": "HRM06__interview_rounds"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "interview_panels", "collection": "HRM06__interview_panels"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "interview_evaluations", "collection": "HRM06__interview_evaluations"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidate_selections", "collection": "HRM06__candidate_selections"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "candidate_offers", "collection": "HRM06__candidate_offers"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "offer_approvals", "collection": "HRM06__offer_approvals"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "onboarding_records", "collection": "HRM06__onboarding_records"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "onboarding_tasks", "collection": "HRM06__onboarding_tasks"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "onboarding_documents", "collection": "HRM06__onboarding_documents"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "onboarding_history", "collection": "HRM06__onboarding_history"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_history", "collection": "HRM06__recruitment_history"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_transactions", "collection": "HRM06__recruitment_transactions"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "recruitment_outbox", "collection": "HRM06__recruitment_outbox"},
  {"module": "HRM", "service": "HRM06", "sourceCollection": "idempotency_records", "collection": "HRM06__idempotency_records"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "analytics_dimensions", "collection": "HRM08__analytics_dimensions"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "employee_analytics_facts", "collection": "HRM08__employee_analytics_facts"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "workforce_snapshots", "collection": "HRM08__workforce_snapshots"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "headcount_facts", "collection": "HRM08__headcount_facts"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "attendance_analytics", "collection": "HRM08__attendance_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "leave_analytics", "collection": "HRM08__leave_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "payroll_analytics", "collection": "HRM08__payroll_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "performance_analytics", "collection": "HRM08__performance_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "recruitment_analytics", "collection": "HRM08__recruitment_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "training_analytics", "collection": "HRM08__training_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "attrition_analytics", "collection": "HRM08__attrition_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "department_analytics", "collection": "HRM08__department_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "employee_lifecycle_analytics", "collection": "HRM08__employee_lifecycle_analytics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "kpi_definitions", "collection": "HRM08__kpi_definitions"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "kpi_results", "collection": "HRM08__kpi_results"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "dashboard_definitions", "collection": "HRM08__dashboard_definitions"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "report_definitions", "collection": "HRM08__report_definitions"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "scheduled_reports", "collection": "HRM08__scheduled_reports"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "analytics_jobs", "collection": "HRM08__analytics_jobs"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "data_quality_metrics", "collection": "HRM08__data_quality_metrics"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "analytics_event_log", "collection": "HRM08__analytics_event_log"},
  {"module": "HRM", "service": "HRM08", "sourceCollection": "analytics_outbox", "collection": "HRM08__analytics_outbox"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_programs", "collection": "HRM09__training_programs"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_courses", "collection": "HRM09__training_courses"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_sessions", "collection": "HRM09__training_sessions"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_trainers", "collection": "HRM09__training_trainers"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_nominations", "collection": "HRM09__training_nominations"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_enrollments", "collection": "HRM09__training_enrollments"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_attendance", "collection": "HRM09__training_attendance"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_assessments", "collection": "HRM09__training_assessments"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_assessment_attempts", "collection": "HRM09__training_assessment_attempts"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_completions", "collection": "HRM09__training_completions"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_certificates", "collection": "HRM09__training_certificates"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_learning_paths", "collection": "HRM09__training_learning_paths"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "employee_learning_paths", "collection": "HRM09__employee_learning_paths"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_resources", "collection": "HRM09__training_resources"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_feedback", "collection": "HRM09__training_feedback"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_skills", "collection": "HRM09__training_skills"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "employee_skill_progress", "collection": "HRM09__employee_skill_progress"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_schedules", "collection": "HRM09__training_schedules"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_approvals", "collection": "HRM09__training_approvals"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_history", "collection": "HRM09__training_history"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "training_outbox", "collection": "HRM09__training_outbox"},
  {"module": "HRM", "service": "HRM09", "sourceCollection": "idempotency_records", "collection": "HRM09__idempotency_records"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "courses", "collection": "LMS01__courses"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_offerings", "collection": "LMS01__course_offerings"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_sections", "collection": "LMS01__course_sections"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_prerequisites", "collection": "LMS01__course_prerequisites"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_faculty", "collection": "LMS01__course_faculty"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_schedules", "collection": "LMS01__course_schedules"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "enrollment_rules", "collection": "LMS01__enrollment_rules"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_versions", "collection": "LMS01__course_versions"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_audit", "collection": "LMS01__course_audit"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_classifications", "collection": "LMS01__course_classifications"},
  {"module": "LMS", "service": "LMS01", "sourceCollection": "course_program_mapping", "collection": "LMS01__course_program_mapping"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "contents", "collection": "LMS02__contents"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_modules", "collection": "LMS02__content_modules"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_topics", "collection": "LMS02__content_topics"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_versions", "collection": "LMS02__content_versions"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_metadata", "collection": "LMS02__content_metadata"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_access", "collection": "LMS02__content_access"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_processing", "collection": "LMS02__content_processing"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_audit", "collection": "LMS02__content_audit"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_dependencies", "collection": "LMS02__content_dependencies"},
  {"module": "LMS", "service": "LMS02", "sourceCollection": "content_publications", "collection": "LMS02__content_publications"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subjects", "collection": "LMS03__subjects"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_versions", "collection": "LMS03__subject_versions"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_program_mappings", "collection": "LMS03__subject_program_mappings"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_course_mappings", "collection": "LMS03__subject_course_mappings"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_semester_mappings", "collection": "LMS03__subject_semester_mappings"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_prerequisites", "collection": "LMS03__subject_prerequisites"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_equivalencies", "collection": "LMS03__subject_equivalencies"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_classifications", "collection": "LMS03__subject_classifications"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_audit", "collection": "LMS03__subject_audit"},
  {"module": "LMS", "service": "LMS03", "sourceCollection": "subject_change_requests", "collection": "LMS03__subject_change_requests"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_plans", "collection": "LMS04__lesson_plans"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_plan_versions", "collection": "LMS04__lesson_plan_versions"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_plan_modules", "collection": "LMS04__lesson_plan_modules"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_plan_topics", "collection": "LMS04__lesson_plan_topics"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_sessions", "collection": "LMS04__lesson_sessions"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_activities", "collection": "LMS04__lesson_activities"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_faculty_assignments", "collection": "LMS04__lesson_faculty_assignments"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_content_mappings", "collection": "LMS04__lesson_content_mappings"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_outcome_mappings", "collection": "LMS04__lesson_outcome_mappings"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_deliveries", "collection": "LMS04__lesson_deliveries"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_variances", "collection": "LMS04__lesson_variances"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_approvals", "collection": "LMS04__lesson_approvals"},
  {"module": "LMS", "service": "LMS04", "sourceCollection": "lesson_audit", "collection": "LMS04__lesson_audit"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignments", "collection": "LMS05__assignments"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_versions", "collection": "LMS05__assignment_versions"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_questions", "collection": "LMS05__assignment_questions"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_rubrics", "collection": "LMS05__assignment_rubrics"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_rubric_criteria", "collection": "LMS05__assignment_rubric_criteria"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_resources", "collection": "LMS05__assignment_resources"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_outcome_mappings", "collection": "LMS05__assignment_outcome_mappings"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_availability", "collection": "LMS05__assignment_availability"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_targets", "collection": "LMS05__assignment_targets"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "student_assignments", "collection": "LMS05__student_assignments"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "submissions", "collection": "LMS05__submissions"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "submission_versions", "collection": "LMS05__submission_versions"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "submission_files", "collection": "LMS05__submission_files"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "evaluations", "collection": "LMS05__evaluations"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "evaluation_items", "collection": "LMS05__evaluation_items"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "grades", "collection": "LMS05__grades"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "feedback", "collection": "LMS05__feedback"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "resubmissions", "collection": "LMS05__resubmissions"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_audit", "collection": "LMS05__assignment_audit"},
  {"module": "LMS", "service": "LMS05", "sourceCollection": "assignment_outbox", "collection": "LMS05__assignment_outbox"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "virtual_classrooms", "collection": "LMS06__virtual_classrooms"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "virtual_classroom_versions", "collection": "LMS06__virtual_classroom_versions"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "virtual_sessions", "collection": "LMS06__virtual_sessions"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_participants", "collection": "LMS06__session_participants"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "participant_sessions", "collection": "LMS06__participant_sessions"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "meeting_configurations", "collection": "LMS06__meeting_configurations"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "provider_meetings", "collection": "LMS06__provider_meetings"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "classroom_access", "collection": "LMS06__classroom_access"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_recordings", "collection": "LMS06__session_recordings"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_interactions", "collection": "LMS06__session_interactions"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_polls", "collection": "LMS06__session_polls"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "breakout_rooms", "collection": "LMS06__breakout_rooms"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_resources", "collection": "LMS06__session_resources"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_attendance_refs", "collection": "LMS06__session_attendance_refs"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "session_analytics", "collection": "LMS06__session_analytics"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "classroom_audit", "collection": "LMS06__classroom_audit"},
  {"module": "LMS", "service": "LMS06", "sourceCollection": "classroom_outbox", "collection": "LMS06__classroom_outbox"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resources", "collection": "LMS07__resources"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_versions", "collection": "LMS07__resource_versions"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_metadata", "collection": "LMS07__resource_metadata"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_categories", "collection": "LMS07__resource_categories"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_tags", "collection": "LMS07__resource_tags"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_associations", "collection": "LMS07__resource_associations"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_permissions", "collection": "LMS07__resource_permissions"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_access_policies", "collection": "LMS07__resource_access_policies"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_storage", "collection": "LMS07__resource_storage"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_processing", "collection": "LMS07__resource_processing"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_previews", "collection": "LMS07__resource_previews"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_links", "collection": "LMS07__resource_links"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_collections", "collection": "LMS07__resource_collections"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_usage", "collection": "LMS07__resource_usage"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_reviews", "collection": "LMS07__resource_reviews"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_publications", "collection": "LMS07__resource_publications"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_retention", "collection": "LMS07__resource_retention"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_audit", "collection": "LMS07__resource_audit"},
  {"module": "LMS", "service": "LMS07", "sourceCollection": "resource_outbox", "collection": "LMS07__resource_outbox"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_records", "collection": "LMS08__progress_records"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "activity_progress", "collection": "LMS08__activity_progress"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "course_progress", "collection": "LMS08__course_progress"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "subject_progress", "collection": "LMS08__subject_progress"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "module_progress", "collection": "LMS08__module_progress"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "lesson_progress", "collection": "LMS08__lesson_progress"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_rules", "collection": "LMS08__progress_rules"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_events", "collection": "LMS08__progress_events"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_snapshots", "collection": "LMS08__progress_snapshots"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_milestones", "collection": "LMS08__progress_milestones"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "learning_streaks", "collection": "LMS08__learning_streaks"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "completion_records", "collection": "LMS08__completion_records"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_calculations", "collection": "LMS08__progress_calculations"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_audit", "collection": "LMS08__progress_audit"},
  {"module": "LMS", "service": "LMS08", "sourceCollection": "progress_outbox", "collection": "LMS08__progress_outbox"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certification_programs", "collection": "LMS09__certification_programs"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certification_definitions", "collection": "LMS09__certification_definitions"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certification_requirements", "collection": "LMS09__certification_requirements"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certification_rules", "collection": "LMS09__certification_rules"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certification_applications", "collection": "LMS09__certification_applications"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "eligibility_evaluations", "collection": "LMS09__eligibility_evaluations"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "eligibility_evidence", "collection": "LMS09__eligibility_evidence"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certificates", "collection": "LMS09__certificates"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certificate_versions", "collection": "LMS09__certificate_versions"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "certificate_templates", "collection": "LMS09__certificate_templates"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credentials", "collection": "LMS09__credentials"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "badges", "collection": "LMS09__badges"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "badge_rules", "collection": "LMS09__badge_rules"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "badge_awards", "collection": "LMS09__badge_awards"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_verifications", "collection": "LMS09__credential_verifications"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_revocations", "collection": "LMS09__credential_revocations"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_expiry", "collection": "LMS09__credential_expiry"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_shares", "collection": "LMS09__credential_shares"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_audit", "collection": "LMS09__credential_audit"},
  {"module": "LMS", "service": "LMS09", "sourceCollection": "credential_outbox", "collection": "LMS09__credential_outbox"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "program_outcomes", "collection": "LMS10__program_outcomes"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "program_specific_outcomes", "collection": "LMS10__program_specific_outcomes"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "course_outcomes", "collection": "LMS10__course_outcomes"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "teaching_learning_outcomes", "collection": "LMS10__teaching_learning_outcomes"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_versions", "collection": "LMS10__outcome_versions"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_mappings", "collection": "LMS10__outcome_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "co_tlo_mappings", "collection": "LMS10__co_tlo_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "co_po_mappings", "collection": "LMS10__co_po_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "co_pso_mappings", "collection": "LMS10__co_pso_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "activity_outcome_mappings", "collection": "LMS10__activity_outcome_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "assessment_outcome_mappings", "collection": "LMS10__assessment_outcome_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "question_outcome_mappings", "collection": "LMS10__question_outcome_mappings"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "attainment_rules", "collection": "LMS10__attainment_rules"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "attainment_thresholds", "collection": "LMS10__attainment_thresholds"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "attainment_evidence", "collection": "LMS10__attainment_evidence"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "co_attainment", "collection": "LMS10__co_attainment"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "po_attainment", "collection": "LMS10__po_attainment"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "pso_attainment", "collection": "LMS10__pso_attainment"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "rubrics", "collection": "LMS10__rubrics"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "rubric_criteria", "collection": "LMS10__rubric_criteria"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_assessments", "collection": "LMS10__outcome_assessments"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_snapshots", "collection": "LMS10__outcome_snapshots"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "obe_reports", "collection": "LMS10__obe_reports"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_audit", "collection": "LMS10__outcome_audit"},
  {"module": "LMS", "service": "LMS10", "sourceCollection": "outcome_outbox", "collection": "LMS10__outcome_outbox"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "institutes", "collection": "ADM01__institutes"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "colleges", "collection": "ADM01__colleges"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "tenant_provisioning", "collection": "ADM01__tenant_provisioning"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "global_settings", "collection": "ADM01__global_settings"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "feature_flags", "collection": "ADM01__feature_flags"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "global_policies", "collection": "ADM01__global_policies"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "admin_users", "collection": "ADM01__admin_users"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "roles", "collection": "ADM01__roles"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "permissions", "collection": "ADM01__permissions"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "role_bindings", "collection": "ADM01__role_bindings"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "access_reviews", "collection": "ADM01__access_reviews"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "subscription_plans", "collection": "ADM01__subscription_plans"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "subscriptions", "collection": "ADM01__subscriptions"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "invoices", "collection": "ADM01__invoices"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "billing_gateway_transactions", "collection": "ADM01__billing_gateway_transactions", "legacySourceCollection": "payment_transactions"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "usage_metrics", "collection": "ADM01__usage_metrics"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "platform_health", "collection": "ADM01__platform_health"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "alerts", "collection": "ADM01__alerts"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "configuration_versions", "collection": "ADM01__configuration_versions"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "data_retention_policies", "collection": "ADM01__data_retention_policies"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "data_classifications", "collection": "ADM01__data_classifications"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "export_requests", "collection": "ADM01__export_requests"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "audit_logs", "collection": "ADM01__audit_logs"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "workflow_instances", "collection": "ADM01__workflow_instances"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "idempotency_records", "collection": "ADM01__idempotency_records"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "outbox_events", "collection": "ADM01__outbox_events"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "inbox_events", "collection": "ADM01__inbox_events"},
  {"module": "ADM", "service": "ADM01", "sourceCollection": "dead_letter_events", "collection": "ADM01__dead_letter_events"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "college_profiles", "collection": "ADM02__college_profiles"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "departments", "collection": "ADM02__departments"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "programs", "collection": "ADM02__programs"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "college_calendar_configuration", "collection": "ADM02__college_calendar_configuration", "legacySourceCollection": "academic_calendars"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "college_settings", "collection": "ADM02__college_settings"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "feature_overrides", "collection": "ADM02__feature_overrides"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "college_users", "collection": "ADM02__college_users", "legacySourceCollection": "configuration_history"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "college_roles", "collection": "ADM02__college_roles", "legacySourceCollection": "college_users"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "role_assignments", "collection": "ADM02__role_assignments", "legacySourceCollection": "college_roles"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "department_access", "collection": "ADM02__department_access", "legacySourceCollection": "role_assignments"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "user_import_jobs", "collection": "ADM02__user_import_jobs", "legacySourceCollection": "department_access"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "data_import_rows", "collection": "ADM02__data_import_rows", "legacySourceCollection": "user_import_jobs"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "data_quality_issues", "collection": "ADM02__data_quality_issues", "legacySourceCollection": "data_import_rows"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "document_metadata", "collection": "ADM02__document_metadata", "legacySourceCollection": "data_quality_issues"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "report_definitions", "collection": "ADM02__report_definitions", "legacySourceCollection": "document_metadata"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "report_runs", "collection": "ADM02__report_runs", "legacySourceCollection": "report_definitions"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "report_schedules", "collection": "ADM02__report_schedules", "legacySourceCollection": "report_runs"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "dashboard_snapshots", "collection": "ADM02__dashboard_snapshots", "legacySourceCollection": "report_schedules"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "approval_requests", "collection": "ADM02__approval_requests", "legacySourceCollection": "dashboard_snapshots"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "workflow_tasks", "collection": "ADM02__workflow_tasks", "legacySourceCollection": "approval_requests"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "data_export_requests", "collection": "ADM02__data_export_requests", "legacySourceCollection": "workflow_tasks"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "configuration_history", "collection": "ADM02__configuration_history", "legacySourceCollection": "data_export_requests"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "audit_logs", "collection": "ADM02__audit_logs"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "idempotency_records", "collection": "ADM02__idempotency_records"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "outbox_events", "collection": "ADM02__outbox_events"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "inbox_events", "collection": "ADM02__inbox_events"},
  {"module": "ADM", "service": "ADM02", "sourceCollection": "dead_letter_events", "collection": "ADM02__dead_letter_events"}
];

// -------------------------------------------------------------------------
// System / governance collections
// -------------------------------------------------------------------------

createOrUpdateCollection("SYS__modules", {
  $jsonSchema: {
    bsonType: "object",
    required: ["moduleCode", "moduleName", "status", "createdAt", "updatedAt"],
    properties: {
      moduleCode: { bsonType: "string", minLength: 2 },
      moduleName: { bsonType: "string", minLength: 1 },
      status: { bsonType: "string" },
      createdAt: { bsonType: "date" },
      updatedAt: { bsonType: "date" }
    },
    additionalProperties: true
  }
});

createOrUpdateCollection("SYS__collection_registry", {
  $jsonSchema: {
    bsonType: "object",
    required: [
      "module",
      "service",
      "sourceCollection",
      "physicalCollection",
      "createdAt",
      "updatedAt"
    ],
    properties: {
      module: { bsonType: "string" },
      service: { bsonType: "string" },
      sourceCollection: { bsonType: "string" },
      legacySourceCollection: { bsonType: ["string", "null"] },
      physicalCollection: { bsonType: "string" },
      sourceDatabase: { bsonType: "string" },
      ownership: { bsonType: "string" },
      createdAt: { bsonType: "date" },
      updatedAt: { bsonType: "date" }
    },
    additionalProperties: true
  }
});

createOrUpdateCollection("SYS__schema_migrations", {
  $jsonSchema: {
    bsonType: "object",
    required: ["migrationId", "version", "appliedAt"],
    properties: {
      migrationId: { bsonType: "string" },
      version: { bsonType: ["int", "long", "string"] },
      appliedAt: { bsonType: "date" },
      checksum: { bsonType: "string" },
      description: { bsonType: "string" }
    },
    additionalProperties: true
  }
});

dbx.getCollection("SYS__modules").createIndex(
  { moduleCode: 1 },
  { unique: true, name: "ux_module_code" }
);

dbx.getCollection("SYS__collection_registry").createIndex(
  { module: 1, service: 1, sourceCollection: 1 },
  { unique: true, name: "ux_module_service_source_collection" }
);

dbx.getCollection("SYS__collection_registry").createIndex(
  { physicalCollection: 1 },
  { unique: true, name: "ux_physical_collection" }
);

dbx.getCollection("SYS__schema_migrations").createIndex(
  { migrationId: 1 },
  { unique: true, name: "ux_migration_id" }
);

const now = new Date();

const moduleDocs = [
  { moduleCode: "ACD", moduleName: "Academic Management", status: "ACTIVE" },
  { moduleCode: "CAM", moduleName: "Communication & Announcements Management", status: "ACTIVE" },
  { moduleCode: "EXM", moduleName: "Examination Management", status: "ACTIVE" },
  { moduleCode: "FIN", moduleName: "Finance Management", status: "ACTIVE" },
  { moduleCode: "HRM", moduleName: "Human Resource Management", status: "ACTIVE" },
  { moduleCode: "LMS", moduleName: "Learning Management System", status: "ACTIVE" },
  { moduleCode: "ADM", moduleName: "Admin & College Admin", status: "ACTIVE" }
];

moduleDocs.forEach(x => {
  dbx.getCollection("SYS__modules").updateOne(
    { moduleCode: x.moduleCode },
    {
      $set: { ...x, updatedAt: now },
      $setOnInsert: { createdAt: now }
    },
    { upsert: true }
  );
});

// -------------------------------------------------------------------------
// Domain collections
// -------------------------------------------------------------------------

collectionSpecs.forEach(s => {
  createOrUpdateCollection(s.collection, baselineValidator());
  addBaselineIndexes(s.collection);
  addSpecialIndexes(s.collection, s.sourceCollection);

  dbx.getCollection("SYS__collection_registry").updateOne(
    {
      module: s.module,
      service: s.service,
      sourceCollection: s.sourceCollection
    },
    {
      $set: {
        module: s.module,
        service: s.service,
        sourceCollection: s.sourceCollection,
        legacySourceCollection: s.legacySourceCollection || null,
        physicalCollection: s.collection,
        sourceDatabase: "Consolidated into CampXSync",
        ownership: s.service,
        updatedAt: now
      },
      $setOnInsert: { createdAt: now }
    },
    { upsert: true }
  );
});

// -------------------------------------------------------------------------
// Migration marker
// -------------------------------------------------------------------------

dbx.getCollection("SYS__schema_migrations").updateOne(
  { migrationId: "CAMPXSYNC-MONGO-002" },
  {
    $set: {
      version: 2,
      appliedAt: now,
      description: "CampXSync consolidated enterprise bootstrap with reconciled ADM-01 and ADM-02 collection boundaries",
      collectionCount: collectionSpecs.length + 3
    }
  },
  { upsert: true }
);

// -------------------------------------------------------------------------
// Completion summary
// -------------------------------------------------------------------------

print("");
print("============================================================");
print(" CampXSync MongoDB bootstrap completed");
print(" Database      : " + DB_NAME);
print(" Domain cols   : " + collectionSpecs.length);
print(" System cols   : 3");
print(" Existing domain: 828");
print(" ADM domain     : 55 (reconciled)");
print(" Total domain   : " + collectionSpecs.length);
print(" Renamed/re-associated ADM collections: 18");
print(" System cols    : 3");
print(" Total cols     : " + (collectionSpecs.length + 3));
print("============================================================");
print("Physical naming: <SERVICE>__<corrected_logical_collection>");
print("============================================================");
