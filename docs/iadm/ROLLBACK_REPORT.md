# IADM Rollback Report (DEV only)

**Date:** 2026-11-22  
**Owner approval:** Niranjan V.G. — recorded 2026-11-21 for this rollback and nothing beyond it.  
**DEV project:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev). Production not touched.  
**Branch:** `feature/java-backend-integration` (no push).

## Verdict

Code, gateway routing, and DEV database objects for IADM Waves A–F / UI-1 / Slice 1 / demo catalog are rolled back to the **RBAC Phase 4** state. RBAC Phases 0–4 stay. Documentation under `docs/iadm/` is retained. Security keep-list items remain.

---

## Method (code)

**CONFIRMED — path-by-path restore from `0353f6e` (RBAC Phase 4 verification and patch), not sequential `git revert`.**

Reason: fourteen IADM commits touch shared gateway/stack/frontend files; sequential revert risked unmanageable conflicts. IADM commits remain reachable via backup tag/branch (no history rewrite).

---

## Step 1 — Backup (CONFIRMED)

| Artifact | Meaning |
|---|---|
| Commit `dc4063f` | Snapshot before IADM rollback (untracked RBAC audit docs + scaffold scripts) |
| Tag `backup-iadm-before-rollback` | Points at `dc4063f` |
| Branch `backup/iadm-before-rollback` | Same tip as the tag |
| `docs/iadm/rollback/` | Applied-migration list, row counts, sanitized export meta, migration copies, K2 patches, tenant list |

### Commit inventory (CONFIRMED via `git log 0353f6e..HEAD`)

| Hash | Plain meaning | Action |
|---|---|---|
| `0353f6e` | RBAC Phase 4 — last pre-IADM commit | **Restore target / KEEP** |
| `24ec0b1` | Step 0 preflight + Wave A (IADM-01/02) | Code removed |
| `7e93751` | Waves B–D microservices + CADM handoff | Code removed |
| `ffc4a18` | campx-logger / safe exceptions / §3.2 docs (K3) | Docs kept; IADM module files gone with modules |
| `27ffae6` | Wave E plan docs | Docs kept under `docs/iadm/` |
| `1fea909` | Wave E0 CADM contract proof | Code removed |
| `3cf3aa6` | Wave E parity + stack cutover | Code removed |
| `0eb5883` / `0ee70f6` | Wave F PARTIAL close | Code removed |
| `128833a` | Two-person Institution Admin quorum (IADM-03-US-009) | Code removed |
| `41f46be` | Break-glass tenant recovery (IADM-03-US-008) | Code removed |
| `64b82ad` | Platform demo desk catalog | Code removed |
| `ef3b93d` | Wave UI-1 Identity + Ops desks | Code removed |
| `0c3b666` | Rework Step 0 visibility / demo seed | Code removed |
| `9a91d64` | Slice 1 Platform Operator desk | Code removed |
| `dc4063f` | Snapshot before rollback | Kept |

**Parent of `24ec0b1` = `0353f6e` (CONFIRMED).** No non-IADM commits between Phase 4 and first IADM commit.

---

## Step 2 — Code rollback commits (CONFIRMED)

| Commit | Plain meaning |
|---|---|
| `fac65de` | Revert IADM Waves A–F, Slice 1 (see docs/iadm/rollback/) |
| `ec6057c` | security: keep context-leak fixes (K2) |

### Removed

- Modules `iadm-01` … `iadm-08` (ports 8099–8106) and Maven reactor entries  
- Gateway `/api/v1/iadm/01–08` routes  
- IADM BFF routes and Platform desks (Identity, Ops, lifecycle inbox, institute detail, demo catalog, onboarding/KPI IADM additions)  
- IADM entries in start/check/stop/restart scripts  
- IADM migration files `20261121000200` … `20261121120000` from `database/migrations/` (copies retained under `docs/iadm/rollback/migrations/`)

### Keep list

| ID | What | Why |
|---|---|---|
| **K1** | Migration `20261121000100` + `plat.assign_institution_admin` using `auth.uid()` | Before fix, College Admin could appoint Institution Admin of any tenant (confirmed exploit). Rollback of this migration **not run**. |
| **K2** | Context-leak finally-block cleanups in Subject / Analytics / Curriculum / InstituteAdmin / CollegeAdmin / Course controllers | Re-applied in `ec6057c` after restore. |
| **K3** | `ffc4a18` engineering standards | Docs kept; IADM-only Java files disappeared with modules (expected). |
| **K4** | All `docs/iadm/`, user-stories (incl. rework DRAFT), audits, RBAC matrix | Documentation retained. |
| **K5** *(added)* | `frontend/src/lib/auth/jwt-verifier.ts` base64- session decode | Security/compat fix from Wave UI-1; not IADM functionality. |
| **K6** *(added)* | Analytics `count(DISTINCT up.id)` | Data-integrity fix; not IADM functionality. |

---

## Step 3 — Database rollback (DEV, CONFIRMED)

Applied IADM migrations (MCP timestamps) rolled back via repo rollback scripts in **reverse order**, skipping K1:

