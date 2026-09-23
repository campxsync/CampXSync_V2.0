// =============================================================================
// CampXSync Enterprise MongoDB ADM Validator & Index Migration Script
// Target Physical Database: CampXSync
// Scope: ADM-01 (Institute Admin - 28 cols) + ADM-02 (College Admin - 27 cols) = 55 cols
// Generated from:
//   - CampXSync_ADM01_Institute_Admin_Enterprise_MongoDB_Architecture_v3.docx
//   - CampXSync_ADM02_College_Admin_Enterprise_MongoDB_Architecture_v3.docx
// Enforces:
//   - JSON Schema ($jsonSchema) validation with strict types, bounds, enums & regex
//   - Unique and compound business indexes for tenant isolation & performance
// Idempotent: Can be run multiple times safely (uses collMod for existing cols)
// =============================================================================

use("CampXSync");

const DB_NAME = "CampXSync";
const dbx = db.getSiblingDB(DB_NAME);

// Common platform audit/isolation properties present on all documents
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

function createValidator(extraProperties = {}, extraRequired = []) {
  return {
    $jsonSchema: {
      bsonType: "object",
      required: [...new Set([...commonRequired, ...extraRequired])],
      properties: { ...commonProperties, ...extraProperties },
      additionalProperties: true
    }
  };
}

