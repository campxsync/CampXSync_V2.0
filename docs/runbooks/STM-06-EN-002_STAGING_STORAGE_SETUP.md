# STM-06-EN-002 — Staging Supabase Storage setup (Phase B applied on Dev)

**Status:** PHASE B LIVE PASS on Dev — private bucket + deny policies + service-role put/get/list/delete verified; document-service storage configured  
**Date prepared:** 2026-11-15  
**Date applied (bucket/policies):** 2026-11-15  
**Date live verified:** 2026-11-16 (`CONTINUE EN-002-STORAGE-LIVE`)  
**Owner approval:** `APPROVED EN-002-STORAGE azulbaxszsqxymqmlwfe`  
**Target:** Development Supabase project only (`azulbaxszsqxymqmlwfe`). Production forbidden.

## Purpose

Create a **private** Storage bucket for student document bytes. Relational tables (`doc.documents`, `doc.document_versions`) keep **metadata and object path only** — never bytes.

## Approved document-service config keys (D11)

| Key | Role | Notes |
|---|---|---|
| `CAMPX_SUPABASE_URL` | Project URL | Server-side document-service only — set in `.env.audit` (gitignored) |
| `CAMPX_SUPABASE_SERVICE_ROLE_KEY` | Service role | **Server-side only.** Placeholder comment in `.env.audit`; owner must paste from Dev Dashboard → Settings → API. Never frontend, public env, Git, logs, errors, or reports |
| `CAMPX_STORAGE_BUCKET` | Bucket name | `student-documents` |

## Applied on Dev (2026-11-15)

1. Bucket `student-documents`: `public=false`, `file_size_limit=10485760`, MIME allowlist `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
2. Restrictive deny policies on `storage.objects` for roles `anon` and `authenticated` scoped to this bucket (`student_documents_anon_deny`, `student_documents_authenticated_deny`). `service_role` bypasses RLS for document-service.
3. Non-secret env keys appended to gitignored `.env.audit` (`CAMPX_SUPABASE_URL`, `CAMPX_STORAGE_BUCKET`).
4. Deny verification evidence: `docs/reports/STM06_EN002_STORAGE_APPLY_VERIFY_2026-11-15.json` (**8/8 PASS**).

## Security verification checklist

- [x] Bucket is private; anonymous upload/download/delete denied (HTTP 400). List returns empty `[]` (no object leakage).
- [x] Authenticated client-side direct Storage upload/download denied; list empty.
- [x] Server-side service-role put/get succeeds (2026-11-16 live smoke 11/11). Document-service restart logged `object storage configured bucket=student-documents` (key not logged).
- [x] Tenant-prefix list for tenant A does not leak tenant B paths; both smoke objects deleted after test.
- [x] Path traversal rejection exists in `SupabaseStorageHttpClient.requirePath` (`..` and absolute paths).
- [x] MIME type and 10 MB limits configured on bucket; service enforces same (`DocumentDomainService.MAX_FILE_SIZE_BYTES`).
- [x] Upload/database failure orphan cleanup — API implemented + **live Dev cleanup executed** (2026-11-16, owner-authorized): `POST …/orphans/cleanup` `confirmExecute=true` on `azulbaxszsqxymqmlwfe` / `student-documents`. Tenant A deleted 1 confirmed orphan, retained 1 referenced; cross-tenant denial PASS; evidence `docs/reports/STM06_EN002_LIVE_ORPHAN_CLEANUP_2026-11-16.json` (12/12).
- [x] Verification report contains no service-role key, access token, or document contents.
- [x] Malware scanning remains an explicit unresolved gap.

## Expected results (after service-role key is set)

- Restart document-service with `CAMPX_SUPABASE_*` set → log: object storage configured bucket=student-documents (key never logged).
- Upload with `contentBase64` stores object at `tenants/<tenantId>/students/<studentId>/<docId>/v1-<fileName>`.
- `GET /api/v1/documents/download?token=...` streams bytes through document-service after HMAC signed-token check.
- Clients never receive Supabase signed URLs.

## Rollback

1. Unset `CAMPX_SUPABASE_URL` / `CAMPX_SUPABASE_SERVICE_ROLE_KEY` on document-service → metadata-only profile.
2. Optionally empty/delete private bucket objects for the pilot tenant prefix only (owner-approved).
3. Do not drop `doc.*` tables or change broader RLS as part of this rollback.

## Out of scope

- Live malware AV scanner (new tool).
- Frontend `contentBase64` wiring (queue item 1b).
- Live commercial malware AV (EN-003; separate gate).
