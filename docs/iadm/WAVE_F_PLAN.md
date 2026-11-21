# IADM Wave F Plan — Close non-deferred PARTIAL/NEW stories

**Date:** 2026-11-21  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Sources:** `IADM_User_Stories.md`, `WAVE_E_REPORT.md`, split services `:8100`–`:8106`, migrations `20261121000200`…`00800`, live DEV catalog  
**Skip:** IADM-01 (completed); DEFERRED IADM-03-US-008/009, IADM-05-US-007  
**Goal:** Bring every non-deferred story to honest `completed` (or explicit blocker in `WAVE_F_REPORT.md`), then CADM.

---

## Phase sequence (from NEXT_TASK)

| Phase | Cluster | Focus |
|---|---|---|
| **F0** | Inventory | This document |
| **F1** | IADM-02 | Catalogue depth + `cfg.setting_value_versions` + real rollback |
| **F2** | IADM-03 + IADM-08 | Permissions on roles; grant-table assign; auditor; audit retention |
| **F3** | IADM-04 + IADM-06 | Plan-change table; billing mutations on split; `college_id`; metering |
| **F4** | IADM-05 + IADM-07 | Notices/classifications; DSR depth; platform minimums; college posture |
| **F5** | Docs + suites | Status cells; Maven green; `WAVE_F_REPORT.md`; STOP |

---

## DB object inventory (live DEV 2026-11-21)

| Object | Status | Notes |
|---|---|---|
| `cfg.setting_value_versions` | **MISSING** | Required for soft rollback (IADM-02-US-007). History today = `cfg.v_setting_history` → `audit.change_log` |
| `cfg.v_setting_history` | **EXISTS** (view) | Scripts_V3.0 `70_views.sql`; used by `:8100` history API |
| `plat.plan_change_requests` | **MISSING** | Causes `501 IADM04_PLAN_CHANGE_UNAVAILABLE` |
| `plat.platform_security_minimums` | **MISSING** | `:8105` returns empty + `partial:true` |
| `plat.audit_retention_policy` | **MISSING** | `:8106` returns defaults `{days:365,mutable:false}` |
| `plat.usage_metrics.college_id` | **MISSING** | Columns: `id,tenant_id,metric_name,dimension,metric_value,recorded_on,…` — no `college_id` |
| `plat.subscription_plans` | **EXISTS** | Split service: read-only; mutations still on monolith `:8081` |
| `cfg.data_retention_policies` | **EXISTS** | Used by IADM-05 retention CRUD |
| `cfg.data_classifications` | **EXISTS** | Unused by split IADM-05 (privacy notices gap) |
| `iam.access_reviews` / `iam.access_review_items` | **EXISTS** | Certify updates review header only; items unused |
| `iam.delegations` | **EXISTS** | Create on `:8101`; Path-C active-check still on monolith |
| `plat.usage_metrics` | **EXISTS** | Aggregates work; college grouping uses `dimension` proxy |
| `cfg.resolve_effective_setting` | **EXISTS** (fn) | Wave A `20261121000200` |
| `plat.check_entitlement` | **EXISTS** (fn) | Wave D `20261121000500` |
| `audit.self_check_immutable` | **EXISTS** (fn) | Wave B `20261121000300` |

### Suggested NEW DDL (Wave F migrations — owner approve before apply)

| Suggested forward | Objects | Stories |
|---|---|---|
| `20261121NNNNN_iadm_wave_f_config_versions.sql` | `cfg.setting_value_versions` (+ trigger or app write on value change); optional `cfg.rollback_setting_value(version_id)` | IADM-02-US-007 |
| `20261121NNNNN_iadm_wave_f_plan_change_requests.sql` | `plat.plan_change_requests` | IADM-04-US-004 |
| `20261121NNNNN_iadm_wave_f_usage_college_id.sql` | `ALTER TABLE plat.usage_metrics ADD COLUMN college_id uuid` (+ index/FK) | IADM-06-US-002 |
| `20261121NNNNN_iadm_wave_f_platform_security_minimums.sql` | `plat.platform_security_minimums` | IADM-07-US-004, US-001 floor |
| `20261121NNNNN_iadm_wave_f_audit_retention_policy.sql` | `plat.audit_retention_policy` | IADM-08-US-003 |
| Optional | `cfg.privacy_notice_versions` **or** reuse `cfg.data_classifications` + purpose JSON | IADM-05-US-002 |
| Optional | `plat.dsr_execution_tasks` (domain instruct/track) | IADM-05-US-005 |

