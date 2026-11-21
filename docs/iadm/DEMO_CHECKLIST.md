# IADM Demo Checklist

**Date:** 2026-11-21 · **Owner:** Niranjan V.G.  
**Plain language:** Steps a real person follows to prove a story is Demo-verified (API + screen + data + audit).

Personas (no passwords in this file — use `.env.audit`):
- Platform Operator R01 — `platformadmin.campx@gmail.com`
- Second platform track R02 — when seeded for quorum
- Institution Admin R03 — `institution.admin.demo@campxsync.test` (when activated)
- Denied — Student R25 — `student1.campx@gmail.com`

## Step 0 regression (defects D1–D5)

| # | Steps | Expected | Result |
|---|---|---|---|
| S0-D1 | Log in as R01 → Dashboard | Total platform users > 0 (matches profiles under institutes) | CONFIRMED smoke: primary tenant `userCount=10` |
| S0-D2 | R01 → Ops & Audit Search | Service health lists IADM-01…08 + gateway | CONFIRMED BFF `/api/platform/ops/health` returns 10 HEALTHY rows |
| S0-D3 | R01 → Ops → search action `platform_admins` | Rows from `audit.change_log` for recent platform_admins changes | CONFIRMED search returns UPDATE/INSERT on `plat.platform_admins` |
| S0-D4 | R01 → Identity & Access | Quorum panel text says Institution Admin (R03), not College Admin | CONFIRMED label fix in UI |
| S0-D5 | R01 → Identity | Start quorum / start break-glass forms present; pending demo rows visible | CONFIRMED pending quorum + start panels |

## Slice 1 — Journey J1

| # | Steps | Expected | Result |
|---|---|---|---|
| J1-1 | R01 opens Onboard Institute → register + appoint R03 | Institute in registry; success panel with tenantId | CONFIRMED path UI→BFF→IADM-01; owner visual confirm pending — see SLICE1_DEMO_REPORT |
| J1-2 | R01 starts Institution Admin (R03) appointment | Quorum PENDING on Identity | CONFIRMED `appoint-admin` → `plat.institution_admin_quorum_requests` |
| J1-3 | R02 approves (different person) | APPROVED; assignment created | Screen-ready; second-signer persona demo Partial |
| J1-4 | New Institution Admin logs in | Sees Institution Admin desk (or interim home) | Partial — R03 desk = Slice 2 |
| J1-deny | Student opens `/platform/identity` or `/platform/lifecycle` | Access denied | CONFIRMED page role guard |

## Later slices

Placeholders for J2–J8 added when those slices ship.
