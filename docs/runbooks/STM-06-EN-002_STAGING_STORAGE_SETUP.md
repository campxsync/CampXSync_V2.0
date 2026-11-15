# STM-06-EN-002 — Staging Supabase Storage setup (Phase A prepare-only)

**Status:** PREPARED — **DO NOT APPLY** until owner authorizes Phase B in a separate prompt.  
**Date prepared:** 2026-11-15  
**Target:** Development Supabase project only (`azulbaxszsqxymqmlwfe`). Production forbidden.

## Purpose

Create a **private** Storage bucket for student document bytes. Relational tables (`doc.documents`, `doc.document_versions`) keep **metadata and object path only** — never bytes.

## Approved document-service config keys (D11)

| Key | Role | Notes |
|---|---|---|
| `CAMPX_SUPABASE_URL` | Project URL | Server-side document-service only |
| `CAMPX_SUPABASE_SERVICE_ROLE_KEY` | Service role | **Server-side only.** Never frontend, public env, Git, logs, errors, or reports |
| `CAMPX_STORAGE_BUCKET` | Bucket name | Default `student-documents` if unset |

## Steps (apply only after Phase B approval)

1. In Supabase Dashboard → Storage → New bucket.
2. Name: `student-documents` (or value matching `CAMPX_STORAGE_BUCKET`).
3. Set bucket to **Private** (not public).
4. Configure allowed MIME types at least: `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
5. Set max object size to match service limit: **10 MB** (`DocumentDomainService.MAX_FILE_SIZE_BYTES`).
6. Add storage policies that deny anonymous public read/write; allow only service-role / authenticated server paths. Prefer path prefix `tenants/{tenant_id}/...`.
7. Set document-service environment variables (names above). Do not print values.
8. Restart document-service (`8098`) and confirm log line: object storage configured bucket=… (key never logged).

## Expected results

- Upload with `contentBase64` stores an object at `tenants/<tenantId>/students/<studentId>/<docId>/v1-<fileName>`.
- `GET /api/v1/documents/download?token=...` streams bytes through document-service after HMAC signed-token check.
- Clients never receive Supabase signed URLs; no direct browser-to-Storage upload/download.
- `doc.documents.storage_path` / `storage_bucket` updated; no `bytea` columns used.

## Rollback

1. Remove or unset `CAMPX_SUPABASE_URL` and `CAMPX_SUPABASE_SERVICE_ROLE_KEY` on document-service → metadata-only local/test profile.
2. Optionally empty/delete the private bucket objects for the pilot tenant prefix only (owner-approved).
3. Do not drop `doc.*` tables or change RLS as part of this rollback.

## Out of scope (Phase A / B)

- Live malware AV scanner (new tool) — open gap.
- Frontend `contentBase64` wiring (queue item 1b).
- Creating Registrar role (D1).