Each forward must ship a matching `*_rollback__*.sql`. Never edit `20261121000200`…`00800`.

---

## Story inventory (IADM-02 … IADM-08)

Legend for **DDL needed:** `yes` = new migration required; `no` = code/tests only on existing objects.

---

### IADM-02 — Platform Configuration & Policy (`:8100` / `/api/v1/iadm/02`)

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-02-US-001** | PARTIAL | `GET/POST …/definitions` → `listDefinitions` / `upsertDefinition`; columns `platform_locked`, `delegable_to_college`, `allowed_scopes` (Wave A) | Empty/missing `allowed_scopes` fail-closed exists; still weak: no dedicated deny when flags omitted as nulls beyond scopes; mutation audit is CampXLogger only (not shared `audit.events` insert). Catalogue CRUD otherwise present. | **no** | LiveDev: upsert with empty scopes → `IADM02_AMBIGUOUS_SCOPE` 400; allowed upsert + `GET /definitions` contains key; R04 without `adm05.manage` → 403 |
| **IADM-02-US-002** | PARTIAL | Platform defaults/locks via same `upsertDefinition` (`platformLocked=true`) | Locked override blocked in `setInstituteValue` / `setCollegeValue`. **Version recorded** not true until value-version table (definition change has no version row). | **yes** *(shared with US-007)* — version rows on definition/value change into `cfg.setting_value_versions` | Set locked default → resolve `source=PLATFORM`; institute set → `IADM02_LOCKED` 403; assert version row exists |
| **IADM-02-US-003** | PARTIAL | `POST …/settings/{key}/institute-value` | Allow-list (`INSTITUTE` in scopes) + lock checks exist. Gap: **type validation** (data_type vs JSON) not enforced. | **no** | Non-allow-listed key → `IADM02_NOT_ALLOWLISTED` 403; wrong JSON type vs `data_type` → 400 once validator added |
| **IADM-02-US-004** | NEW *(code ahead of backlog)* | `POST …/settings/{key}/delegate` → `cfg.setting_delegations`; college write gated on active delegation | Core done. Soft gaps: no list/revoke delegation API; “lock the rest” is implicit (non-delegable cannot be written), not an explicit lock-all action. | **no** | Delegate only when `delegable_to_college`; locked → `IADM02_LOCKED`; college write without delegation → `IADM02_NOT_DELEGATED` |
| **IADM-02-US-005** | NEW *(E0 PASS)* | `GET …/settings/{key}/effective` → `cfg.resolve_effective_setting` | Precedence + lock + fail-closed proven in `PlatformConfigLiveDevTest`. Treat as **ready to mark completed** after status cell update. | **no** | Existing contract test 3/0 + one college-delegated precedence case |
| **IADM-02-US-006** | NEW | Same effective API returns `platformLocked`, `delegated`, `source`, `denied` | No separate R04 desk; acceptable if R04 has `adm05.view` and cannot call write routes. Gap: prove R04 view-only (no write keys). | **no** | R04 `GET …/effective` 200 with flags; R04 `POST …/institute-value` → 403 |
| **IADM-02-US-007** | PARTIAL | `GET …/history` → `cfg.v_setting_history` (fallback `setting_values`); `POST …/rollback` → **501** `IADM02_ROLLBACK_UNAVAILABLE` | History readable. **Rollback = new version + audit** missing until versions table + implement `rollbackSetting`. | **yes** — `cfg.setting_value_versions` (+ write on value mutate; rollback inserts new current version) | After DDL: set value → history ≥1 → rollback to prior `versionId` → effective equals old; audit event |
| **IADM-02-US-008** | NEW | Auditor can use `adm05.view` on read routes if granted | No write-path negative for Auditor (R28); no dedicated “config history read-only” assertion. | **no** | R28 `GET /definitions` + history 200; R28 `POST /definitions` → 403 |

---

### IADM-03 — Platform Identity & Access (`:8101` / `/api/v1/iadm/03`)

