# STM-07 batch 4 — headed browser demo guide

**Date:** 2026-11-17  
**Project:** `azulbaxszsqxymqmlwfe` (Dev/staging only)  
**Accounts:** only those in `.env.audit`. No invented credentials.

## What this demo shows

| Spec | Plain name | ID / case |
|---|---|---|
| `01_en005_integration_flow.spec.mjs` | Connect account setup to student, admission, lifecycle and portal | STM-07-EN-005 |
| `02_registrar_delegated_success.spec.mjs` | Delegated pilot Registrar succeeds on staff screen | Registrar (R31) + STM-07.ACCOUNT.A |
| `03_registrar_without_delegation_deny.spec.mjs` | No-delegation browser deny | **not run in browser** — see below |

## Platform Admin (R01) on staff desks

**Platform Admin cannot use the staff desks until a separate story is approved.**  
There is no college/tenant selector for Platform Admin. Staff desks are for roles tied to one college — Institution Admin (R03) and the delegated Registrar (R31).

### Which existing test accounts hold System Admin (R02) or Institution Admin (R03)?

| Role | Finding |
|---|---|
| System Admin (R02) | **No** existing test account in `.env.audit`, and **no** `SYSTEM_ADMIN` row in `plat.platform_admins` or `iam.role_assignments` on Dev. |
| Institution Admin (R03) | **No** existing test account in `.env.audit`, and **no** active `INSTITUTION_ADMIN` assignment linked to an Auth email on Dev. |
| Present in `.env.audit` | Platform Admin (R01) `platformadmin.campx@gmail.com`, College Admin `campxsync@gmail.com`, Faculty, Student 1/2, Registrar pilot. |

## Registrar demo re-run (after gateway recognises Registrar)

Gateway change: commit `dffbf19` — Registrar (R31) recognised in identity resolution only; no new route grants.

| Spec | Result |
|---|---|
| Delegated Registrar success | **FAIL** — desk opens as Registrar; gateway accepts the JWT and forwards to student-service. Load link-status returns **404 Student not found** for live Dev student `81dd67d3-1d5f-4d8e-8061-a67648610002` (row exists). Same call as College Admin reaches Java and returns **403 Role is not authorised** (row visible; staff ACCOUNT.A path denies College Admin). Registrar path is blocked by student-row visibility under the authenticated DB role before the Java ACCOUNT.A check runs. Screenshot: `screenshots/02_registrar_delegated_success.png`. Report: `REGISTRAR_DEMO_RUN_REPORT.json`. |
| No-delegation Java denial in browser | **Not run** — College Admin does **not** use the same STM-07.ACCOUNT.A check (denied as unauthorised role). Pilot M1 not revoked. Rely on `Stm07Us003StaffLinkStatusTest` service negatives. |

```powershell
# Success case only was re-run after the gateway change
node --input-type=module  # imports 02_registrar_delegated_success.spec.mjs only
# Full registrar pair:
node docs/reports/playwright-stm07-batch4/run_registrar_demo.mjs
```

## EN-005 flow notes

1. Admission auto-login-setup — stopped (not wired).
2. Staff desk / student portal strips — see batch-4 EN-005 report.
3. Live Dev currently may have zero `stu.student_iam_links` rows; demo does not invent links.

```powershell
node docs/reports/playwright-stm07-batch4/run_batch4_demo.mjs
```

`slowMo` defaults to **700 ms**.
