# IADM-03 S4 Build Report (US-032 / 034 / 035)

**Date:** 2026-11-24  
**Environment:** DEV (`azulbaxszsqxymqmlwfe`)  
**Trigger:** Owner `approved` + `APPROVE MIGRATION 20261124140000`  
**Service:** `:8101` `/api/v1/iadm/03`  
**Commit:** owner `APPROVED S4 COMMIT` (local)

---

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-032 Access review | **completed** | Review `179c146b-…` IN_PROGRESS; CERTIFY→KEEP; REQUEST_REVOCATION recorded; **no auto-revoke** |
| US-033 R28 auditor | **Blocked by D3** | Not in this slice |
| US-034 Notification vs audit | **partially completed** | `/notifications/contract` + review responses carry deferred note; live “notification service stopped” film deferred |
| US-035 R27 denials | **completed** | `/denials/r27` → 403; unit matrix; adm03 dormant |
| US-036/037 | **WAITS** | CADM-03 / STM-07 |
| US-038/039 | **PARKED** | — |
| US-040 close-out | **yet to pickup** | Full checklist / 56-cell |

---

## Migration

| Item | Status |
|---|---|
| `20261124140000_iadm03_access_review_rls.sql` | **Applied on DEV** — replaced `adm09.*` policies with `iam.review.manage` / platform |
| FORCE RLS | **Not** applied |

---

## Verification

| Check | Result |
|---|---|
| Maven tests | **0 failures** |
| `tsc --noEmit` | **0 errors** |
| `node scripts/demo/iadm03_s4_headed.mjs` | **PASS** |
| DB read-back | Review `179c146b-…`; AUDITOR **KEEP**; FACULTY **REVOKE** (request only) |
| 56-cell | **Not re-run** |

---

## Evidence

- `docs/iadm/iadm-03/evidence/s4/s4_r03_review_start.png`
- `docs/iadm/iadm-03/evidence/s4/s4_r03_review_certify.png`
- `docs/iadm/iadm-03/evidence/s4/s4_r03_review_revoke_req.png`
- `docs/iadm/iadm-03/evidence/s4/S4_HEADED_RESULTS.json`

---

## STOP

Owner **`APPROVED S4 COMMIT`**. Do **not** push unless owner says.
