# IADM — User Stories (Final reconciled backlog)

**Module:** IADM = Institute Administration Module (IADM-01 … IADM-08)  
**Date:** 2026-11-21  
**Owner:** Niranjan V.G.  
**Status:** FINAL for Waves A–D implementation in DEV  
**Derived from:** `IADM_Implementation_Plan_and_User_Stories_DRAFT.md` (kept unchanged), `IADM_Architecture_Design.md` v2.0, legacy ADM-01 CSV (history), RBAC Matrix v3.0, live `institute-admin-service` inventory  
**Traceability column:** legacy ADM-01 component and/or architecture §  

Status: **EXISTS** | **PARTIAL** | **NEW** | **DEFERRED**  
Access letters: M manage, W write, V view, E export, AU audit read, X execute  

---

## Waves

| Wave | Services |
|---|---|
| A | IADM-01 Institute & Tenant Lifecycle; IADM-02 Platform Configuration & Policy |
| B | IADM-03 Platform Identity & Access Governance; IADM-08 Audit, Health & Operations |
| C | IADM-07 Security & Compliance Governance; IADM-05 Data Governance & Privacy |
| D | IADM-04 Subscription, Billing & Entitlements; IADM-06 Platform Analytics & Usage |

Wave order unchanged (no dependency force).

---

## IADM-01 — Institute & Tenant Lifecycle (Wave A)

Access: Super Admin (R01) M; System Admin (R02) W/V; Institution Admin (R03) M own institute; Auditor (R28) V.  
Keys: `adm01.manage` / `adm01.write` / `adm01.view`  
Port: **8099** · Gateway: `/api/v1/iadm/01`

| ID | Story | Acceptance criteria | Access | Status | Traceability |
|---|---|---|---|---|---|
| IADM-01-US-001 | As Super Admin (R01), I register a new institute so that it gets its own isolated tenant. | Name, code, region; unique code; tenant row; audit; duplicate rejected | R01 M | completed | ADM01_institutes; Arch §5.1; :8099 |
| IADM-01-US-002 | As Super Admin (R01), I appoint the first Institution Admin (R03). | Uses `plat.assign_institution_admin`; caller from `auth.uid()` not request field; audit; no self-appointment | R01 M | completed | Arch §5.1; Matrix §21.1 / D-RBAC-M23; Step 0.1 fixed |
| IADM-01-US-003 | As Institution Admin (R03), I register a college under my institute with first College Admin (R04). | Creates college, first College Admin, handover record; atomic; audit; Auth-linked admin (SEC-11) | R03 M | completed | :8099 + `plat.is_auth_linked_user` |
| IADM-01-US-004 | As Institution Admin (R03), I record handover of a college to its College Admin. | Handover record; after handover R03 loses direct college user admin; Auditor visible | R03 M | completed | `plat.is_college_handed_over`; IADM-03 gate |
| IADM-01-US-005 | As Institution Admin (R03), I view my institute profile and lifecycle status. | Status + dates; own institute only | R03 V | completed | `IADM01_OWN_TENANT_ONLY` |
| IADM-01-US-006 | As Super Admin (R01), I suspend or reactivate an institute. | Reason required; sessions blocked while suspended; reversible; audit | R01 M | completed | `plat.is_tenant_suspended` + session blocks |
| IADM-01-US-007 | As Institution Admin (R03), I request suspension/decommission/export; Super Admin decides. | R03 request only; decision stored; destructive needs R01 | R03 request, R01 M | completed | Matrix §21.1 guard 4 |
| IADM-01-US-008 | As Auditor (R28), I view institute lifecycle history read-only. | Timeline; no write path | R28 V | completed | Arch §5.1; `adm01.view` |

---

## IADM-02 — Platform Configuration & Policy (Wave A)

Access: R01 M; R02 M; R03 W allow-listed; College Admin (R04) V effective only; R28 V.  
Keys: `adm05.manage` / `adm05.write` / `adm05.view`  
Port: **8100** · Gateway: `/api/v1/iadm/02`  
*Negative:* no IADM authority over notification settings.

