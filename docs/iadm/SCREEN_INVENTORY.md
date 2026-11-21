# Platform Desk — Screen Inventory (Rework Step 0)

**Date:** 2026-11-21 · **Owner:** Niranjan V.G.  
**Plain language:** This lists every Platform Operator sidebar item (Super Admin R01 / System Admin R02 desk), what it shows, where data comes from, and whether the admin can only look or also act.

**Legend:** BFF = Next.js `/api/...` routes that call the API Gateway. Gateway = port 8080. IADM-0N = Java domain service.

| Nav label | Route | Data shown | Source endpoint | Actions | List-only? |
|---|---|---|---|---|---|
| Dashboard | `/platform` | Institutes by status, role templates, **total platform users**, recent prefs | Gateway `GET /api/v1/academics/analytics/dashboard?role=platform_admin` (ACD-10 analytics `:8092`); prefs via ADM-01 | Links: Onboard, Customize Dashboard | Mostly view; counters should open lists (Slice 1 PD-01) |
| Institutes & Tenancies | `/platform/institutes` | Searchable institute cards (name, code, status, college/user counts) | Same analytics dashboard institutes array | Open row (limited); Onboard CTA | Mostly list; detail/suspend = Slice 1 PD-03 |
| Onboard Institute | `/platform/institutes/new` | Multi-step wizard (name, code, plan, first admin) | BFF `POST /api/platform/onboard-tenant` → Gateway `/api/v1/admin/onboard-tenant` (monolith `:8081` / IADM-01 path) | Create (may be gated) | No — wizard exists; J1 quorum wiring = Slice 1 PD-04/06 |
| Identity & Access | `/platform/identity` | Pending Institution Admin (R03) quorum, break-glass requests/sessions | BFF `/api/platform/identity/**` → IADM-03 `:8101` | **Start** quorum / break-glass; approve/reject; close session | No (after Step 0 D5) |
| Ops & Audit Search | `/platform/ops` | Service health, audit self-check, audit search results | BFF `/api/platform/ops/**` → IADM-08 `:8106` | Search filters; refresh | Search/act on ops; retention UI later (Slice 4) |
| Student Account Provisioning | `/college/account-provisioning` | Student Module IAM link / provisioning desk | STM-07 student services via college BFF | Student link flows | **Not IADM** — left in place; labelled Student Module (default decision) |
| Platform Audit (legacy) | `/platform/audit` | Legacy audit list via `/api/v1/admin/audit-logs` | Gateway → monolith `:8081` with spoofable header pattern (deviation) | View | Duplicate of Ops search; merge planned PD-14 (Slice 4) |

**Redirect:** `/platform/iadm` → `/platform/identity` (status catalog removed).

## Tagging

| Claim | Tag |
|---|---|
| Nav items from `frontend/src/lib/nav-config.ts` | CONFIRMED |
| Identity/Ops BFF paths | CONFIRMED |
| Platform Audit still uses X-User-* headers on page | CONFIRMED (`platform/audit/page.tsx`) |
| Onboard wizard exists | CONFIRMED |
