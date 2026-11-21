# Platform Desk — Screen Inventory (Rework Step 0)

**Date:** 2026-11-21 · **Owner:** Niranjan V.G.  
**Plain language:** This lists every Platform Operator sidebar item (Super Admin R01 / System Admin R02 desk), what it shows, where data comes from, and whether the admin can only look or also act.

**Legend:** BFF = Next.js `/api/...` routes that call the API Gateway. Gateway = port 8080. IADM-0N = Java domain service.

| Nav label | Route | Data shown | Source endpoint | Actions | List-only? |
|---|---|---|---|---|---|
| Dashboard | `/platform` | Institutes by status, role templates, **total platform users**, **pending lifecycle**, **pending R03 quorum** | Analytics dashboard + IADM-01 lifecycle list + IADM-03 quorum list | Links: Onboard, Lifecycle inbox, Identity, Customize | No — counters open lists (Slice 1 PD-01) |
| Institutes & Tenancies | `/platform/institutes` | Searchable institute rows (name, code, status, college/user counts) | Analytics dashboard institutes array | Row → detail; Onboard CTA | No — opens PD-03 detail |
| Onboard Institute | `/platform/institutes/new` | Wizard: details → plan/college → appoint Institution Admin (R03) existing UUID or invite email → review | BFF `POST /api/platform/institutes` + `.../appoint-admin` → IADM-01 `:8099` | Register + start R03 quorum | No (Slice 1 PD-04) |
| Tenant Lifecycle | `/platform/lifecycle` | Pending suspend/decommission/export requests | BFF `/api/platform/lifecycle` → IADM-01 lifecycle-requests | Approve / Reject | No (Slice 1 PD-05) |
| Identity & Access | `/platform/identity` | Pending Institution Admin (R03) quorum, break-glass requests/sessions | BFF `/api/platform/identity/**` → IADM-03 `:8101` | **Start** quorum / break-glass; approve/reject; close session | No (PD-06) |
| Ops & Audit Search | `/platform/ops` | Service health, audit self-check, audit search results | BFF `/api/platform/ops/**` → IADM-08 `:8106` | Search filters; refresh | Search/act on ops; retention UI later (Slice 4) |
| Student Account Provisioning | `/college/account-provisioning` | Student Module IAM link / provisioning desk | STM-07 student services via college BFF | Student link flows | **Not IADM** — left in place; labelled Student Module (default decision) |
| Platform Audit (legacy) | `/platform/audit` | Legacy audit list via `/api/v1/admin/audit-logs` | Gateway → monolith `:8081` with spoofable header pattern (deviation) | View | Duplicate of Ops search; merge planned PD-14 (Slice 4) |

**Institute detail (not a nav root):** `/platform/institutes/[id]` — profile + Suspend/Reactivate (reason required) + link to appoint R03 (Slice 1 PD-03).

**Redirect:** `/platform/iadm` → `/platform/identity` (status catalog removed).

## Tagging

| Claim | Tag |
|---|---|
| Nav items from `frontend/src/lib/nav-config.ts` | CONFIRMED |
| Identity/Ops BFF paths | CONFIRMED |
| Platform Audit still uses X-User-* headers on page | CONFIRMED (`platform/audit/page.tsx`) |
| Onboard wizard exists | CONFIRMED |