| ID | Story | Acceptance criteria | Access | Status | Traceability |
|---|---|---|---|---|---|
| IADM-02-US-001 | As Super Admin (R01), I define the configuration catalogue (type, default, scope, lock, delegable). | Missing scope/flag fails closed; audit on change | R01 M | completed | :8100 Wave F |
| IADM-02-US-002 | As System Admin (R02) or Super Admin (R01), I set platform defaults and locks. | Locked values cannot be overridden; version recorded | R01/R02 M | completed | `cfg.setting_value_versions` |
| IADM-02-US-003 | As Institution Admin (R03), I set institute-level values only for allow-listed settings. | Non-allow-listed → 403; type validated | R03 W | completed | `IADM02_TYPE_MISMATCH` |
| IADM-02-US-004 | As Institution Admin (R03), I delegate selected college-level settings and lock the rest. | Only `delegable_to_college`; per-college delegation; locked stays locked | R03 W | completed | :8100 Wave F |
| IADM-02-US-005 | As any service, I resolve effective value (platform > institute > college). | Single API; locked wins; ambiguity deny; tenant-isolated; precedence tests | internal | completed | CADM-01 E0 PASS |
| IADM-02-US-006 | As College Admin (R04), I see effective value and inherited/locked/delegated flags. | Read-only; cannot override IADM | R04 V | completed | effective flags |
| IADM-02-US-007 | As R01/R03, I view setting history and roll back. | Who/when/old/new; rollback = new version + audit | R01 M, R03 W | completed | `Iadm02WaveFLiveDevTest` |
| IADM-02-US-008 | As Auditor (R28), I view configuration and history read-only. | No write path | R28 V | completed | `adm05.view` |

---

## IADM-03 — Platform Identity & Access Governance (Wave B)

Access: R01 M; R02 M; R03 M within ceiling; R04 request only; R28 V.  
Keys: `adm08.*`, `adm09.*` · Port **8101** · `/api/v1/iadm/03`  
*Excluded:* API client issuing (`adm03` dormant, IADM-D5).

| ID | Story | Acceptance | Access | Status | Traceability |
|---|---|---|---|---|---|
| IADM-03-US-001 | View role catalogue + permissions for institute | Read-only; plain names | R03 V | completed | roles + permissions join |
| IADM-03-US-002 | Set role ceilings for College Admin | CADM-03 cannot exceed; ceiling ≤ R03 holds | R03 M | completed | ceiling gate on assign |
| IADM-03-US-003 | Assign/remove roles per grant table §21.1 | No self-grant; no above-own; audit; Student/Parent via STM-07 | R03 M | completed | `iam.role_grant_rules` |
| IADM-03-US-004 | College Admin requests role/ceiling change; R03 decides | Request + decision stored; no self-approve | R04 request, R03 M | completed | decide applies ROLE_ASSIGN |
| IADM-03-US-005 | Time-boxed delegations | Scope + expiry enforced | R03 M | completed | create/list/revoke |
| IADM-03-US-006 | Access review certify/revoke | Existing access-review tables; immediate revoke | R03 M | completed | REVOKE soft-deletes assignment |
| IADM-03-US-007 | Auditor view role changes/delegations/reviews | No write | R28 V | completed | `adm09.view` lists |
| IADM-03-US-008 | Break-glass tenant recovery | Reason + expiry; audited | R01 | DEFERRED | Matrix §21.1 guard 5; §22.5 |
| IADM-03-US-009 | Two-person appoint/remove Institution Admin | Two different people | R01 | DEFERRED | Matrix §21.7; FINDING-RBAC-07 |

---

## IADM-08 — Audit, Health & Operations (Wave B)

Keys: shared audit view · Port **8106** · `/api/v1/iadm/08` · One shared append-only audit store.

| ID | Story | Access | Status | Traceability |
|---|---|---|---|---|
| IADM-08-US-001 | Search audit trail own institute | R03 V | completed | CADM-07 E0 PASS |
| IADM-08-US-002 | Auditor view/export evidence | R28 V/AU/E | completed | `/audit/export` |
| IADM-08-US-003 | Manage audit retention/archival (no edit/delete records) | R01/R02 M | completed | `audit.retention_policies` + RLS |
| IADM-08-US-004 | View service health | R01/R02 M | completed | `/ops/health` |
| IADM-08-US-005 | Institute operational status | R03 V | completed | `/ops/institute-status` |
| IADM-08-US-006 | Audit self-check (no update/delete possible) | R02 | completed | `audit.self_check_immutable` |

---

## IADM-07 — Security & Compliance (Wave C)

Port **8105** · `/api/v1/iadm/07`

