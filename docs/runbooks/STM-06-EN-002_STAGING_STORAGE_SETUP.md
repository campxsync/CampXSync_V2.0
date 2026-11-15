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
9. Verify authorization policy behavior before pilot use: anonymous requests cannot list, read, upload, overwrite, or delete objects; authenticated client requests cannot access objects directly; only the approved server-side document-service path can perform the required operations. Record the exact test identity/path and outcome without recording credentials or tokens.
10. Verify tenant-prefix isolation using at least two distinct staging tenant prefixes: a request authorized for one tenant must not read, overwrite, or delete an object under another tenant's prefix. Test upload, download, and deletion paths where supported. Any cross-tenant success is a STOP condition.
11. Verify object-path handling rejects path traversal, absolute paths, unexpected separators, and unsafe filename/path components. Confirm the service constructs the tenant/student/document path rather than trusting a client-supplied storage path.
12. Verify failure cleanup: if object upload succeeds but the corresponding metadata/database operation fails, the service must attempt to remove the orphaned object and log a correlation identifier plus outcome (never secrets). If cleanup cannot be guaranteed, document the residual orphan-reconciliation procedure before pilot use; do not silently claim cleanup is complete.
13. Verify MIME type and size enforcement at the service boundary as well as the bucket configuration. The allowed types and 10 MB limit must match the service's enforced rules.

## Security verification checklist (must be completed before pilot use)

- [ ] Bucket is private; anonymous list/read/write/delete attempts are denied.
- [ ] Authenticated client-side direct Storage access is denied; clients use document-service endpoints only.
- [ ] Server-side document-service access succeeds only with the configured secret held in the service environment.
- [ ] Tenant-prefix isolation is verified across upload, download, overwrite, and delete operations that the service supports.
- [ ] Path traversal and unsafe path/filename inputs are rejected; object paths are server-constructed.
- [ ] MIME type and 10 MB size limits are enforced by the service and match bucket configuration.
- [ ] Upload/database failure behavior is verified, including orphan-object cleanup or a documented reconciliation procedure.
- [ ] Logs contain no service-role key, access token, signed download token, or document contents.
- [ ] Malware scanning remains an explicit unresolved gap; do not describe uploaded documents as malware-scanned.

Record test date, environment, test case, outcome, and non-secret correlation/reference IDs. Do not include credentials, tokens, or document contents in evidence.

## Expected results

- Upload with `contentBase64` stores an object at `tenants/<tenantId>/students/<studentId>/<docId>/v1-<fileName>`.
- `GET /api/v1/documents/download?token=...` streams bytes through document-service after HMAC signed-token check.
- Clients never receive Supabase signed URLs; no direct browser-to-Storage upload/download.
- Anonymous and unauthorized authenticated access is denied; tenant-prefix isolation is verified.
- Unsafe object paths are rejected and upload/database failure cleanup is verified or has a documented reconciliation procedure.
- `doc.documents.storage_path` / `storage_bucket` updated; no `bytea` columns used.

## Pre-apply authorization gates

Do not create the bucket, change Storage policies, set service environment variables, upload objects, or restart services under this prepare-only runbook. Before applying, obtain a separate explicit owner authorization for the development project and the exact bucket, policy, configuration, and verification steps. Complete the security verification checklist before pilot use. Production remains prohibited.

## Rollback

1. Remove or unset `CAMPX_SUPABASE_URL` and `CAMPX_SUPABASE_SERVICE_ROLE_KEY` on document-service → metadata-only local/test profile.
2. Optionally empty/delete the private bucket objects for the pilot tenant prefix only (owner-approved).
3. Do not drop `doc.*` tables or change RLS as part of this rollback.

## Out of scope (Phase A / B)

- Live malware AV scanner (new tool) — open gap; no malware-scanning guarantee may be made until implemented and verified.
- Frontend `contentBase64` wiring (queue item 1b).
- Creating Registrar role (D1).