*Skip DEFERRED: US-008 break-glass, US-009 two-person appoint.*

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-03-US-001** | PARTIAL | `GET …/roles` → `iam.roles` (`code`,`name` only) | **Permissions not returned** (plain names only). Need join to role↔permission catalogue. | **no** *(use existing `iam.role_permissions` / template tables if present)* | `GET /roles` includes permission codes/names; student → 403 |
| **IADM-03-US-002** | NEW *(API exists)* | `POST/GET …/ceilings` → `iam.role_ceilings` (CADM-03 E0 PASS) | Set/get works. Gap: **CADM cannot exceed ceiling** and **ceiling ≤ R03 holds** not enforced on assign path. | **no** | Set ceiling; assign above ceiling → 403; ceiling keys R03 does not hold → 403 |
| **IADM-03-US-003** | PARTIAL | `POST …/role-assignments`; revoke DELETE/POST; handover gate via `plat.is_college_handed_over` | Missing: **no self-grant**; **no above-own**; Matrix §21.1 grant-table checks in Java. | **no** | Self-assign → 403; assign role caller does not hold → 403; post-handover R03 college assign → `IADM03_HANDOVER_COMPLETE` |
| **IADM-03-US-004** | NEW *(API exists)* | `POST …/access-requests`; `POST …/access-requests/{id}/decide` (blocks self-approve) | Decide only flips status — **does not apply** ROLE_ASSIGN/REVOKE/CEILING_CHANGE payload. | **no** | R04 create PENDING; R03 APPROVE applies assignment; self-decide → blocked |
| **IADM-03-US-005** | PARTIAL | `POST …/delegations` → `iam.delegations` with `valid_from`/`valid_to` | Create + window validation. Gaps: list/revoke; **expiry enforced** at permission check time on this service (Path-C active-check still monolith `:8081`). | **no** for table; optional move active-check | Create ACTIVE; after `valid_to` active-check fails closed; revoke sets REVOKED |
| **IADM-03-US-006** | PARTIAL | `GET …/access-reviews`; `POST …/access-reviews/{id}/certify` | Certify sets review `COMPLETED` only. Gap: **no per-item KEEP/REVOKE** via `iam.access_review_items`; **immediate revoke** of assignments not wired. | **no** | Certify with item REVOKE → assignment soft-deleted same txn |
| **IADM-03-US-007** | NEW | None dedicated | Need read-only list of role assignment changes / delegations / reviews for R28; deny writes. | **no** | R28 GET reviews+delegations 200; R28 POST assign → 403 |

---

### IADM-04 — Subscription, Billing & Entitlements (`:8102` / `/api/v1/iadm/04`)

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-04-US-001** | EXISTS | `GET …/plans` reads `plat.subscription_plans` | **Create/publish/retire** still on monolith `:8081` `/api/v1/admin/billing/plans*`. Wave F: port mutations to `:8102` or keep EXISTS with explicit monolith ownership note. | **no** | POST plan + publish + retire on `:8102` (or documented EXISTS evidence from monolith suite) |
| **IADM-04-US-002** | EXISTS | `GET …/subscriptions` | Assign + state transitions on monolith only. | **no** | Assign ACTIVE + illegal transition → 400 on split (or EXISTS evidence) |
| **IADM-04-US-003** | PARTIAL | Lists subscriptions (+ plans/invoices separately) | No single “own plan + entitlements” aggregate for R03. | **no** | R03 GET own subscription+plan+entitlement keys; other tenant → empty/403 |
| **IADM-04-US-004** | NEW | `POST …/plan-change-requests` → **501** `IADM04_PLAN_CHANGE_UNAVAILABLE` | Need table + persist PENDING request (R03); R01 decide later optional for completed. | **yes** — `plat.plan_change_requests` | R03 create → 201 + row; missing table path gone; student → 403 |
| **IADM-04-US-005** | EXISTS | `GET …/invoices` | Issue/reconcile on monolith. | **no** | Issue invoice + list read-back (split or EXISTS evidence) |
| **IADM-04-US-006** | PARTIAL *(E0 PASS)* | `GET …/entitlements/check` + `plat.check_entitlement` | Fail-closed unknown key proven. Minor: ensure non-platform callers with `iadm04.view` only work; metering feed (US-006↔IADM-06-US-004) still thin. | **no** | Existing `Iadm04ContractLiveDevTest` 4/0 + known key with limit |
| **IADM-04-US-007** | NEW | Auditor has `iadm04.view` grant (Wave CD role grants) | Prove read lists; deny plan-change write. | **no** | R28 GET plans/subs/invoices 200; POST plan-change → 403 |

---

### IADM-05 — Data Governance & Privacy (`:8103` / `/api/v1/iadm/05`)

