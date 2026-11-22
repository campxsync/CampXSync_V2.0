# IADM-01 Slice S1 Demo Report

**Date:** 2026-11-22  
**Owner approval gate:** STOP for `APPROVED S1` or `CHANGES S1: <feedback>`  
**Owner authorization:** Niranjan V.G. — IADM-01 S0–S4 DEV only (2026-11-22)  
**DEV project:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)  
**Plain language:** Prove Platform Operator can register an institute, invite the first Institution Admin (R03), recover from a failed invite, and see durable audit — without creating a college in the Platform wizard.

---

## Environment (CONFIRMED)

| Check | Result |
|---|---|
| Free RAM during session | ~3.6 GB |
| IADM-01 `:8099` health | `{"status":"UP","service":"IADM-01"}` |
| API Gateway `:8080` health | UP |
| Next.js `:3000` | HTTP 200 |
| Unauthenticated `GET /api/v1/iadm/01/institutes` | **401** |
| College Admin (R04) BFF list | **403** (`DENY_R04`) |

Services started: IADM-01, API Gateway, frontend only (memory-conscious).

---

## Checklist results

| Step | Persona | Expected | Result | Evidence |
|---|---|---|---|---|
| Setup — health + deny | Platform / unauth / R04 | Health UP; unauth 401; R04 forbidden | **PASS** | Local smoke 2026-11-22 |
| TECH-005 seed | Platform | Demo institute visible | **PASS** | `INST-DEMO-001` / `COL-DEMO-001` in DEV [SQL] |
| US-016 list | R01 (Platform Operator) | List from IADM-01; unavailable on failure | **PASS (API)** / **PARTIAL (owner visual)** | BFF `GET /api/platform/institutes` returned DEMO + live rows |
| US-024 summary | R01 | Counters from DB | **PARTIAL** | Summary returns institute-by-status; `colleges=0` is FINDING-IADM01-SUM-01 |
| US-001 register | R01 | Institute `ONBOARDING`; audit | **PASS** | `S1F175540` → `61e9b962-caff-4158-9cc7-dfdd84139f11`; audit `IADM01_INSTITUTE_CREATE` |
| US-002 fail invite | R01 | Plain retry message; step FAILED | **PASS** | HTTP **502** `IADM01_AUTH_INVITE_FAILED` — “Invitation could not be sent. Retry invitation.”; step `INVITE_LOGIN=FAILED` |
| US-002/003/004/013 retry | R01 | Resume invite → profile → appoint; no re-register | **PASS** | Retry HTTP 200 `complete=true`; steps all SUCCEEDED; institute **ACTIVE** [SQL] |
| US-014 notification note | R01 | Non-blocking delayed note | **PASS** | Response includes `notificationMessage`: “Action completed; notification delivery is delayed.” |
| US-015 R04 denied | College Admin (R04) | Forbidden on institute list | **PASS** | BFF **403** |
| US-005 R03 home | New Institution Admin | Login → `/institution` home | **PARTIAL** | Screen + BFF `/api/institution/home` exist; invite user created (`s1.r03f.175540@campxsync.test`). **Owner must open invite email / set password and confirm landing.** Test-session has no R03 JWT persona. |
| US-017 detail tabs | R01 | Profile / Colleges / Administrators / Audit | **PARTIAL** | Tabs implemented; Administrators loads onboarding steps via BFF |
| Audit trail findable | R01 | Actions in `audit.events` | **PASS (DB)** / **PARTIAL (Ops UI)** | Actions present in `audit.events` [SQL below]. Ops search still in-memory (FINDING-IADM01-AUD-01). |

### Audit rows for institute `61e9b962-…` (CONFIRMED SQL)

| action | status |
|---|---|
| `IADM01_INSTITUTE_CREATE` | SUCCESS |
| `IADM01_R03_ONBOARDING_STARTED` | SUCCESS |
| `IADM01_R03_PROFILE_CREATED` | SUCCESS |
| `IADM01_R03_APPOINTED` | SUCCESS (duplicated once — FINDING-IADM01-AUD-02) |

### Onboarding steps after retry (CONFIRMED)

`INVITE_LOGIN` / `CREATE_PROFILE` / `APPOINT_R03` → all **SUCCEEDED**; institute status **ACTIVE**, `onboarded_at` set.

---

## Migrations applied this slice (DEV)

| Version / file | Plain meaning |
|---|---|
| `20261122010000_iadm01_lifecycle_foundation.sql` | Onboarding steps, feature flags, lifecycle tables, Auth-link helper |
| `20261122010100_iadm01_demo_seed_s1.sql` | Idempotent `INST-DEMO-001` / `COL-DEMO-001` |
| `20261122010200_iadm01_lifecycle_status_enums.sql` | Allow `ONBOARDING` / college `PENDING_HANDOVER` |
| `20261122010300_iadm01_grant_audit_log_event.sql` | `GRANT EXECUTE` on `audit.log_event` to `authenticated` |
| `20261122010400_iadm01_create_first_admin_profile.sql` | Platform-only SECURITY DEFINER profile create (US-003) |

Each has a matching `*_rollback__*.sql` beside it under `database/migrations/`.

---

## Automated tests

| Suite | Result |
|---|---|
| `TenantLifecycleRbacTest` | **7 run, 0 fail** (CONFIRMED Maven) |
| Frontend `npx tsc --noEmit` | **0 errors** |

---

## CSV status summary (S1)

See `docs/user-stories/IADM-01_User_Stories.csv`. Allowed values used: `DONE - <evidence>`, `PARTIAL - <reason>`, `NOT DONE`.

Honest gate: **API + BFF journey with fail/retry + durable audit = demonstrated**. **Owner visual walk of the wizard and first R03 invite login remains the confirmation this STOP requests.**

---

## Local commits (no push)

- `90cafba` — feat(iadm01): S0 foundation and S1 onboarding path (DEV)  
- `f7ab6d8` — feat(iadm01): wire gateway, UI journey, and DEV migrations  
- (plus this report / polish commit after S1 gate docs)

---

## STOP

Awaiting owner reply:

- `APPROVED S1` — proceed to Slice S2 (colleges and handover) without further stop until S2/S3/S4 reports  
- `CHANGES S1: <feedback>` — rework, re-demo, then stop again  
