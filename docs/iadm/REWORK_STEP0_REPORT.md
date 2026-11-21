# IADM Rework — Step 0 Report (Housekeeping & Truth)

**Date:** 2026-11-21 · **Owner approval:** Niranjan V.G. (DEV only)  
**Branch:** `feature/java-backend-integration` · **No push**

## Plain-language map

| Term | Meaning |
|---|---|
| R01 | Super Admin (Platform Operator) |
| R02 | System Admin (second platform track for two-person approval) |
| R03 | Institution Admin (runs one institute) |
| BFF | Next.js server routes under `/api/...` that call the Gateway |
| Gateway | API Gateway on port 8080 |
| IADM-03 / IADM-08 | Identity Access (:8101) / Audit Ops (:8106) Java services |
| RLS | Row Level Security in PostgreSQL |
| `audit.change_log` | Table filled by database trigger `audit.capture()` |
| `audit.events` | Separate application-event table (not the trigger target) |

## 0.1 Snapshot commits

| Commit | Contents |
|---|---|
| `ef3b93d` | Wave UI-1 Identity & Ops desks (local) |
| Prior Wave F | Already on branch (`0eb5883`, `0ee70f6`) — CONFIRMED via `git log` |

## 0.2 Screen inventory

See `docs/iadm/SCREEN_INVENTORY.md`.

## 0.3 Defects D1–D7

| ID | Problem | Cause | Fix | Evidence |
|---|---|---|---|---|
| D1 | Platform users = 0 | RLS on `iam.user_profiles` required `tenant_id = current_tenant_id()`; platform JWT has no tenant | Policy `user_profiles_select_platform` using `iam.is_platform_admin()`; analytics `count(DISTINCT up.id)` | CONFIRMED: dashboard payload `userCount=10` for DEV tenant |
| D2 | Service health empty | No writers to `plat.service_health` | Function `plat.upsert_service_heartbeat`; seed 10 services; IADM-08 `/ops/health` refreshes heartbeats | CONFIRMED: BFF health returns 10 HEALTHY rows |
| D3 | Audit search empty | Search read `audit.events`; triggers write `audit.change_log`; RLS also hid rows without tenant | Search `audit.change_log` + platform select policy | CONFIRMED: search `platform_admins` returns INSERT/UPDATE rows |
| D4 | Quorum text said COLLEGE_ADMIN | UI label wrong; API already APPOINT/REMOVE Institution Admin (R03) | Label/copy fix on Identity desk | CONFIRMED: code in `identity-access-client.tsx`; API `Iadm03DomainService` APPOINT/REMOVE |
| D5 | Lists with no start action | UI only listed | Start quorum + start break-glass forms + BFF create routes | CONFIRMED: panels `quorum-start-panel`, `bg-start-panel` |
| D6 | Unclear nav | Account Provisioning / Platform Audit confusing | Renamed to Student Account Provisioning / Platform Audit (legacy); full merge = Slice 4 PD-14 | CONFIRMED: `nav-config.ts` |
| D7 | False "completed" counts | API-only done | Re-baseline with API-ready / Screen-ready / Demo-verified (below) | CONFIRMED: this report + story header update |

## 0.4 Story re-baseline (honest)

Three levels only: **API-ready** | **Screen-ready** | **Demo-verified**.

| Area | API-ready | Screen-ready | Demo-verified |
|---|---|---|---|
| IADM-01…08 backend Waves A–F + US-008/009 | Most stories | Partial (PD desks only for 03/08 ops) | None except Step 0 defect demos |
| Platform Identity desk (quorum/break-glass) | Yes | Yes (list + start + decide) | Step 0 checklist partial |
| Platform Ops desk | Yes (after D2/D3) | Yes | Step 0 checklist partial |
| Platform dashboard users | Yes (after D1) | Yes | Step 0 D1 |
| Institution Admin desk (R03) | APIs exist | **No desk yet** | No |
| Contracts IADM-C-01…C-05 | Yes (Wave E0) | N/A (not admin work) | Contract-proven only |

System-actor stories reclassified as contracts (not admin work):
- **IADM-C-01** = former IADM-02-US-005 effective setting resolve  
- **IADM-C-02** = ceiling lookup consumed by CADM  
- **IADM-C-03** = IADM-04-US-006 entitlement check  
- **IADM-C-04** = cross-service audit search as a service call  
- **IADM-C-05** = auth-linked user check / suspend gate helpers  

Full cell-by-cell status remains in `docs/user-stories/IADM_User_Stories.md` with a Rework banner: treat prior "completed" as **API-ready** unless Demo-verified is recorded.

## 0.5 Demo data

| Migration | Purpose |
|---|---|
| `20261121120000_iadm_rework_step0_visibility.sql` | RLS policies, heartbeat function, seed heartbeats, pending quorum + break-glass (+ lifecycle attempt) |
| `20261121120000_rollback__iadm_rework_step0_visibility.sql` | Removes seed + policies + function |

Applied on DEV `azulbaxszsqxymqmlwfe` via Supabase MCP `apply_migration` name `iadm_rework_step0_visibility`. **No credentials in files.**

## Default decisions (recorded)

1. **Staff login creation:** invite by email through existing Auth-linked check (`plat.is_auth_linked_user`); no new tool.  
2. **Account Provisioning nav:** leave in place; label **Student Account Provisioning**; FINDING for later ownership.

## Files created/changed (Step 0)

- `database/migrations/20261121120000_iadm_rework_step0_visibility.sql` (+ rollback)
- `docs/iadm/SCREEN_INVENTORY.md`, `DEMO_CHECKLIST.md`, `REWORK_STEP0_REPORT.md`
- `services/.../iadm-08/.../Iadm08DomainService.java` (search + heartbeat)
- `services/.../analytics/.../PostgresAnalyticsDashboardRepository.java` (DISTINCT users)
- `frontend/.../identity/*`, `ops/*`, `nav-config.ts`, BFF create routes
- `docs/user-stories/IADM_User_Stories.md` (rework banner)
- `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`

## Next

**Slice 1** — Platform Operator Desk PD-01…PD-06 + Journey J1, then STOP for owner pattern confirmation.