| ID | Story | Access | Status | Traceability |
|---|---|---|---|---|
| IADM-07-US-001 | Institute security policy ≥ platform minimum | R03 W | completed | `IADM07_BELOW_MINIMUM` |
| IADM-07-US-002 | View compliance posture | R03 V | completed | `/posture` |
| IADM-07-US-003 | Track security findings | R03 W | completed | create/list findings |
| IADM-07-US-004 | Platform security minimums + cross-institute posture | R01/R02 M | completed | `plat.platform_security_minimums` |
| IADM-07-US-005 | College Admin view own college posture | R04 V | completed | `/posture/college` |
| IADM-07-US-006 | Auditor read-only | R28 V | completed | view keys |

---

## IADM-05 — Data Governance & Privacy (Wave C)

Port **8103** · `/api/v1/iadm/05` · New keys `iadm05.*` created when built. Domain services execute erasure/export.

| ID | Story | Access | Status | Traceability |
|---|---|---|---|---|
| IADM-05-US-001 | Retention rules ≥ platform minimum | R03 W | completed | `plat.institute_retention_policies` ≥ `plat.retention_minimums` |
| IADM-05-US-002 | Privacy notice / consent purpose versions | R03 W | completed | `cfg.data_classifications` proxy |
| IADM-05-US-003 | Data-subject requests | R03 W | completed | create/list DSR |
| IADM-05-US-004 | College Admin execute assigned tasks | R04 X | completed | college-scoped execute |
| IADM-05-US-005 | Instruct domain services erase/export; track completion | internal | completed | IN_PROGRESS→COMPLETED notes |
| IADM-05-US-006 | Legal hold blocks erasure | R03 W | completed | `IADM05_LEGAL_HOLD` |
| IADM-05-US-007 | Guardian-consent rules under-18 | R03 W | DEFERRED | D-RBAC-M17 legal check |
| IADM-05-US-008 | Auditor read-only | R28 V | completed | `iadm05.view` |

---

## IADM-04 — Subscription, Billing & Entitlements (Wave D)

Port **8102** · `/api/v1/iadm/04` · Keep `adm01.manage` until `iadm04.*` created.

| ID | Story | Access | Status | Traceability |
|---|---|---|---|---|
| IADM-04-US-001 | Create/publish/retire plans | R01 M | completed | :8102 plan mutations |
| IADM-04-US-002 | Assign subscription + state transitions | R01 M | completed | assign + legal transitions; `Iadm04PendingCloseLiveDevTest` |
| IADM-04-US-003 | View own plan/subscription/entitlements | R03 V | completed | `/subscription/mine` |
| IADM-04-US-004 | Request plan change/add-on | R03 W limited | completed | `plat.plan_change_requests` |
| IADM-04-US-005 | Issue invoices / reconcile | R01 M | completed | issue ISSUED + reconcile PAID/VOID |
| IADM-04-US-006 | Entitlement check API | internal | completed | E0 PASS |
| IADM-04-US-007 | Auditor billing read-only | R28 V | completed | `iadm04.view` |

---

## IADM-06 — Platform Analytics & Usage (Wave D)

Port **8104** · `/api/v1/iadm/06`

| ID | Story | Access | Status | Traceability |
|---|---|---|---|---|
| IADM-06-US-001 | Platform-wide usage aggregates | R01 M/E | completed | `/usage/platform` |
| IADM-06-US-002 | Institute usage by college aggregates | R03 V/E | completed | `college_id` column |
| IADM-06-US-003 | Export aggregate reports | R01/R03 E | completed | `/usage/export` |
| IADM-06-US-004 | Metering feed to IADM-04 | internal | completed | `POST /usage/ingest` |
| IADM-06-US-005 | Auditor read-only | R28 V | completed | `iadm06.view` |

---

## CADM contracts (publish early)

| Contract | Stories | Consumer |
|---|---|---|
| First College Admin + handover | IADM-01-US-003, US-004 | CADM-02 |
| Effective config resolution | IADM-02-US-005 | CADM-01 |
| Role ceiling lookup | IADM-03-US-002 | CADM-03 |
| Shared audit read | IADM-08-US-001 | CADM-07 |
| Entitlement check | IADM-04-US-006 | All services |

---

## Cross-cutting (every IADM service)

| ID | Rule |
|---|---|
| IADM-CC-01 | Permission-key checks; fail closed 403 |
| IADM-CC-02 | Mutation → shared audit entry |
| IADM-CC-03 | Tenant from signed gateway identity; non-privileged DB session (RLS) |
| IADM-CC-04 | Gateway registration; next free port from registry |
| IADM-CC-05 | Allowed/denied tests per role + one live DEV test per service |
| IADM-CC-06 | New numbered migrations + rollbacks; never edit old migrations |
