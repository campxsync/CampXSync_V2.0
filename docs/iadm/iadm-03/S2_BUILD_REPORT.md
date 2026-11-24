# IADM-03 S2 Build Report (US-015…022)

**Date:** 2026-11-24  
**Environment:** DEV (`azulbaxszsqxymqmlwfe`)  
**Trigger:** Owner `approved proceed to next slice` after `APPROVED S1 COMMIT`  
**Service:** `:8101` `/api/v1/iadm/03`  
**Commit:** **`475480c`** — owner `APPROVED S2 COMMIT` (local only; no push)

---

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-015 Appoint R03 | **API + Screen** | Appoint form on profiles desk (`s2_r01_appoint.png`); `POST /grants/appoint-r03` idempotent profile→grant |
| US-016 Assign eligible role | **completed** | R03 granted FACULTY to `69822238-…`; DB assignment `0b690be3-…`; `s2_r03_assign.png` |
| US-017 Ceiling denials | **completed** | Unit `GrantorCeilingDenialTest`; UI preview + live R03→R03 deny (`s2_r01_ceiling.png`, `s2_r03_deny.png`) |
| US-018 R02 technical | **partially completed** | API path via `plat.platform_admins` / `SYSTEM_ADMIN`; no headed R02 persona film this turn |
| US-019 Revoke | **API + Screen** | Revoke controls on detail desks; R03 removal queues US-020 (not direct) |
| US-020 R03 grant change decide | **API + Screen** | Queue + decide form; appoint two-person remains **PARKED** |
| US-021 Claims contract | **API-ready + Demo** | `GET /claims/contract` — authoritative DB checks; gateway cache ≤300s; `iadm03OnRequestPath=false` |
| US-022 Idempotency | **API** | `iam.governance_idempotency` on appoint/assign; replay flag |

---

## Path

UI → BFF `/api/platform/identity/grants/*` or `/api/institution/access/grants/*` → Gateway `:8080` → IADM-03 `:8101` → PostgreSQL.

---

## Verification

| Check | Result |
|---|---|
| `mvn test -pl …/iadm-03-identity-access-service` | **0 failures** |
| `cd frontend; npx tsc --noEmit` | **0 errors** |
| `node scripts/demo/iadm03_s2_grant_smoke.mjs` | **PASS** ceiling + claims |
| `node scripts/demo/iadm03_s2_headed.mjs` | **PASS** R01 + R03 |
| DB read-back | FACULTY assignment `0b690be3-4c43-404b-88f4-7fcc8a290165` for subject `69822238-…` granted by Maharani R03 |
| 56-cell matrix | **Not re-run** — still required before close-out |

---

## Notes

1. **R03 permission-subset:** flat catalogue count blocked legitimate FACULTY grants; for R03, GrantorCeiling §21.1 is authoritative (documented interim).
2. **US-020:** removing INSTITUTION_ADMIN always queues independent approval; two-person **appoint** stays PARKED.
3. **US-021:** claims note returned on grant/revoke; full “stop IADM-03” consumer film not re-run this slice.
4. Ceiling evaluate + claims contract are HMAC-free pure reads on `:8101` for smoke; mutations still require gateway HMAC.

---

## Evidence paths

- `docs/iadm/iadm-03/evidence/s2/s2_r01_claims.png`
- `docs/iadm/iadm-03/evidence/s2/s2_r01_appoint.png`
- `docs/iadm/iadm-03/evidence/s2/s2_r01_ceiling.png`
- `docs/iadm/iadm-03/evidence/s2/s2_r03_assign.png`
- `docs/iadm/iadm-03/evidence/s2/s2_r03_deny.png`
- `docs/iadm/iadm-03/evidence/s2/S2_HEADED_RESULTS.json`
- `docs/iadm/iadm-03/evidence/s2/S2_API_SMOKE.json`

---

## STOP

**`APPROVED S2 COMMIT` recorded.** Do **not** push unless owner says. Next optional: R02 technical headed film; 56-cell; platform RLS; S3 when authorized.
