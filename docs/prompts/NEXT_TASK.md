# Next Task

**STATUS: Frontend 1b contentBase64 DONE (2026-11-16).** Upload path wired UI → BFF → Gateway → document-service → Supabase Storage (Dev).

## Just completed

- Student documents desk: real file picker → `contentBase64` + SHA-256 + MIME/size client prep
- BFF `/api/student/documents` (+ resubmit routes): `guardDocumentUploadPayload` — rejects missing bytes, bad MIME, >10MB, size mismatch, client `storagePath`, credential fields; never forwards service-role
- Java resubmit path: `contentBase64` required when storage configured; server-built object path + `putObject`
- Verification:
  - `npx tsc --noEmit` → 0 errors
  - `node --experimental-strip-types scripts/verify_document_upload_guard.mjs` → 11/11
  - `mvn test -pl services/student-services/document-service` → 128/128
  - Live smoke `scripts/demo/smoke_frontend_1b_contentbase64.mjs` → **13/13 PASS**
  - Evidence: `docs/reports/STM06_FRONTEND_1B_CONTENTBASE64_SMOKE_2026-11-16.json`

## Remaining gaps (EN-002 still partially completed)

- Orphan object delete API not implemented (reconciliation procedure in runbook)
- Live malware AV still open gap (D11)
- Frontend Registrar mapping (1c) — **BLOCKED** until product pickup

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE** (activation + Path C smoke).
1. **STM-06-EN-002** — **partially completed** (Phase B live PASS + frontend 1b DONE; orphan API + malware remain).
1b. **Frontend:** `contentBase64` upload — **DONE** (2026-11-16).
1c. **Frontend:** Registrar role mapping — **BLOCKED** until product pickup.
2. **STM-CROSS-EN-010** e2e — **BLOCKED** (harness approval).
6. **STM-06-US-015** — **BLOCKED** (notification DDL).
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED**.

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
- Next eligible without product decision: none in this queue (1c blocked). Await owner pickup.