1. `20261121120000` Rework Step 0 visibility  
2. `20261121001300` break-glass  
3. `20261121001200` two-person quorum  
4. `20261121001100` Wave F pending close  
5. `20261121001000` Wave F audit retention RLS  
6. `20261121000900` Wave F partial close  
7. `20261121000800` Wave E handover/suspend  
8. `20261121000700` auth-linked user check  
9. `20261121000600` Wave C/D role grants  
10. `20261121000500` Wave D billing/analytics keys  
11. `20261121000400` Wave C privacy/security tables  
12. `20261121000300` Wave B identity/audit  
13. `20261121000200` Wave A lifecycle/config  

**Skipped:** `20261121000100` / applied `20261121074939` (K1).

### After rollback

- All 20 IADM-created tables listed in `table_row_counts.csv`: **gone** (CONFIRMED).  
- `iadm*` permission keys: **0**; role grants: **0** (CONFIRMED).  
- K1 function body still uses `v_caller := auth.uid()` and ignores spoofed `p_assigned_by` (CONFIRMED via `pg_get_functiondef`).  
- K1 negative test: call without `auth.uid()` → Access Denied / missing authenticated caller (CONFIRMED).  
- `verify_phase1_rbac.sql`: **PASS** (CONFIRMED).  
- Phase 2 core assertions: **PASS**; verbatim assertion 8 fails because Phase 4 re-added `INSTITUTION_ADMIN → STUDENT/PARENT` (count=2) — **expected Phase 4 supersession**, not IADM damage (CONFIRMED).  
- `verify_phase3` + `verify_phase4`: **PASS** (CONFIRMED).  
- RBAC counts: **R04 (College Admin) = 135 keys**, **R03 (Institution Admin) = 54 keys**, **TENANT_ADMIN templates = 0**, **wildcard `*` held by 0 roles** (CONFIRMED).

`supabase_migrations.schema_migrations` rows for IADM versions were **not deleted** (not part of rollback scripts; no improvised destructive SQL). Objects are gone; version rows remain as apply history.

### Test institutes retained in `core.tenants` (list only — not deleted)

See `docs/iadm/rollback/iadm_test_tenants_since_2026-11-21.csv` (19 rows since 2026-11-21, including Wave E probes and `BEC` / Brunda Engineering College).

---

## Step 4 — Verify

### Tests after rollback (CONFIRMED unless noted)

| Suite | Result | vs Phase 4 target |
|---|---|---|
| Frontend `npx tsc --noEmit` | **0 errors** (after clearing stale `.next` types) | Match |
| institute-admin-service | **510 run, 0 fail, 0 err, 33 skip** | Match |
| attendance-service | **38 run, 0 fail** | Match |
| college-admin-service | **148 run, 0 fail, 31 skip** | Match |
| api-gateway | **181 run, 1 fail, 2 skip** | Diff: `ApiGatewayRoutingTest.testRoutesEndpoint` — response contained substring `8088` while attendance listens on 8088 during stack; **INFERRED** environmental/pre-existing, not IADM-route related (GatewayConfig has no IADM routes) |
| Other services “141” | **NOT re-run in full this session** | Phase 4 named academic suites not re-batched; primary Phase 4 suites above done |

### Stack smoke (CONFIRMED)

| Check | Result |
|---|---|
| Gateway `:8080` actuator health | **200** |
| Institute Admin `:8081` | **LISTEN**; health returns **401** (HMAC expected) |
| College Admin `:8082` | **Not started by pre-IADM `start_stack.ps1`** (script has no 8082 entry — CONFIRMED). `restart_service.ps1` also lacks college-admin. |
| Ports 8099–8106 | **All free** — nothing listens |
| `/api/v1/iadm/*` via gateway | **404** (not routed) |
| Platform UI `/platform`, `/platform/institutes`, `/platform/institutes/new` | **200** |
| Nav | Institutes & Tenancies + Onboard Institute; no Identity/Ops/IADM (CONFIRMED in `nav-config.ts`) |
| `/platform/identity`, `/platform/iadm` | Source pages deleted; Next still returns app shell with **404** marker in HTML |

RAM warning during start: free RAM ~0.23 GB (below 3 GB gate) — some STM ports did not come up; core gateway + institute-admin + frontend did.

---

## How to restore IADM later

1. `git checkout backup-iadm-before-rollback` (or tag `backup-iadm-before-rollback`).  
2. Re-apply SQL from `docs/iadm/rollback/migrations/` (or files on that backup tip) on **DEV only**, in forward order.  
3. Next IADM attempt must follow `docs/user-stories/IADM_Rework_Desks_Journeys_and_Stories_v2_DRAFT.md` (desks, demo data, stricter DoD). **CADM waits.** Do not start IADM/CADM implementation from this rollback tip without a new owner task.

---

## Evidence paths

- `docs/iadm/rollback/applied_migrations.csv`  
- `docs/iadm/rollback/table_row_counts.csv`  
- `docs/iadm/rollback/iadm_test_tenants_since_2026-11-21.csv`  
- `docs/iadm/rollback/migrations/`  
- `docs/iadm/rollback/keep-patches/`  
- `docs/iadm/rollback/test-results/`  
- `docs/iadm/rollback/before_test_baseline.md`  
- `docs/iadm/rollback/stack_start.txt`
