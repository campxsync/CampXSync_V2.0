# IADM-03 S3 Build Report (US-023…031)

**Date:** 2026-11-24  
**Environment:** DEV (`azulbaxszsqxymqmlwfe`)  
**Trigger:** Owner `proceed` + `APPROVE MIGRATION 20261124120000`  
**Service:** `:8101` `/api/v1/iadm/03`  
**Commit:** **not committed** — await `APPROVED S3` / `APPROVED S3 COMMIT`

---

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-023 R04 request-only | **API + Screen** | Submit path + matrix R04=`iam.request.submit` only; live film used R03 (no R04 password in harness) |
| US-024 R03 request | **completed** | Submit `9c0643f1-…` HOD; no grant on submit (`s3_r03_request_submit.png`) |
| US-025 Decide | **partially completed** | Self-approve denied; R01 **REJECT** filmed (`s3_r01_decide.png`). R01 **APPROVE→apply** blocked by existing `iam.guard_role_assignment` (needs institute-admin JWT) — peer R03 approve path remains |
| US-026 Create delegation | **completed** | Delegation `74c708fc-…` max **90d** from IADM-02; DB guard requires holding the role (`INSTITUTION_ADMIN`) |
| US-027 Revoke | **API + Screen** | Revoke controls on desk; not filmed this turn |
| US-028 Active-check | **API-ready** | Path C `:8081` reused; contract note on create response |
| US-029 90-day default | **completed** | `Iadm02DelegationDefaultsClient` → `iam.delegation.max_validity_days`; fail closed if IADM-02 down |
| US-030 Catalogue change | **yet to pickup** | Not in this slice capacity |
| US-031 Governance read | **yet to pickup** | Blocked/deferred with D5 |

---

## Migration

| Item | Status |
|---|---|
| `20261124120000_iadm03_access_requests.sql` | **Applied on DEV** after owner `APPROVE MIGRATION 20261124120000` |
| FORCE RLS | **Not** applied (separate approval) |

---

## Verification

| Check | Result |
|---|---|
| Maven tests | **0 failures** |
| `tsc --noEmit` | **0 errors** |
| `node scripts/demo/iadm03_s3_headed.mjs` | **PASS** |
| DB read-back | Request `9c0643f1-…` **REJECTED**; delegation `74c708fc-…` **ACTIVE** |
| 56-cell | **Not re-run** |

---

## Evidence

- `docs/iadm/iadm-03/evidence/s3/s3_r03_request_submit.png`
- `docs/iadm/iadm-03/evidence/s3/s3_r03_self_approve_deny.png`
- `docs/iadm/iadm-03/evidence/s3/s3_r03_delegation.png`
- `docs/iadm/iadm-03/evidence/s3/s3_r01_decide.png`
- `docs/iadm/iadm-03/evidence/s3/S3_HEADED_RESULTS.json`

---

## STOP

Await owner: **`APPROVED S3`** or **`APPROVED S3 COMMIT`** (or `CHANGES S3: …`).  
Do **not** push.