*Skip DEFERRED: US-007 under-18 guardian consent.*

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-05-US-001** | NEW | `GET/POST …/retention` → `cfg.data_retention_policies` | CRUD works. Gap: **≥ platform minimum** not enforced (no platform retention floor table). | **yes** if floor required — e.g. extend `plat.audit_retention_policy` pattern or `plat.retention_minimums`; **no** if accept institute-only rules | Upsert days below platform min → 400; at/above → 201 |
| **IADM-05-US-002** | NEW | *(none)* — `cfg.data_classifications` exists unused | No privacy notice / consent purpose version API. | **yes** preferred — `cfg.privacy_notice_versions` (purpose, version, effective_at) **or** CRUD on `cfg.data_classifications` if owner accepts as notice proxy | Create notice version v2; list shows versions; R28 read-only |
| **IADM-05-US-003** | NEW *(core done)* | `GET/POST …/dsr` → `plat.data_subject_requests` | Create/list OPEN. Gap: due dates / college assign optional fields unused. | **no** | Create ACCESS/EXPORT; list contains id; bad type → 400 |
| **IADM-05-US-004** | NEW | `POST …/dsr/{id}/execute` checks `iadm05.execute` | No **assigned_college_id** scope — any execute key can run any DSR in tenant. | **no** | R04 execute only when `assigned_college_id` matches; else 403 |
| **IADM-05-US-005** | NEW | Execute flips OPEN→IN_PROGRESS / else COMPLETED | **No instruct domain services**; no completion tracking table/outbox. Status-only stub. | **yes** optional — `plat.dsr_execution_tasks` (service, status, completed_at) | Execute ERASURE/EXPORT creates task rows; completion updates DSR COMPLETED |
| **IADM-05-US-006** | NEW *(core done)* | `POST …/legal-holds`; erase execute blocked by active hold | Core acceptance met. | **no** | Place hold → erase execute → `IADM05_LEGAL_HOLD` 403; release/absent → progresses |
| **IADM-05-US-008** | NEW | `iadm05.view` granted to AUDITOR | No dedicated auditor suite proving no write. | **no** | R28 GET dsr/retention 200; POST dsr → 403 |

---

### IADM-06 — Platform Analytics & Usage (`:8104` / `/api/v1/iadm/06`)

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-06-US-001** | NEW | `GET …/usage/platform` (platform admin) | Aggregates by tenant+metric. Export key `iadm06.export` unused on this route. | **no** | Platform admin 200 with items; R03 → 403 |
| **IADM-06-US-002** | NEW | `GET …/usage/by-college` groups by `dimension` proxy (`partial:true`) | Need real **`college_id`** grouping. | **yes** — `ALTER … ADD COLUMN college_id uuid` (+ index) | Insert metrics with college_id; by-college returns that UUID; `partial` false |
| **IADM-06-US-003** | NEW | `GET …/usage/export` JSON wrapper | Thin export; no CSV/file; should require `iadm06.export`. | **no** | Caller with export key 200; without → 403 |
| **IADM-06-US-004** | NEW | *(none)* — entitlement reads `usage_metrics` but no ingest API on `:8104` | Need metering feed POST (internal/HMAC) writing `plat.usage_metrics` for IADM-04. | **no** for table; **yes** only if college_id required for feed | POST metric → row; `check_entitlement` used_value increases |
| **IADM-06-US-005** | NEW | Auditor `iadm06.view` | Prove read; deny export/platform if not entitled. | **no** | R28 GET `/usage` 200; POST ingest → 403 |

---

### IADM-07 — Security & Compliance (`:8105` / `/api/v1/iadm/07`)

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-07-US-001** | NEW *(core)* | `GET/POST …/policies` → `plat.institute_security_policies` | Upsert works. Gap: **≥ platform minimum** not validated. | **yes** *(depends on US-004 table)* | Policy below minimum → 400; at/above → 201 |
| **IADM-07-US-002** | NEW *(core)* | `GET …/posture` (open findings by severity + policyCount) | Thin but meets “view posture”. Optional: include minimum compliance flags after US-004. | **no** | R03 GET posture 200 with severity map |
| **IADM-07-US-003** | NEW *(core)* | `GET/POST …/findings` → `plat.security_findings` | Create/list OPEN. Gap: assign/close transitions optional. | **no** | Create finding → list contains OPEN |
| **IADM-07-US-004** | NEW | `GET …/platform-minimums` stub (`partial:true`, empty) | Need table + R01/R02 manage + cross-institute posture rollup. | **yes** — `plat.platform_security_minimums` | Upsert minimum; GET returns items; `partial` false |
| **IADM-07-US-005** | NEW | Posture/findings are tenant-wide | No college-scoped posture (`college_id` on findings unused). | **no** | R04 GET posture?collegeId=… only own college findings |
| **IADM-07-US-006** | NEW | Auditor `iadm07.view` | Read-only proof. | **no** | R28 GET posture/findings 200; POST findings → 403 |

