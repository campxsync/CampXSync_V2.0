# Next Task

**STATUS: EN-002 live PASS (2026-11-16).** Private bucket + service-role Storage path verified on Dev; document-service storage configured.

## Just completed

- `CONTINUE EN-002-STORAGE-LIVE` after full service-role JWT re-copy
- Dev identity + existing `student-documents` confirmed (no duplicate bucket)
- Live smoke **11/11 PASS** — put/get/list/delete + tenant-prefix isolation  
  Evidence: `docs/reports/STM06_EN002_STORAGE_LIVE_SMOKE_2026-11-16.json`
- document-service restart: `object storage configured bucket=student-documents`; health 200; stopped cleanly
- Secrets never printed

## Remaining gaps (EN-002 still partially completed)

- Orphan object delete API not implemented (reconciliation procedure in runbook)
- Live malware AV still open gap (D11)
- Frontend contentBase64 upload (1b) / Registrar mapping (1c)

## Pickup queue (order)

0. IAM Path C / R31 pilot — **DONE** (activation + Path C smoke).
1. **STM-06-EN-002** — **partially completed** (Phase B live PASS; orphan API + malware + frontend remain).
1b. **Frontend:** `contentBase64` upload — next candidate (storage path live).
1c. **Frontend:** Registrar role mapping — **BLOCKED** until product pickup.
2. **STM-CROSS-EN-010** e2e — **BLOCKED** (harness approval).
6. **STM-06-US-015** — **BLOCKED** (notification DDL).
7. **STM-CROSS-EN-014** / **STM-03-EN-007** — **BLOCKED**.

## Rules

- No push unless owner authorizes.
- No production access.
- Never print service-role keys.
