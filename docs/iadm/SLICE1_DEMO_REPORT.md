# Slice 1 Demo Report — Platform Operator Desk (PD-01…PD-06 / Journey J1)

**Date:** 2026-11-21 · **Owner:** Niranjan V.G.  
**Scope:** Make Platform Operator (Super Admin R01 / System Admin R02) institute work visible and real.  
**Plain language:** BFF = Next.js `/api/platform/...` routes that call the API Gateway (:8080). IADM-01 = Tenant Lifecycle service (:8099). IADM-03 = Identity & Access (:8101). Institution Admin (R03) = the institute’s own top admin (not College Admin).

## Defaults recorded (no stop)

| Decision | Default chosen | Where logged |
|---|---|---|
| Who creates login for staff an Institution Admin assigns | Invite by email through existing Auth link check (`plat.is_auth_linked_user`); appoint resolves email → profile only if Auth-linked | This report + FINDINGS |
| “Account Provisioning” nav | Leave in place; label **Student Account Provisioning** (Student Module) | SCREEN_INVENTORY + FINDINGS |

## Screens shipped (Slice 1)

| Story | Route / control | Evidence |
|---|---|---|
| IADM-PD-01 Dashboard | `/platform` — KPIs include pending lifecycle + pending R03 quorum (clickable) | CONFIRMED `platform/page.tsx`, `platform-dashboard-view.tsx` |
| IADM-PD-02 Institutes list | `/platform/institutes` — rows link to detail | CONFIRMED `institutes-registry-client.tsx` |
| IADM-PD-03 Institute detail | `/platform/institutes/[id]` — suspend / reactivate + reason | CONFIRMED detail page + BFF suspend/reactivate |
| IADM-PD-04 Onboard wizard | `/platform/institutes/new` — register via IADM-01 + appoint R03 (existing UUID or invite email) | CONFIRMED wizard + `POST /api/platform/institutes` + `.../appoint-admin` |
| IADM-PD-05 Lifecycle inbox | `/platform/lifecycle` — approve / reject PENDING | CONFIRMED page + BFF list/decide |
| IADM-PD-06 Two-person R03 appointment | `/platform/identity` — start + decide quorum (label = Institution Admin R03) | CONFIRMED Step 0 + wizard deep-link |

## Journey J1 checklist

| # | Steps | Expected | Result |
|---|---|---|---|
| J1-1 | R01 opens Onboard Institute → enter code/name/plan → appoint R03 (existing UUID or Auth-linked email) → Confirm | Institute registered; appears in registry | CONFIRMED path wired UI→BFF→IADM-01 `registerInstitute`; live smoke below |
| J1-2 | Appointment starts | Quorum PENDING; Identity desk shows row | CONFIRMED `appointInstitutionAdmin` inserts `plat.institution_admin_quorum_requests` |
| J1-3 | Second platform track (R02) approves on Identity | APPROVED; assignment applied by IADM-03 decide | API-ready from Wave E; screen-ready on Identity desk — **demo of second signer depends on seeded R02 persona** |
| J1-4 | New Institution Admin logs in | Sees desk / interim home | **Partial:** R03 desk is Slice 2 (ID-01…). Login possible after quorum approve if Auth-linked; Institution Admin desk not built yet |
| J1-deny | Student opens `/platform/identity` or `/platform/lifecycle` | Access denied | CONFIRMED page guards `role !== 'platform_admin'` → `/access-denied` |

### Live smoke (DEV)

Run after stack healthy (gateway :8080, iadm-01 :8099, iadm-03 :8101, Next :3000):

1. Browser: login as Platform Operator R01 (`platformadmin.campx@gmail.com` from `.env.audit`).
2. `/platform` — pending quorum / lifecycle counters load (soft-fail 0 if services down).
3. `/platform/institutes` → open a row → detail with Suspend/Reactivate.
4. `/platform/lifecycle` — inbox loads (may be empty if no PENDING requests).
5. `/platform/institutes/new` — register with unique code; appoint demo target `c03a0000-0000-4000-8000-000000000003` (EXISTING) or Auth-linked email.
6. `/platform/identity` — see PENDING quorum; second signer decides.
7. `/platform/ops` — search action containing appoint / `institution_admin` / `platform_admins` — mutation visible in `audit.change_log` (IADM-08).

**Tagging:** Path and code CONFIRMED. Full headed Playwright persona video for J1-3/J1-4 not recorded in this pass (STOP for owner pattern confirm first).

## Status honesty (PD stories)

| ID | API-ready | Screen-ready | Demo-verified |
|---|---|---|---|
| IADM-PD-01 | Yes | Yes (pending KPIs) | Partial — counters wired; owner visual confirm pending |
| IADM-PD-02 | Yes | Yes | Partial — list + links; owner confirm |
| IADM-PD-03 | Yes | Yes | Partial — UI + BFF; owner confirm suspend cycle |
| IADM-PD-04 | Yes | Yes | Partial — live register+appoint path; J1 full E2E after R02 approve |
| IADM-PD-05 | Yes | Yes | Partial — inbox UI; needs a PENDING request row for full demo |
| IADM-PD-06 | Yes | Yes | Partial — Identity desk + wizard; second-signer demo needs R02 |

## Files created / changed (Slice 1)

**Created**
- `frontend/src/app/(dashboard)/platform/institutes/[id]/page.tsx`
- `frontend/src/app/(dashboard)/platform/institutes/[id]/institute-detail-client.tsx`
- `frontend/src/app/(dashboard)/platform/lifecycle/page.tsx`
- `frontend/src/app/(dashboard)/platform/lifecycle/lifecycle-inbox-client.tsx`
- `frontend/src/app/api/platform/institutes/route.ts`
- `frontend/src/app/api/platform/institutes/[id]/appoint-admin/route.ts`
- `frontend/src/app/api/platform/institutes/[id]/route.ts` (prior)
- `frontend/src/app/api/platform/institutes/[id]/suspend/route.ts` (prior)
- `frontend/src/app/api/platform/institutes/[id]/reactivate/route.ts` (prior)
- `frontend/src/app/api/platform/lifecycle/route.ts` (prior)
- `frontend/src/app/api/platform/lifecycle/[id]/decide/route.ts` (prior)
- `docs/iadm/SLICE1_DEMO_REPORT.md`

**Changed**
- `frontend/src/lib/nav-config.ts` — Tenant Lifecycle nav
- `frontend/src/app/(dashboard)/platform/institutes/institutes-registry-client.tsx` — detail links
- `frontend/src/app/(dashboard)/platform/institutes/new/onboarding-wizard.tsx` — PD-04 R03 appoint
- `frontend/src/app/(dashboard)/platform/page.tsx` + `platform-dashboard-view.tsx` — pending KPIs
- `services/.../iadm-01-.../TenantLifecycleService.java` — email appoint + list lifecycle
- `services/.../iadm-01-.../Iadm01Controller.java` — appoint email body
- `docs/iadm/SCREEN_INVENTORY.md`, `DEMO_CHECKLIST.md`, `PROGRESS.md`, `NEXT_TASK.md`, `IADM_User_Stories.md` (as updated with this slice)

## STOP — owner checkpoint

**Please confirm the Slice 1 pattern** (desk + BFF + gateway + Java + audit, honest three-level status, J1 path). Reply:

- `APPROVED SLICE1` — continue Slice 2 (Institution Admin Desk J2/J3)
- `CHANGES SLICE1: <feedback>` — rework before Slice 2

Do **not** push. Local commit for Slice 1 is created after this report is written.