---

### IADM-08 — Audit, Health & Operations (`:8106` / `/api/v1/iadm/08`)

| ID | Backlog status | Code / API already on split | Acceptance gap remaining | NEW DDL? | Minimal test to mark completed |
|---|---|---|---|---|---|
| **IADM-08-US-001** | PARTIAL *(E0 PASS)* | `GET …/audit/search` → `audit.events` | Own-institute search works. Ready to mark completed after status update + R03-only negative. | **no** | Existing contract 3/0 |
| **IADM-08-US-002** | PARTIAL | `GET …/audit/export` wraps search as JSON download metadata | Auditor AU/E: ensure R28 can export; no mutate. | **no** | R28 export 200; R28 cannot UPDATE via API (no write route) |
| **IADM-08-US-003** | PARTIAL | `GET …/audit/retention` → defaults / missing table | **Manage** retention (R01/R02) not implemented — GET-only defaults. Need table + POST/PUT (never edit/delete audit rows). | **yes** — `plat.audit_retention_policy` | R01 set days → GET source=`plat.audit_retention_policy`; R03 manage → 403 |
| **IADM-08-US-004** | NEW *(done)* | `GET …/ops/health` → `plat.service_health` | Present. Mark completed with platform-admin test. | **no** | Platform admin 200; student 403 |
| **IADM-08-US-005** | NEW *(done)* | `GET …/ops/institute-status` → `core.tenants` + lifecycle event count | Present. | **no** | R03 200 own tenant; wrong tenant isolation |
| **IADM-08-US-006** | NEW *(done)* | `GET …/audit/self-check` → `audit.self_check_immutable()` | Present. | **no** | Checks include no UPDATE/DELETE policy on `audit.events` |

---

## Wave E tracked PARTIAL gaps → F ownership

| Wave E gap | Story | F phase | DDL |
|---|---|---|---|
| Config rollback 501 | IADM-02-US-007 | F1 | `cfg.setting_value_versions` |
| Plan-change 501 | IADM-04-US-004 | F3 | `plat.plan_change_requests` |
| `usage_metrics.college_id` | IADM-06-US-002 | F3 | ALTER COLUMN |
| `plat.platform_security_minimums` | IADM-07-US-004 | F4 | new table |
| Audit retention defaults | IADM-08-US-003 | F2 | `plat.audit_retention_policy` |
| Monolith Path-C delegations shim | IADM-03-US-005 (partial) | F2 optional | none (move route later) |

---

## Existing routes cheat-sheet (split services)

| Service | Port | Implemented routes |
|---|---|---|
| IADM-02 | 8100 | `/definitions`, `/settings/{key}/institute-value\|college-value\|delegate\|effective\|history\|rollback`, `/health` |
| IADM-03 | 8101 | `/roles`, `/ceilings`, `/access-requests[+ /decide]`, `/role-assignments[+ /revoke]`, `/delegations`, `/access-reviews[+ /certify]`, `/health` |
| IADM-04 | 8102 | `/plans`, `/subscriptions`, `/invoices`, `/plan-change-requests` (501), `/entitlements/check`, `/health` |
| IADM-05 | 8103 | `/dsr[+ /execute]`, `/retention`, `/legal-holds`, `/health` |
| IADM-06 | 8104 | `/usage`, `/usage/by-college`, `/usage/export`, `/usage/platform`, `/health` |
| IADM-07 | 8105 | `/policies`, `/findings`, `/posture`, `/platform-minimums`, `/health` |
| IADM-08 | 8106 | `/audit/search`, `/audit/export`, `/audit/retention`, `/audit/self-check`, `/ops/health`, `/ops/institute-status`, `/health` |

---

## Exit criteria (Wave F)

1. Every non-deferred story above is `completed` in `IADM_User_Stories.md` **or** listed as blocked with owner decision in `WAVE_F_REPORT.md`.
2. Five Wave E gaps closed (or explicitly deferred by owner).
3. New migrations applied on DEV with rollbacks present.
4. Touched module Maven suites green; at least one LiveDev test per closed 501/DDL gap.
5. STOP for `APPROVED WAVE-F` before CADM Step 0.

---

## Still deferred (no Wave F work)

- IADM-03-US-008 break-glass  
- IADM-03-US-009 two-person Institution Admin appoint  
- IADM-05-US-007 under-18 guardian consent  
- R27 / `adm03` API clients  
- Kafka / intelligence bypass  