// -----------------------------------------------------------------------------
// ADM-01 (Institute Admin) & ADM-02 (College Admin) Specific $jsonSchema Rules
// -----------------------------------------------------------------------------
const admValidators = {
  // --- ADM-01: Master / Tenant Governance ---
  "ADM01__institutes": createValidator({
    instituteId: { bsonType: "string", minLength: 1, description: "Business ID" },
    code: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,30}$", description: "Institute code" },
    name: { bsonType: "string", minLength: 2, maxLength: 255 },
    legalName: { bsonType: "string", minLength: 2, maxLength: 255 },
    status: { enum: ["ACTIVE", "SUSPENDED", "INACTIVE", "ARCHIVED"] },
    currency: { bsonType: "string", pattern: "^[A-Z]{3}$" },
    timezone: { bsonType: "string" },
    locale: { bsonType: "string" },
    profile: { bsonType: "object" },
    defaultPolicySetId: { bsonType: "string" },
    subscriptionPlanId: { bsonType: "string" }
  }, ["instituteId", "code", "name", "status"]),

  "ADM01__colleges": createValidator({
    collegeId: { bsonType: "string", minLength: 1 },
    instituteId: { bsonType: "string", minLength: 1 },
    code: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,30}$" },
    name: { bsonType: "string", minLength: 2, maxLength: 255 },
    status: { enum: ["ACTIVE", "SUSPENDED", "INACTIVE", "ARCHIVED"] },
    provisioningStatus: { enum: ["REQUESTED", "VALIDATED", "PROVISIONING", "ACTIVE", "SUSPENDED", "FAILED"] },
    campusIds: { bsonType: "array", items: { bsonType: "string" } }
  }, ["collegeId", "instituteId", "code", "name", "status", "provisioningStatus"]),

  "ADM01__tenant_provisioning": createValidator({
    provisioningId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    workflowId: { bsonType: "string" },
    currentStep: { bsonType: "string", minLength: 1 },
    attempt: { bsonType: ["int", "long"], minimum: 1 },
    provisioningStatus: { enum: ["REQUESTED", "VALIDATED", "PROVISIONING", "COMPLETED", "FAILED"] },
    idempotencyKey: { bsonType: "string" },
    requestedBy: { bsonType: "string" }
  }, ["provisioningId", "collegeId", "currentStep", "provisioningStatus"]),

  "ADM01__global_settings": createValidator({
    settingKey: { bsonType: "string", minLength: 1 },
    settingValue: { bsonType: "string" },
    dataType: { enum: ["STRING", "INTEGER", "BOOLEAN", "JSON", "SECRET"] },
    scope: { enum: ["GLOBAL", "INSTITUTE", "COLLEGE"] },
    isSecret: { bsonType: "bool" },
    effectiveFrom: { bsonType: "date" },
    effectiveTo: { bsonType: ["date", "null"] }
  }, ["settingKey", "dataType", "scope", "isSecret"]),

  "ADM01__feature_flags": createValidator({
    flagKey: { bsonType: "string", pattern: "^[a-z0-9._-]{2,50}$" },
    name: { bsonType: "string", minLength: 1 },
    description: { bsonType: "string" },
    enabled: { bsonType: "bool" },
    rolloutPercentage: { bsonType: ["int", "long"], minimum: 0, maximum: 100 },
    rules: { bsonType: "array" }
  }, ["flagKey", "enabled"]),

  "ADM01__global_policies": createValidator({
    policyCode: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,50}$" },
    name: { bsonType: "string", minLength: 1 },
    category: { bsonType: "string" },
    rules: { bsonType: "object" },
    enforcementMode: { enum: ["ENFORCE", "AUDIT", "DISABLED"] }
  }, ["policyCode", "enforcementMode"]),

  "ADM01__admin_users": createValidator({
    username: { bsonType: "string", minLength: 3, maxLength: 50 },
    email: { bsonType: "string", pattern: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$" },
    fullName: { bsonType: "string", minLength: 1 },
    passwordHash: { bsonType: "string" },
    roles: { bsonType: "array", items: { bsonType: "string" } },
    status: { enum: ["ACTIVE", "SUSPENDED", "LOCKED", "INACTIVE"] }
  }, ["username", "email", "status"]),

  "ADM01__roles": createValidator({
    roleCode: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,50}$" },
    name: { bsonType: "string", minLength: 1 },
    permissions: { bsonType: "array", items: { bsonType: "string" } },
    isSystemRole: { bsonType: "bool" }
  }, ["roleCode", "name"]),

  "ADM01__permissions": createValidator({
    permissionCode: { bsonType: "string", pattern: "^[A-Z0-9_:-]{2,60}$" },
    module: { bsonType: "string", minLength: 2 },
    resource: { bsonType: "string", minLength: 1 },
    action: { bsonType: "string", minLength: 1 }
  }, ["permissionCode", "module", "action"]),

  "ADM01__role_bindings": createValidator({
    principalId: { bsonType: "string", minLength: 1 },
    principalType: { enum: ["USER", "SERVICE_ACCOUNT", "GROUP"] },
    roleCode: { bsonType: "string", minLength: 1 },
    scope: { bsonType: "string", minLength: 1 }
  }, ["principalId", "principalType", "roleCode", "scope"]),

  "ADM01__access_reviews": createValidator({
    reviewId: { bsonType: "string", minLength: 1 },
    cycleName: { bsonType: "string", minLength: 1 },
    reviewerId: { bsonType: "string" },
    status: { enum: ["PENDING", "IN_PROGRESS", "COMPLETED", "EXPIRED"] }
  }, ["reviewId", "cycleName", "status"]),

  "ADM01__subscription_plans": createValidator({
    planCode: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,50}$" },
    name: { bsonType: "string", minLength: 1 },
    billingCycle: { enum: ["MONTHLY", "QUARTERLY", "ANNUALLY"] },
    price: { bsonType: ["double", "decimal", "int", "long"], minimum: 0 },
    currency: { bsonType: "string", pattern: "^[A-Z]{3}$" },
    entitlements: { bsonType: "array", items: { bsonType: "string" } },
    isPublished: { bsonType: "bool" }
  }, ["planCode", "name", "billingCycle", "price", "currency"]),

  "ADM01__subscriptions": createValidator({
    subscriptionId: { bsonType: "string", minLength: 1 },
    instituteId: { bsonType: "string", minLength: 1 },
    planCode: { bsonType: "string", minLength: 1 },
    startDate: { bsonType: "date" },
    endDate: { bsonType: "date" },
    autoRenew: { bsonType: "bool" },
    status: { enum: ["TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED"] }
  }, ["subscriptionId", "instituteId", "planCode", "status"]),

  "ADM01__invoices": createValidator({
    invoiceNumber: { bsonType: "string", minLength: 1 },
    subscriptionId: { bsonType: "string", minLength: 1 },
    amount: { bsonType: ["double", "decimal", "int", "long"], minimum: 0 },
    currency: { bsonType: "string", pattern: "^[A-Z]{3}$" },
    dueDate: { bsonType: "date" },
    status: { enum: ["DRAFT", "ISSUED", "PAID", "VOID", "UNCOLLECTIBLE"] }
  }, ["invoiceNumber", "amount", "currency", "status"]),

  "ADM01__billing_gateway_transactions": createValidator({
    transactionId: { bsonType: "string", minLength: 1 },
    invoiceId: { bsonType: "string" },
    gateway: { bsonType: "string", minLength: 1 },
    amount: { bsonType: ["double", "decimal", "int", "long"], minimum: 0 },
    currency: { bsonType: "string", pattern: "^[A-Z]{3}$" },
    status: { enum: ["PENDING", "SUCCESS", "FAILED", "REFUNDED"] },
    gatewayRef: { bsonType: "string" }
  }, ["transactionId", "gateway", "amount", "status"]),

  "ADM01__usage_metrics": createValidator({
    metricName: { bsonType: "string", minLength: 1 },
    dimension: { bsonType: "string" },
    metricValue: { bsonType: ["double", "decimal", "int", "long"] },
    recordedAt: { bsonType: "date" }
  }, ["metricName", "metricValue", "recordedAt"]),

  "ADM01__platform_health": createValidator({
    serviceName: { bsonType: "string", minLength: 1 },
    status: { enum: ["HEALTHY", "DEGRADED", "UNHEALTHY"] },
    latencyMs: { bsonType: ["int", "long", "double"], minimum: 0 },
    lastHeartbeat: { bsonType: "date" }
  }, ["serviceName", "status", "lastHeartbeat"]),

  "ADM01__alerts": createValidator({
    alertId: { bsonType: "string", minLength: 1 },
    severity: { enum: ["INFO", "WARNING", "ERROR", "CRITICAL"] },
    title: { bsonType: "string", minLength: 1 },
    status: { enum: ["ACTIVE", "ACKNOWLEDGED", "RESOLVED"] }
  }, ["alertId", "severity", "title", "status"]),

  "ADM01__configuration_versions": createValidator({
    configKey: { bsonType: "string", minLength: 1 },
    versionNumber: { bsonType: ["int", "long"], minimum: 1 },
    appliedBy: { bsonType: "string" }
  }, ["configKey", "versionNumber"]),

  "ADM01__data_retention_policies": createValidator({
    policyCode: { bsonType: "string", minLength: 1 },
    entityType: { bsonType: "string", minLength: 1 },
    retentionDays: { bsonType: ["int", "long"], minimum: 1 },
    action: { enum: ["ARCHIVE", "DELETE", "ANONYMIZE"] }
  }, ["policyCode", "entityType", "retentionDays", "action"]),

  "ADM01__data_classifications": createValidator({
    classificationCode: { bsonType: "string", minLength: 1 },
    sensitivityLevel: { enum: ["LOW", "MEDIUM", "HIGH", "RESTRICTED"] },
    encryptionRequired: { bsonType: "bool" }
  }, ["classificationCode", "sensitivityLevel"]),

  "ADM01__export_requests": createValidator({
    exportId: { bsonType: "string", minLength: 1 },
    entityType: { bsonType: "string", minLength: 1 },
    status: { enum: ["REQUESTED", "PROCESSING", "COMPLETED", "FAILED"] },
    requestedBy: { bsonType: "string", minLength: 1 }
  }, ["exportId", "entityType", "status", "requestedBy"]),

  "ADM01__audit_logs": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    action: { bsonType: "string", minLength: 1 },
    principalId: { bsonType: "string", minLength: 1 },
    resourceType: { bsonType: "string", minLength: 1 },
    status: { bsonType: "string", minLength: 1 }
  }, ["eventId", "action", "principalId", "resourceType", "status"]),

  "ADM01__workflow_instances": createValidator({
    workflowId: { bsonType: "string", minLength: 1 },
    workflowType: { bsonType: "string", minLength: 1 },
    status: { enum: ["RUNNING", "COMPLETED", "FAILED", "SUSPENDED"] }
  }, ["workflowId", "workflowType", "status"]),

  "ADM01__idempotency_records": createValidator({
    idempotencyKey: { bsonType: "string", minLength: 1 },
    expiresAt: { bsonType: "date" }
  }, ["idempotencyKey", "expiresAt"]),

  "ADM01__outbox_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    aggregateType: { bsonType: "string", minLength: 1 },
    eventType: { bsonType: "string", minLength: 1 },
    status: { enum: ["PENDING", "DISPATCHED", "FAILED"] }
  }, ["eventId", "aggregateType", "eventType", "status"]),

  "ADM01__inbox_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    sourceService: { bsonType: "string", minLength: 1 },
    eventType: { bsonType: "string", minLength: 1 },
    status: { enum: ["RECEIVED", "PROCESSED", "FAILED"] }
  }, ["eventId", "sourceService", "eventType", "status"]),

  "ADM01__dead_letter_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    failureReason: { bsonType: "string", minLength: 1 },
    status: { enum: ["FAILED", "REPLAYED", "DISCARDED"] }
  }, ["eventId", "failureReason", "status"]),

  // --- ADM-02: College Operational Tier ---
  "ADM02__college_profiles": createValidator({
    collegeId: { bsonType: "string", minLength: 1 },
    code: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,30}$" },
    name: { bsonType: "string", minLength: 2, maxLength: 255 },
    legalName: { bsonType: "string", minLength: 2, maxLength: 255 },
    status: { enum: ["ACTIVE", "SUSPENDED", "INACTIVE"] },
    accreditationRefs: { bsonType: "array", items: { bsonType: "string" } },
    address: { bsonType: "string" },
    contact: { bsonType: "object" }
  }, ["collegeId", "code", "name", "status"]),

  "ADM02__departments": createValidator({
    departmentId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    code: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,30}$" },
    name: { bsonType: "string", minLength: 2, maxLength: 255 },
    headUserId: { bsonType: "string" },
    parentDepartmentId: { bsonType: ["string", "null"] },
    status: { enum: ["ACTIVE", "RETIRED"] }
  }, ["departmentId", "collegeId", "code", "name", "status"]),

  "ADM02__programs": createValidator({
    programId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    departmentId: { bsonType: "string", minLength: 1 },
    code: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,30}$" },
    name: { bsonType: "string", minLength: 2, maxLength: 255 },
    durationYears: { bsonType: ["int", "long"], minimum: 1, maximum: 10 },
    level: { bsonType: "string" },
    published: { bsonType: "bool" },
    status: { enum: ["ACTIVE", "DISCONTINUED", "DRAFT"] }
  }, ["programId", "collegeId", "departmentId", "code", "name", "durationYears", "status"]),

  "ADM02__college_calendar_configuration": createValidator({
    calendarConfigId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    academicYear: { bsonType: "string", minLength: 4 },
    workingDaysPerWeek: { bsonType: ["int", "long"], minimum: 1, maximum: 7 },
    status: { enum: ["DRAFT", "PUBLISHED", "ARCHIVED"] }
  }, ["calendarConfigId", "collegeId", "academicYear", "status"]),

  "ADM02__college_settings": createValidator({
    settingKey: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    settingValue: { bsonType: "string" },
    dataType: { bsonType: "string" },
    isOverridden: { bsonType: "bool" }
  }, ["collegeId", "settingKey", "settingValue"]),

  "ADM02__feature_overrides": createValidator({
    flagKey: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    enabled: { bsonType: "bool" }
  }, ["collegeId", "flagKey", "enabled"]),

  "ADM02__college_users": createValidator({
    userId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    username: { bsonType: "string", minLength: 3 },
    email: { bsonType: "string", pattern: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$" },
    fullName: { bsonType: "string", minLength: 1 },
    departmentId: { bsonType: "string" },
    status: { enum: ["ACTIVE", "SUSPENDED", "INACTIVE"] }
  }, ["userId", "collegeId", "username", "email", "status"]),

  "ADM02__college_roles": createValidator({
    roleCode: { bsonType: "string", pattern: "^[A-Z0-9_-]{2,50}$" },
    collegeId: { bsonType: "string", minLength: 1 },
    name: { bsonType: "string", minLength: 1 },
    permissions: { bsonType: "array", items: { bsonType: "string" } }
  }, ["roleCode", "collegeId", "name"]),

  "ADM02__role_assignments": createValidator({
    assignmentId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    userId: { bsonType: "string", minLength: 1 },
    roleCode: { bsonType: "string", minLength: 1 }
  }, ["assignmentId", "collegeId", "userId", "roleCode"]),

  "ADM02__department_access": createValidator({
    accessId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    userId: { bsonType: "string", minLength: 1 },
    departmentId: { bsonType: "string", minLength: 1 },
    accessLevel: { enum: ["READ", "WRITE", "ADMIN"] }
  }, ["accessId", "collegeId", "userId", "departmentId", "accessLevel"]),

  "ADM02__user_import_jobs": createValidator({
    jobId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    entityType: { enum: ["STUDENT", "FACULTY", "DEPARTMENT", "COURSE"] },
    mode: { enum: ["INSERT", "UPSERT", "REPLACE"] },
    status: { enum: ["PENDING", "PROCESSING", "COMPLETED", "FAILED"] },
    totalRows: { bsonType: ["int", "long"], minimum: 0 },
    processedRows: { bsonType: ["int", "long"], minimum: 0 },
    failedRows: { bsonType: ["int", "long"], minimum: 0 }
  }, ["jobId", "collegeId", "entityType", "status"]),

  "ADM02__data_import_rows": createValidator({
    rowId: { bsonType: "string", minLength: 1 },
    jobId: { bsonType: "string", minLength: 1 },
    rowNumber: { bsonType: ["int", "long"], minimum: 1 },
    status: { enum: ["PENDING", "SUCCESS", "VALIDATION_FAILED", "SYSTEM_ERROR"] }
  }, ["jobId", "rowNumber", "status"]),

  "ADM02__data_quality_issues": createValidator({
    issueId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    jobId: { bsonType: "string" },
    severity: { enum: ["WARNING", "ERROR"] },
    ruleName: { bsonType: "string", minLength: 1 }
  }, ["issueId", "collegeId", "severity", "ruleName"]),

  "ADM02__document_metadata": createValidator({
    documentId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    documentType: { bsonType: "string", minLength: 1 },
    title: { bsonType: "string", minLength: 1 },
    ownerId: { bsonType: "string" },
    classification: { enum: ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"] },
    status: { enum: ["DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PUBLISHED", "ARCHIVED"] },
    checksum: { bsonType: "string" },
    sizeBytes: { bsonType: ["int", "long", "double"], minimum: 0 }
  }, ["documentId", "collegeId", "documentType", "title", "classification", "status"]),

  "ADM02__report_definitions": createValidator({
    reportId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    name: { bsonType: "string", minLength: 1 }
  }, ["reportId", "collegeId", "name"]),

  "ADM02__report_runs": createValidator({
    runId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    reportId: { bsonType: "string", minLength: 1 },
    status: { enum: ["QUEUED", "RUNNING", "COMPLETED", "FAILED"] }
  }, ["runId", "collegeId", "reportId", "status"]),

  "ADM02__report_schedules": createValidator({
    scheduleId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    reportId: { bsonType: "string", minLength: 1 },
    cronExpression: { bsonType: "string", minLength: 5 },
    active: { bsonType: "bool" }
  }, ["scheduleId", "collegeId", "reportId", "cronExpression", "active"]),

  "ADM02__dashboard_snapshots": createValidator({
    snapshotId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    dashboardType: { bsonType: "string", minLength: 1 },
    capturedAt: { bsonType: "date" }
  }, ["snapshotId", "collegeId", "dashboardType", "capturedAt"]),

  "ADM02__approval_requests": createValidator({
    requestId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    resourceType: { bsonType: "string", minLength: 1 },
    resourceId: { bsonType: "string", minLength: 1 },
    status: { enum: ["PENDING", "APPROVED", "REJECTED", "CANCELLED"] }
  }, ["requestId", "collegeId", "resourceType", "resourceId", "status"]),

  "ADM02__workflow_tasks": createValidator({
    taskId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    status: { enum: ["OPEN", "IN_PROGRESS", "COMPLETED", "BLOCKED"] }
  }, ["taskId", "collegeId", "status"]),

  "ADM02__data_export_requests": createValidator({
    exportId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    entityType: { bsonType: "string", minLength: 1 },
    status: { enum: ["REQUESTED", "PROCESSING", "COMPLETED", "FAILED"] }
  }, ["exportId", "collegeId", "status"]),

  "ADM02__configuration_history": createValidator({
    historyId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    settingKey: { bsonType: "string", minLength: 1 },
    changedBy: { bsonType: "string" }
  }, ["historyId", "collegeId", "settingKey", "changedBy"]),

  "ADM02__audit_logs": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    collegeId: { bsonType: "string", minLength: 1 },
    action: { bsonType: "string", minLength: 1 },
    principalId: { bsonType: "string", minLength: 1 },
    resourceType: { bsonType: "string", minLength: 1 },
    status: { bsonType: "string", minLength: 1 }
  }, ["eventId", "collegeId", "action", "principalId", "resourceType", "status"]),

  "ADM02__idempotency_records": createValidator({
    idempotencyKey: { bsonType: "string", minLength: 1 },
    expiresAt: { bsonType: "date" }
  }, ["idempotencyKey", "expiresAt"]),

  "ADM02__outbox_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    aggregateType: { bsonType: "string", minLength: 1 },
    eventType: { bsonType: "string", minLength: 1 },
    status: { enum: ["PENDING", "DISPATCHED", "FAILED"] }
  }, ["eventId", "aggregateType", "eventType", "status"]),

  "ADM02__inbox_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    sourceService: { bsonType: "string", minLength: 1 },
    eventType: { bsonType: "string", minLength: 1 },
    status: { enum: ["RECEIVED", "PROCESSED", "FAILED"] }
  }, ["eventId", "sourceService", "eventType", "status"]),

  "ADM02__dead_letter_events": createValidator({
    eventId: { bsonType: "string", minLength: 1 },
    failureReason: { bsonType: "string", minLength: 1 },
    status: { enum: ["FAILED", "REPLAYED", "DISCARDED"] }
  }, ["eventId", "failureReason", "status"])
};

