# ACD-03 Subject Management Service - Deployment, Security & Reliability Guide

## 1. Security Architecture (§47-50)

### 1.1 Data Classification Tiers
- **L1 Public**: `subjectCode`, `name`, `shortName`, `description`, `credits`, `status` (Active only). Visible to Students, Parents, External API consumers.
- **L2 Internal**: `departmentId`, `campusId`, `subjectType`, `classification`, `contactHours`, `academicYear`, non-sensitive metadata tags.
- **L3 Confidential**: `regulatoryCode`, `prerequisiteNotes`, approval and actor details (`approvedBy`, `publishedBy`). Stripped from responses for unprivileged callers.
- **L4 Restricted**: Technical operational logs, idempotency records, broker connection credentials, dead-letter payloads.

### 1.2 Transport & Rest Encryption (TLS & At-Rest)
- **In-Transit**: All client-to-gateway and gateway-to-service communication supports TLS 1.2+.
  - Enabled via `-DHTTPS_KEYSTORE_PATH=/path/to/keystore.jks -DHTTPS_KEYSTORE_PASSWORD=secret`.
- **At-Rest**: MongoDB WiredTiger encryption-at-rest configured with AWS KMS or HashiCorp Vault.

---

## 2. Resilience, Retry & Idempotency (§54-56)

### 2.1 Bounded Exponential Backoff
- Base delay: `1,000 ms`, Max delay: `30,000 ms`, Max retries: `5`, Jitter factor: `0.1`.
- Deterministic 4xx errors are **never retried**.
- Transient 5xx server errors, timeouts, and broker latency are retried with jitter.

### 2.2 Idempotency Engine
- Mutating commands (`POST /api/v1/academics/subjects`, publish, deactivate, reactivate) accept the `Idempotency-Key` header.
- Cached response returned on duplicate retry with matching payload hash.
- Key reuse with conflicting payload returns `422 IDEMP_HASH_MISMATCH`.
- Automatic hourly background cleanup purges records older than 24 hours.

---

## 3. Disaster Recovery & Backup (§57)
- **RPO (Recovery Point Objective)**: < 15 minutes.
- **RTO (Recovery Time Objective)**: < 1 hour.
- Automated daily logical backups and continuous MongoDB oplog archiving.
