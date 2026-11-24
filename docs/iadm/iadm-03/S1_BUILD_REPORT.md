# IADM-03 S1 Build Report (US-008…014)

**Date:** 2026-11-24  
**Environment:** DEV (`azulbaxszsqxymqmlwfe`)  
**Trigger:** Owner `APPROVED S0 COMMIT` → S1 screens  
**Service:** `:8101` `/api/v1/iadm/03`  
**Commit:** S0 already local `ffc44af`; **S1 not committed** (await `APPROVED S1` / `APPROVED S1 COMMIT`)

---

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-008 Role catalogue | **completed** | R01 desk `s1_r01_desk.png`; R03 desk `s1_r03_desk.png` (institute-filtered) |
| US-009 Permission catalogue + mappings | **completed** | R01 desk permissions table with `mappedRoles` |
| US-010 Access overview | **completed** | Platform + institute counters; pending-requests note (store not created — S3) |
| US-011 Subject access detail | **completed** | `s1_r01_detail.png` grants + store |
| US-012 Invite/create profile | **API + Screen** | Invite form on profiles desk; Auth Admin client wired; live invite not emailed in headed film (form present) |
| US-013 List/read profiles | **completed** | `s1_r01_profiles.png` real R03 rows; R03 desk scoped |
| US-014 Profile status | **completed** | API smoke inactive→active `S1_US014_STATUS_SMOKE.json` + audit `IADM03_PROFILE_STATUS` |

---

## Path

UI → BFF `/api/platform/identity/*` or `/api/institution/access/*` → Gateway `:8080` → IADM-03 `:8101` → PostgreSQL.

---

## Verification

| Check | Result |
|---|---|
| `mvn test -pl …/iadm-03-identity-access-service` | **0 failures** |
| `cd frontend; npx tsc --noEmit` | **0 errors** |
| Headed demo `node scripts/demo/iadm03_s1_headed.mjs` | **PASS** R01 + R03 |
| US-014 status smoke | **200/200**; profile restored ACTIVE |
| 56-cell matrix | **Not re-run this turn** — still required before claiming close-out |

---

## DEV fixes applied during S1

1. **MAC R03 Auth `app_metadata`:** set `tenant_id` + `role=INSTITUTION_ADMIN` (was missing → `GATEWAY_MALFORMED_JWT`).
2. **Platform RLS gap (S0 D2):** no `user_profiles` platform SELECT/UPDATE policies. Interim **pool-role read/write after permission check** for R01 cross-tenant profile ops; documented in API `rlsNote`. Owner-approved RLS migration still required.
3. **Audit binding:** `audit.log_event` `resource_id` must be UUID.

---

## Evidence paths

- `docs/iadm/iadm-03/evidence/s1/s1_r01_desk.png`
- `docs/iadm/iadm-03/evidence/s1/s1_r01_profiles.png`
- `docs/iadm/iadm-03/evidence/s1/s1_r01_detail.png`
- `docs/iadm/iadm-03/evidence/s1/s1_r03_desk.png`
- `docs/iadm/iadm-03/evidence/s1/S1_HEADED_RESULTS.json`
- `docs/iadm/iadm-03/evidence/s1/S1_US014_STATUS_SMOKE.json`

---

## STOP

Await owner: **`APPROVED S1`** or **`APPROVED S1 COMMIT`** (or `CHANGES S1: …`).  
Do **not** push. Optional next: owner-approved `iam.user_profiles` platform RLS policies; 56-cell re-run; S2 grants.
