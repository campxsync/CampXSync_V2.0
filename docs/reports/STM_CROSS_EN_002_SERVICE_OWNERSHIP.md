# STM-CROSS-EN-002 — Service-Owned Data Boundaries

**Date:** 2026-11-14  
**Rule:** No duplicate ownership tables; each domain service owns its schema slice.

| Service | Port | Owns |
|---|---|---|
| admissions-service | 8094 | `stu.applications`, admission_* |
| enrollment-service | 8095 | `stu.enrolments`, enrolment_* , catalogue |
| lifecycle-service | 8096 | `stu.lifecycle_status`, `lifecycle_requests`, `lifecycle_history` |
| guardian-service | 8097 | `stu.guardianships`, `core.person_emergency_contacts` |
| document-service | 8098 | `doc.documents`, `document_types`, `stage_document_requirements`, versions |
| student-service | 8093 | `stu.students` / persons profile path |
| API Gateway | 8080 | Ingress only (no domain tables) |

**Audit note:** `doc.lifecycle_requests` exists in DB snapshot but lifecycle domain ownership remains `stu.lifecycle_*` via lifecycle-service — treat `doc.lifecycle_requests` as non-authoritative / investigate separately; no DDL this story.
