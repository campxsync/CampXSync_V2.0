# STM-08 Academic History & Transcripts — Design Note

**Status:** BLOCKED — awaiting owner approval to create a new microservice (no code under `services/` for STM-08).  
**Date:** 2026-11-16  
**Queue rule:** New Service Gate (Agreement V2.1 / owner queue). Do not implement stubs or alternative hosts.

---

## 1. Purpose

Own verified academic history ingestion from EXM-01, credit/progress views, transcript generation, awards, and revaluation outcome consumption for the Student Module (STM-08).

## 2. Proposed data model (Supabase PostgreSQL only)

Suggested service-owned schema (names illustrative; final DDL only after owner approval):

| Table | Role |
|---|---|
| `stu.academic_records` | Verified result rows (student, programme, term, subject, grade/credits) |
| `stu.academic_record_sources` | EXM-01 source references (idempotency keys) |
| `stu.academic_record_corrections` | Previous vs revised values |
| `stu.transcript_requests` | Transcript request/generation status |
| `stu.academic_awards` | Recorded awards |

All new tenant tables: `ENABLE` + `FORCE ROW LEVEL SECURITY`; policies on `core.current_tenant_id()`.

## 3. API list (draft)

- `POST /api/v1/academic-history/results/ingest` — EXM-01 approved results
- `GET /api/v1/academic-history/students/{id}/records`
- `GET /api/v1/academic-history/students/{id}/credits`
- `GET /api/v1/academic-history/students/{id}/progress`
- `POST /api/v1/academic-history/transcripts`
- `GET /api/v1/academic-history/transcripts/{id}`

## 4. RBAC rows used

Backlog CSV STM-08-US-001..018 / EN-001..007. Canonical codes as listed in CSV; no new role grants without owner decision. FLAG-007 still open for matrix policy items.

## 5. Dependencies

- Existing IAM + API Gateway (:8080)
- STM-01 student identity, STM-03 enrolment, STM-04 lifecycle, STM-06 documents, STM-09 requests
- EXM-01 official results (external contract)
- Shared storage for generated transcripts (existing patterns only; no new infra)

## 6. Test plan

- Unit: ingestion idempotency, tenant isolation, role denials, correction history
- Integration: Dev project `azulbaxszsqxymqmlwfe` only
- Negative tests from CSV Required Negative Tests column

## 7. Deployment needs (existing patterns only)

- New Java service JAR under `services/` (owner must approve creation)
- Gateway route registration + HMAC
- `start_stack.ps1` / health checks extension
- No Kafka until CROSS-EN-007 unblocked

## 8. Open decisions (owner)

1. Approve creation of `academic-history-service` (or named equivalent) under `services/`.
2. Confirm EXM-01 contract host and auth.
3. Confirm transcript storage bucket ownership vs STM-06.
4. Close FLAG-007 items that touch STM-08.

### Open items delta

- 2026-11-16: Gate applied; no implementation until owner approval.
