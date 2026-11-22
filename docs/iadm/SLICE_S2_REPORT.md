# IADM-01 Slice S2 Report — Colleges & Handover

**Date:** 2026-11-22  
**Owner authorization:** Niranjan V.G. — IADM-01 S0–S4 DEV only; `APPROVED S1` recorded same day  
**DEV project:** `azulbaxszsqxymqmlwfe`  
**Plain language:** Institution (or Platform support path) can register a college, invite first College Admin (R04) with fail+retry, hand over to CADM with INTERIM acks, update institute/college identity, and refuse post-handover college profile edits.

---

## Environment (CONFIRMED)

| Check | Result |
|---|---|
| IADM-01 `:8099` | `{"status":"UP","service":"IADM-01"}` |
| API Gateway `:8080` | UP (PATCH proxy fixed for JDK 8) |
| Next.js `:3000` | UP |
| Student create college | **403** |
| Unauthenticated gateway | protected by gateway auth |

---

## Story results

| Story | Expected | Result | Evidence |
|---|---|---|---|
| US-019 list colleges | List with status / handover | **PASS (API)** | `GET /api/platform/institutes/{id}/colleges` → **200** `count=3+`; helper `plat.iadm01_list_colleges` |
| US-006 register college | `ONBOARDING`; audit | **PASS** | `COL183036` → `a787519c-…` **201**; audit `IADM01_COLLEGE_CREATE` |
| US-020 view/update | Identity editable until handover | **PASS** | Pre-handover PATCH **200**; post-handover PATCH **409** `IADM01_HANDOVER_COMPLETE` |
| US-018 institute identity | R01/R03 patch name/contact/location | **PASS (API)** | PATCH institute **200** name=`S1 Fresh Institute (S2 profile)` |
| US-007 first R04 | Invite fail+retry → appoint | **PASS** | Fail **502** `IADM01_AUTH_INVITE_FAILED`; retry **200** steps SUCCEEDED; `APPOINT_R04` via `plat.assign_college_admin` |
| US-008 handover | INTERIM CADM acks; college ACTIVE | **PASS** | Handover **200** `status=COMPLETED`, `cadm01Interim/cadm02Interim=true`; college **ACTIVE** |
| US-009 R03 user-admin revoked | Post-handover authority removed flag | **PASS (API)** | Response `r03CollegeUserAdmin=REVOKED`; detail `handoverStatus=COMPLETED` |

### Demo college (primary)

| Field | Value |
|---|---|
| Institute | `61e9b962-caff-4158-9cc7-dfdd84139f11` (`S1F175540`) |
| College | `a787519c-0a4c-452d-9781-c90d9a5542c0` (`COL183036`) |
| R04 Auth user | `d679f4a0-daec-497b-8a60-d6b3a985f6c5` (`s2.r04.183036@campxsync.test`) |
| Handover | `f22969a7-a3e6-4e02-a8a9-e2a6962d9f52` COMPLETED |

### Audit (CONFIRMED SQL)

`IADM01_COLLEGE_CREATE`, `IADM01_R04_ONBOARDING_STARTED`, `IADM01_R04_APPOINTED` (duplicated once — FINDING-IADM01-AUD-03), `IADM01_CADM_HANDOVER`.

---

## Migrations this slice (DEV)

| File | Meaning |
|---|---|
| `20261122010500` / `10600` | College create/update/status SECURITY DEFINER helpers |
| `20261122010700_iadm01_college_read_helpers.sql` | `plat.iadm01_get_college` / `list_colleges` |
| `20261122010800_iadm01_assign_college_admin.sql` | `plat.assign_college_admin` (R04 under RLS) |

Matching `*_rollback__*.sql` files are in `database/migrations/`.

---

## Screens / BFF

| Path | Role |
|---|---|
| `/institution/colleges`, `/new`, `/[id]` | R03 primary desk |
| `/institution/profile` | US-018 |
| `/api/institution/colleges/**`, `/api/institution/profile` | R03 BFF |
| `/api/platform/institutes/[id]/colleges`, `/api/platform/colleges/[id]/**` | Platform support path used for headed-less E2E |

**Owner visual:** R03 JWT persona still via invite email (no test-session R03); screens exist.

---

## Automated checks

| Suite | Result |
|---|---|
| `TenantLifecycleRbacTest` | **PASS** (Maven exit 0) |
| `frontend` `npx tsc --noEmit` | **0 errors** |

---

## Gateway note

JDK 8 `HttpURLConnection` rejected `PATCH`. Gateway `ReverseProxyHandler` now forces PATCH via reflection so US-018/US-020 reach IADM-01.

---

## Findings opened/updated

- **FINDING-IADM01-AUD-03** — duplicate `IADM01_R04_APPOINTED` (same class as AUD-02).
- **FINDING-IADM01-SUM-01** — still OPEN (summary college count under platform JWT).
- **FINDING-IADM01-CADM-01** — INTERIM acks exercised in this demo.

---

## Stop / continue

Per owner task: **do not stop for S2 approval**. Proceed to Slice S3 (lifecycle).  
Local commit for S2 will be created; **no push**.