// -----------------------------------------------------------------------------
// ADM Unique and Compound Business Indexes
// -----------------------------------------------------------------------------
const admIndexes = {
  // ADM-01 Indexes
  "ADM01__institutes": [
    { key: { instituteId: 1 }, options: { unique: true, sparse: true, name: "ux_institute_id" } },
    { key: { code: 1 }, options: { unique: true, sparse: true, name: "ux_institute_code" } },
    { key: { status: 1, updatedAt: -1 }, options: { name: "ix_institute_status_updated" } }
  ],
  "ADM01__colleges": [
    { key: { collegeId: 1 }, options: { unique: true, sparse: true, name: "ux_college_id" } },
    { key: { instituteId: 1, code: 1 }, options: { unique: true, sparse: true, name: "ux_institute_college_code" } },
    { key: { instituteId: 1, provisioningStatus: 1 }, options: { name: "ix_institute_provisioning_status" } }
  ],
  "ADM01__tenant_provisioning": [
    { key: { provisioningId: 1 }, options: { unique: true, sparse: true, name: "ux_provisioning_id" } },
    { key: { idempotencyKey: 1 }, options: { unique: true, sparse: true, name: "ux_provisioning_idempotency" } },
    { key: { collegeId: 1, createdAt: -1 }, options: { name: "ix_college_provisioning_history" } }
  ],
  "ADM01__global_settings": [
    { key: { scope: 1, settingKey: 1, version: 1 }, options: { unique: true, partialFilterExpression: { settingKey: { $exists: true } }, name: "ux_setting_scope_key_ver" } },
    { key: { settingKey: 1, effectiveFrom: -1 }, options: { name: "ix_setting_effective" } }
  ],
  "ADM01__feature_flags": [
    { key: { flagKey: 1 }, options: { unique: true, sparse: true, name: "ux_feature_flag_key" } }
  ],
  "ADM01__global_policies": [
    { key: { policyCode: 1 }, options: { unique: true, sparse: true, name: "ux_global_policy_code" } }
  ],
  "ADM01__admin_users": [
    { key: { username: 1 }, options: { unique: true, sparse: true, name: "ux_admin_username" } },
    { key: { email: 1 }, options: { unique: true, sparse: true, name: "ux_admin_email" } }
  ],
  "ADM01__roles": [
    { key: { roleCode: 1 }, options: { unique: true, sparse: true, name: "ux_admin_role_code" } }
  ],
  "ADM01__permissions": [
    { key: { permissionCode: 1 }, options: { unique: true, sparse: true, name: "ux_permission_code" } }
  ],
  "ADM01__role_bindings": [
    { key: { principalId: 1, roleCode: 1, scope: 1 }, options: { unique: true, partialFilterExpression: { principalId: { $exists: true } }, name: "ux_role_binding" } }
  ],
  "ADM01__subscription_plans": [
    { key: { planCode: 1 }, options: { unique: true, sparse: true, name: "ux_plan_code" } }
  ],
  "ADM01__subscriptions": [
    { key: { subscriptionId: 1 }, options: { unique: true, sparse: true, name: "ux_subscription_id" } },
    { key: { instituteId: 1, status: 1 }, options: { name: "ix_institute_subscription" } }
  ],
  "ADM01__invoices": [
    { key: { invoiceNumber: 1 }, options: { unique: true, sparse: true, name: "ux_invoice_number" } },
    { key: { subscriptionId: 1, status: 1 }, options: { name: "ix_subscription_invoices" } }
  ],
  "ADM01__billing_gateway_transactions": [
    { key: { transactionId: 1 }, options: { unique: true, sparse: true, name: "ux_transaction_id" } },
    { key: { invoiceId: 1 }, options: { name: "ix_invoice_transactions" } }
  ],

  // ADM-02 Indexes
  "ADM02__college_profiles": [
    { key: { collegeId: 1 }, options: { unique: true, sparse: true, name: "ux_college_profile_id" } },
    { key: { tenantId: 1, code: 1 }, options: { unique: true, partialFilterExpression: { code: { $exists: true } }, name: "ux_tenant_college_code" } }
  ],
  "ADM02__departments": [
    { key: { collegeId: 1, code: 1 }, options: { unique: true, partialFilterExpression: { code: { $exists: true } }, name: "ux_college_department_code" } },
    { key: { collegeId: 1, status: 1 }, options: { name: "ix_college_department_status" } },
    { key: { parentDepartmentId: 1 }, options: { sparse: true, name: "ix_parent_department" } }
  ],
  "ADM02__programs": [
    { key: { collegeId: 1, code: 1 }, options: { unique: true, partialFilterExpression: { code: { $exists: true } }, name: "ux_college_program_code" } },
    { key: { collegeId: 1, departmentId: 1, status: 1 }, options: { name: "ix_college_dept_program_status" } }
  ],
  "ADM02__college_calendar_configuration": [
    { key: { collegeId: 1, academicYear: 1 }, options: { unique: true, partialFilterExpression: { academicYear: { $exists: true } }, name: "ux_college_academic_year_calendar" } }
  ],
  "ADM02__college_settings": [
    { key: { collegeId: 1, settingKey: 1 }, options: { unique: true, partialFilterExpression: { settingKey: { $exists: true } }, name: "ux_college_setting_key" } }
  ],
  "ADM02__feature_overrides": [
    { key: { collegeId: 1, flagKey: 1 }, options: { unique: true, partialFilterExpression: { flagKey: { $exists: true } }, name: "ux_college_feature_override" } }
  ],
  "ADM02__college_users": [
    { key: { collegeId: 1, username: 1 }, options: { unique: true, partialFilterExpression: { username: { $exists: true } }, name: "ux_college_username" } },
    { key: { collegeId: 1, email: 1 }, options: { unique: true, partialFilterExpression: { email: { $exists: true } }, name: "ux_college_user_email" } },
    { key: { collegeId: 1, departmentId: 1 }, options: { name: "ix_college_user_department" } }
  ],
  "ADM02__college_roles": [
    { key: { collegeId: 1, roleCode: 1 }, options: { unique: true, partialFilterExpression: { roleCode: { $exists: true } }, name: "ux_college_role_code" } }
  ],
  "ADM02__role_assignments": [
    { key: { collegeId: 1, userId: 1, roleCode: 1 }, options: { unique: true, partialFilterExpression: { userId: { $exists: true } }, name: "ux_college_role_assignment" } }
  ],
  "ADM02__department_access": [
    { key: { collegeId: 1, userId: 1, departmentId: 1 }, options: { unique: true, partialFilterExpression: { departmentId: { $exists: true } }, name: "ux_college_dept_access" } }
  ],
  "ADM02__user_import_jobs": [
    { key: { jobId: 1 }, options: { unique: true, sparse: true, name: "ux_user_import_job_id" } },
    { key: { collegeId: 1, status: 1, createdAt: -1 }, options: { name: "ix_college_import_jobs" } }
  ],
  "ADM02__data_import_rows": [
    { key: { jobId: 1, rowNumber: 1 }, options: { unique: true, partialFilterExpression: { rowNumber: { $exists: true } }, name: "ux_job_row_number" } },
    { key: { jobId: 1, status: 1 }, options: { name: "ix_job_row_status" } }
  ],
  "ADM02__data_quality_issues": [
    { key: { issueId: 1 }, options: { unique: true, sparse: true, name: "ux_data_issue_id" } },
    { key: { collegeId: 1, jobId: 1 }, options: { name: "ix_college_job_issues" } }
  ],
  "ADM02__document_metadata": [
    { key: { documentId: 1 }, options: { unique: true, sparse: true, name: "ux_document_id" } },
    { key: { collegeId: 1, documentType: 1, status: 1 }, options: { name: "ix_college_doc_type_status" } },
    { key: { collegeId: 1, classification: 1 }, options: { name: "ix_college_doc_classification" } }
  ],
  "ADM02__report_definitions": [
    { key: { reportId: 1 }, options: { unique: true, sparse: true, name: "ux_report_id" } },
    { key: { collegeId: 1, name: 1 }, options: { unique: true, partialFilterExpression: { name: { $exists: true } }, name: "ux_college_report_name" } }
  ],
  "ADM02__report_runs": [
    { key: { runId: 1 }, options: { unique: true, sparse: true, name: "ux_report_run_id" } },
    { key: { collegeId: 1, reportId: 1, createdAt: -1 }, options: { name: "ix_college_report_runs" } }
  ],
  "ADM02__approval_requests": [
    { key: { requestId: 1 }, options: { unique: true, sparse: true, name: "ux_approval_request_id" } },
    { key: { collegeId: 1, status: 1, createdAt: -1 }, options: { name: "ix_college_approvals" } }
  ],
  "ADM02__workflow_tasks": [
    { key: { taskId: 1 }, options: { unique: true, sparse: true, name: "ux_workflow_task_id" } },
    { key: { collegeId: 1, assigneeId: 1, status: 1 }, options: { name: "ix_college_user_tasks" } }
  ]
};

