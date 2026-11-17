# STM-07 batch 4 — headed browser demo guide

**Date:** 2026-11-17  
**Project:** `azulbaxszsqxymqmlwfe` (Dev/staging only)  
**Accounts:** only those in `.env.audit`. No invented credentials or Auth users.

## What this demo shows

| Spec | Plain name | ID |
|---|---|---|
| `01_en005_integration_flow.spec.mjs` | Connect account setup to student, admission, lifecycle and portal | STM-07-EN-005 |

### Plain steps in the browser

1. **Admission auto-login-setup** — recorded as stopped: automatic “request a new login” after admission handover is **not wired** (same gap as the integration matrix).
2. **Staff desk link status** — Platform Admin (R01) opens `/college/account-provisioning` and loads link status for the known Dev student `81dd67d3-1d5f-4d8e-8061-a67648610002` (enrollment `STU-DEV-2026-0001`).
3. **Student portal link status** — Student 1 opens `/student` and the account link-status strip (`student-account-link-status`) is shown.

## Services started for this demo

| Service | Port | Why |
|---|---|---|
| Frontend (Next.js) | 3000 | Already running via stack |
| API Gateway | 8080 | Already running |
| Institute Admin (IAM Path C) | 8081 | Already running |
| Student service (STM-01 / STM-07) | 8093 | Already running |
| Lifecycle service (STM-04) | 8096 | Already running |
| **Admissions service (STM-02)** | **8094** | **Started manually for batch 4** |
| **Enrollment service (STM-03)** | **8095** | **Started manually for batch 4** |
| **Guardian service (STM-05)** | **8097** | **Started manually for batch 4** |

## Known live-data / stopped-step notes

1. Dev currently has **zero** rows in `stu.student_iam_links`. This demo does **not** create a link or Auth user.
2. **Staff link-status load (Platform Admin R01):** BFF → gateway returned **403** with detail `Missing required X-Tenant-Id header for student domain operations`. Platform Admin’s session/JWT path does not supply a tenant id for student-domain calls. Stopped — needs an owner decision on how Platform Admin (R01) selects/supplies tenant context for this desk (no Auth inventing done here).
3. **Student portal strip:** the Account link status panel rendered, but no status value appeared (no resolved student profile / link payload for the session). Strip presence still proves the portal UI integration point.
4. **Admission auto-login-setup:** stopped — not wired in Connect account setup integration (STM-07-EN-005).

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\check_stack.ps1
# If admissions/enrollment/guardian are down, start them the same way as batch 4 (java Main on 8094/8095/8097).
node docs/reports/playwright-stm07-batch4/run_batch4_demo.mjs
```

`slowMo` defaults to **700 ms**. Browser holds ~4 s after the spec.