// -----------------------------------------------------------------------------
// Execution Routine
// -----------------------------------------------------------------------------
function applyValidatorsAndIndexes() {
  print("============================================================");
  print(" Applying ADM-01 & ADM-02 Validators & Unique Indexes");
  print(" Database: " + DB_NAME);
  print(" Target Collections: " + Object.keys(admValidators).length);
  print("============================================================");

  let updatedCount = 0;
  let createdCount = 0;
  let indexCount = 0;

  const existingCollections = dbx.getCollectionNames();

  for (const [colName, validator] of Object.entries(admValidators)) {
    const exists = existingCollections.includes(colName);

    if (!exists) {
      dbx.createCollection(colName, {
        validator,
        validationLevel: "moderate",
        validationAction: "error"
      });
      print("CREATED  " + colName + " (with $jsonSchema)");
      createdCount++;
    } else {
      dbx.runCommand({
        collMod: colName,
        validator,
        validationLevel: "moderate",
        validationAction: "error"
      });
      print("UPDATED  " + colName + " (with $jsonSchema)");
      updatedCount++;
    }

    // Apply baseline compound indexes
    const c = dbx.getCollection(colName);
    c.createIndex({ tenantId: 1, status: 1, updatedAt: -1 }, { name: "ix_tenant_status_updated" });
    c.createIndex({ tenantId: 1, updatedAt: -1 }, { name: "ix_tenant_updated" });

    // Apply specific ADM unique & compound indexes
    if (admIndexes[colName]) {
      for (const idx of admIndexes[colName]) {
        try {
          c.createIndex(idx.key, idx.options || {});
          indexCount++;
        } catch (e) {
          print("  [INDEX NOTICE] " + colName + " - " + e.message);
        }
      }
    }
  }

  print("============================================================");
  print(" SUMMARY OF APPLIED ADM VALIDATIONS & INDEXES");
  print(" Created Collections: " + createdCount);
  print(" Updated Collections: " + updatedCount);
  print(" Total Collections Processed: " + (createdCount + updatedCount));
  print(" Specific ADM Indexes Configured: " + indexCount);
  print(" Validation Level: moderate | Validation Action: error");
  print("============================================================");
}

applyValidatorsAndIndexes();
