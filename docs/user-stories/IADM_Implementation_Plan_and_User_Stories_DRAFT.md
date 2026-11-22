# IADM — Implementation Plan and User Stories (DRAFT v0.1)

**Module:** IADM = Institute Administration Module (8 services, IADM-01 … IADM-08)
**Date:** 2026-11-21
**Owner:** Niranjan V.G.
**Status:** DRAFT. Derived from the RBAC Final Matrix v3.0 (§2, §8, §12, §21, §22), owner decisions, and what the RBAC audits saw in the code. The coding agent must reconcile it with `IADM_Architecture_Design.md` and the legacy ADM-01 stories (34 stories, 8 epics) before building (Step 0 in the kickoff file), and records traceability for every story.
**Not covered here:** CADM (College Administration Module) stories come next; this plan only defines what CADM needs from IADM (§4).

---

## 1. Guiding rules (already decided)

| Rule | Source |
|---|---|
| Flow is UI → API Gateway (`:8080`) → Java service → Supabase PostgreSQL. No direct frontend database access in new work. | Architecture rules, AGENTS.md |
| Supabase, not MongoDB. Kafka work is deferred. No new infrastructure or tools. | Owner constraints |
| Each IADM service is a separate microservice (not a logical block inside one admin service). | Owner decision 2026-11-19 |
| IADM-02 (Platform Configuration & Policy) has the upper hand: nothing set in IADM can be overridden by CADM. Missing value, default or lock flag is never permission; ambiguous authority fails closed; Java services enforce server-side. | Owner decisions |
| IADM delegates only college-level settings. Notification settings are not an IADM authority; each college manages its own. | Owner decisions |
| Institute Super Admin means Institution Admin (R03). Super Admin (R01) cross-institute operations are described inside IADM. | IADM-D1, IADM-D2 |
| Access is checked by permission key, never by role name. Every change writes an audit entry. | Matrix §5, §21.1 |
| Institution Admin (R03) may not create or elevate API clients; `adm03` stays dormant. | §21.7, IADM-D5 |
| Old ADM-01 / ADM-02 stories are history only. New IDs are `IADM-0X-US-0NN`, each with a traceability reference. | Owner decision |

---

## 2. Delivery waves

| Wave | Services | Why this order | What it unlocks |
|---|---|---|---|
| **A** | IADM-01 Institute & Tenant Lifecycle, IADM-02 Platform Configuration & Policy | CADM depends on the first College Admin handover and on effective configuration | CADM-01, CADM-02 can start |
| **B** | IADM-03 Platform Identity & Access Governance, IADM-08 Audit, Health & Operations | Role ceilings and the shared audit view are needed before CADM-03 and CADM-07 | CADM-03, CADM-07 |
| **C** | IADM-07 Security & Compliance Governance, IADM-05 Data Governance & Privacy | Builds on access reviews and audit; privacy stories partly wait on the legal check (not a gate) | Compliance reporting |
| **D** | IADM-04 Subscription, Billing & Entitlements, IADM-06 Platform Analytics & Usage | Billing code already exists and only needs moving and hardening; analytics needs usage data from A–C | Entitlement checks, usage reporting |

**Per-wave definition of done:** all stories in the wave have acceptance criteria met and tests green; a new numbered database migration with a rollback script; gateway route registered; permission checks by key with allowed/denied tests per role; audit entries verified; a wave report file; a local commit; PROGRESS.md and NEXT_TASK.md updated.

**Frontend:** API and database first. Admin screens for IADM are a separate wave after the APIs are stable, unless a story below says a screen is required (assumption, changeable).

---

## 3. User stories

Status tags: **EXISTS-VERIFY** the RBAC audits saw code for it; confirm and move. **PARTIAL** some pieces exist. **NEW** nothing seen. **DEFERRED** accepted risk or waiting on a decision.
Access column uses matrix §8 letters: M = manage, W = write, V = view, E = export, AU = audit read, X = execute. Key names come from the Step 0 key map.

### IADM-01 — Institute & Tenant Lifecycle (Wave A)
Access: Super Admin (R01) M; System Admin (R02) W/V; Institution Admin (R03) M own institute; Auditor (R28) V.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-01-US-001 | As Super Admin (R01), I register a new institute so that it gets its own isolated tenant. | Name, code, region captured; unique code; tenant row created; audit entry; duplicate code rejected | R01 M | PARTIAL |
| IADM-01-US-002 | As Super Admin (R01), I appoint the first Institution Admin (R03) of an institute so the institute can be run. | Uses the platform appointment function; caller identity taken from the login, never from a request field; audit entry; no self-appointment | R01 M | EXISTS-VERIFY |
| IADM-01-US-003 | As Institution Admin (R03), I register a college under my institute so that it gets a tenant scope and a first College Admin (R04). | Existing onboarding endpoint; creates college, first College Admin, and a handover record; atomic; audit entry | R03 M | EXISTS-VERIFY |
| IADM-01-US-004 | As Institution Admin (R03), I record the handover of a college to its College Admin so that CADM-02 takes over user management. | Handover record (who, when, college); after handover R03 loses direct college user administration; visible to Auditor | R03 M | NEW |
| IADM-01-US-005 | As Institution Admin (R03), I view my institute profile and lifecycle status. | Status (e.g. active, suspended) and key dates; own institute only | R03 V | PARTIAL |
| IADM-01-US-006 | As Super Admin (R01), I suspend or reactivate an institute so that access can be stopped safely. | Reason required; sessions blocked while suspended; reversible; audit entry | R01 M | NEW |
| IADM-01-US-007 | As Institution Admin (R03), I request suspension, decommission or full export of my institute; Super Admin approves. | R03 can only request (matrix §21.1 guard 4); request, decision and timestamps stored; destructive actions need R01 | R03 request, R01 M | NEW |
| IADM-01-US-008 | As Auditor (R28), I view the lifecycle history of institutes read-only. | Timeline of created, appointed, suspended, handovers; no write path | R28 V | NEW |

### IADM-02 — Platform Configuration & Policy (Wave A)
Access: R01 M; R02 M; R03 W within allowed institute settings; College Admin (R04) V effective only; R28 V.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-02-US-001 | As Super Admin (R01), I define the configuration catalogue so that every setting has a type, default, scope level, lock flag and "delegable to college" flag. | A setting with no scope or flag is unusable and fails closed; audit entry on every change | R01 M | PARTIAL |
| IADM-02-US-002 | As System Admin (R02) or Super Admin (R01), I set platform defaults and platform locks. | Locked values cannot be overridden at lower levels; version recorded | R01/R02 M | PARTIAL |
| IADM-02-US-003 | As Institution Admin (R03), I set institute-level values only for allow-listed settings. | Non-allow-listed setting rejected (403); value validated against type | R03 W | PARTIAL |
| IADM-02-US-004 | As Institution Admin (R03), I delegate selected college-level settings to colleges and lock the rest. | Only settings flagged "delegable"; delegation recorded per college; locked stays locked | R03 W | NEW |
| IADM-02-US-005 | As any service, I resolve the effective value of a setting (platform > institute > college) so that precedence is enforced everywhere. | Single resolution API; locked platform/institute value wins; missing flag or ambiguity returns "deny"; tenant-isolated; tested for every precedence combination | internal | NEW |
| IADM-02-US-006 | As College Admin (R04), I see the effective value and whether it is inherited, locked or delegated. | Read-only; cannot override IADM values | R04 V | NEW |
| IADM-02-US-007 | As Institution Admin (R03) or Super Admin (R01), I view the change history of a setting and roll back to an earlier version. | Who, when, old and new value; rollback creates a new version and an audit entry | R01 M, R03 W | NEW |
| IADM-02-US-008 | As Auditor (R28), I view configuration and its history read-only. | No write path | R28 V | NEW |

*Negative requirement:* IADM-02 has no authority over notification settings (each college manages its own).

### IADM-03 — Platform Identity & Access Governance (Wave B)
Access: R01 M; R02 M; R03 M within institute ceiling; R04 request only; R28 V.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-03-US-001 | As Institution Admin (R03), I view the role catalogue and each role's permission set for my institute. | Roles R01–R39 as defined; read-only; plain-language names | R03 V | PARTIAL |
| IADM-03-US-002 | As Institution Admin (R03), I set role ceilings: the maximum roles and keys a College Admin may assign in a college. | CADM-03 cannot exceed the ceiling; ceiling cannot exceed what R03 holds | R03 M | NEW |
| IADM-03-US-003 | As Institution Admin (R03), I assign and remove roles per the grant table (matrix §21.1) so that staff can work. | Allowed roles only; no self-grant; no grant above own permissions; scope enforced; audit entry; Student and Parent only through account provisioning | R03 M | PARTIAL |
| IADM-03-US-004 | As College Admin (R04), I request a role or ceiling change; Institution Admin decides. | Request, decision, reason stored; R04 cannot self-approve | R04 request, R03 M | NEW |
| IADM-03-US-005 | As Institution Admin (R03), I create time-boxed delegations (role, scope, validity). | A delegate gets only the delegated role within the scope; expired delegation stops immediately | R03 M | PARTIAL |
| IADM-03-US-006 | As Institution Admin (R03), I run an access review: list role holders, certify or revoke. | Uses the existing access-review table; revoke takes effect immediately; result stored | R03 M | PARTIAL |
| IADM-03-US-007 | As Auditor (R28), I view role changes, delegations and reviews read-only. | No write path | R28 V | NEW |
| IADM-03-US-008 | As Super Admin (R01), I use a time-boxed, audited break-glass path to recover a tenant. | Reason and expiry required; every action audited | R01 | DEFERRED (accepted risk, before production) |
| IADM-03-US-009 | As Super Admin (R01), a second approver must confirm appointing or removing an Institution Admin. | Two different people | R01 | DEFERRED (accepted risk, before production) |

*Excluded:* API client issuing (adm03 family is dormant, IADM-D5).

### IADM-04 — Subscription, Billing & Entitlements (Wave D)
Access: R01 M; R02 W; R03 V and limited W on own subscription; R28 V. Student fees stay in Finance (FIN).

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-04-US-001 | As Super Admin (R01), I create, publish and retire commercial plans. | Platform-admin only; existing handlers moved into the IADM-04 service | R01 M | EXISTS-VERIFY |
| IADM-04-US-002 | As Super Admin (R01), I assign a subscription to an institute and move it through its states. | State transitions validated; audit entry | R01 M | EXISTS-VERIFY |
| IADM-04-US-003 | As Institution Admin (R03), I view my institute's plan, subscription and entitlements. | Own institute only | R03 V | PARTIAL |
| IADM-04-US-004 | As Institution Admin (R03), I request a plan change or add-on for my subscription. | Request stored; Super Admin decides | R03 W (limited) | NEW |
| IADM-04-US-005 | As Super Admin (R01), I issue invoices and reconcile transactions. | Existing handlers; permission key enforced (already patched) | R01 M | EXISTS-VERIFY |
| IADM-04-US-006 | As any service, I check an institute's entitlement (feature or limit) so that plan limits are enforced. | Entitlement API; unknown entitlement denies; cached safely | internal | PARTIAL |
| IADM-04-US-007 | As Auditor (R28), I view billing records read-only. | No write path | R28 V | NEW |

### IADM-05 — Data Governance & Privacy (Wave C)
Access: R01 M; R02 W; R03 W own-institute adherence; College Admin (R04) V and execute under policy; R28 V. Domain services execute erasure and export; IADM-05 only instructs and tracks.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-05-US-001 | As Institution Admin (R03), I define retention rules per data category within the platform minimum. | Category, period, legal basis note; cannot go below platform minimum | R03 W | NEW |
| IADM-05-US-002 | As Institution Admin (R03), I maintain privacy notice and consent purpose versions. | Versioned; effective date; history kept | R03 W | NEW |
| IADM-05-US-003 | As Institution Admin (R03), I register and track data-subject requests (access, correction, erasure). | Request type, subject reference, due date, status | R03 W | NEW |
| IADM-05-US-004 | As College Admin (R04), I execute assigned request tasks for my college under policy. | Only tasks assigned to the college; every step audited | R04 X | NEW |
| IADM-05-US-005 | As IADM-05, I instruct domain services to erase or export and record their completion. | No direct data access by IADM-05; each service reports done or failed; retry | internal | NEW |
| IADM-05-US-006 | As Institution Admin (R03), I place a legal hold that blocks erasure. | Hold overrides erasure instructions; release is audited | R03 W | NEW |
| IADM-05-US-007 | As Institution Admin (R03), I configure guardian-consent rules for students under 18. | Waits on the legal check (D-RBAC-M17); not a gate on other stories | R03 W | DEFERRED |
| IADM-05-US-008 | As Auditor (R28), I view requests and completion evidence read-only. | No write path | R28 V | NEW |

*New permission keys are created for this service when it is built (no keys exist today).*

### IADM-06 — Platform Analytics & Usage (Wave D)
Access: R01 M/E; R02 V/E; R03 V/E own-institute aggregate; R28 V. No raw cross-college browsing.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-06-US-001 | As Super Admin (R01), I view platform-wide usage (institutes, active users, usage volume) as aggregates. | Aggregates only; no person-level rows | R01 M/E | NEW |
| IADM-06-US-002 | As Institution Admin (R03), I view my institute's usage by college as aggregates. | Own institute only; no raw cross-college browse | R03 V/E | NEW |
| IADM-06-US-003 | As Super Admin (R01) or Institution Admin (R03), I export aggregate reports. | Export audited; same aggregate rules | R01/R03 E | NEW |
| IADM-06-US-004 | As IADM-04, I receive usage metering to compare against entitlements. | Metering feed; no personal data | internal | NEW |
| IADM-06-US-005 | As Auditor (R28), I view usage reports read-only. | No write path | R28 V | NEW |

### IADM-07 — Security & Compliance Governance (Wave C)
Access: R01 M; R02 M; R03 W own-institute posture; College Admin (R04) V; R28 V. Technical enforcement stays in the shared services.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-07-US-001 | As Institution Admin (R03), I set institute security policy (e.g. session and credential rules) at or above the platform minimum. | Cannot weaken the platform minimum; enforced by shared services | R03 W | NEW |
| IADM-07-US-002 | As Institution Admin (R03), I view my institute's compliance posture (policies met, access reviews due, open findings). | Own institute only | R03 V | NEW |
| IADM-07-US-003 | As Institution Admin (R03), I track security findings with an owner and status. | Created, assigned, closed; audited | R03 W | NEW |
| IADM-07-US-004 | As Super Admin (R01) or System Admin (R02), I set platform security minimums and review institute posture across institutes. | Aggregate view; changes audited | R01/R02 M | NEW |
| IADM-07-US-005 | As College Admin (R04), I view the posture of my own college. | Read-only | R04 V | NEW |
| IADM-07-US-006 | As Auditor (R28), I view policies and findings read-only. | No write path | R28 V | NEW |

### IADM-08 — Audit, Health & Operations (Wave B)
Access: R01 M; R02 M; R03 V own institute; Auditor (R28) V/AU/E read-only. One shared append-only audit store; no second store.

| ID | Story | Acceptance criteria | Access | Status |
|---|---|---|---|---|
| IADM-08-US-001 | As Institution Admin (R03), I search the audit trail for my institute by actor, record, action and time. | Reads the shared audit store; own institute only; no edit or delete path | R03 V | PARTIAL |
| IADM-08-US-002 | As Auditor (R28), I view and export audit evidence independently. | Read-only; export itself audited; no audit-admin keys | R28 V/AU/E | PARTIAL |
| IADM-08-US-003 | As Super Admin (R01) or System Admin (R02), I manage audit retention and archival. | Operates the service; never edits or deletes records | R01/R02 M | PARTIAL |
| IADM-08-US-004 | As Super Admin (R01) or System Admin (R02), I view service health (status, version, failures). | Gateway and each service; read from existing health endpoints | R01/R02 M | NEW |
| IADM-08-US-005 | As Institution Admin (R03), I view the operational status relevant to my institute. | Own institute only | R03 V | NEW |
| IADM-08-US-006 | As System Admin (R02), I run an audit self-check that no update or delete is possible on the audit log. | Report produced; failures raised as findings | R02 | NEW |

---

## 4. What CADM needs from IADM (contracts to publish early)

| Contract | Provided by | Needed by |
|---|---|---|
| First College Admin creation and handover record | IADM-01-US-003, US-004 | CADM-02 College User Administration |
| Effective configuration resolution (platform > institute > college, with lock and delegation flags) | IADM-02-US-005 | CADM-01 College Profile & Configuration |
| Role ceiling lookup | IADM-03-US-002 | CADM-03 College Roles & Access Admin |
| Shared audit read | IADM-08-US-001 | CADM-07 Audit & Compliance |
| Entitlement check | IADM-04-US-006 | All services |

---

## 5. Cross-cutting stories (apply to every IADM service)

| ID | Rule |
|---|---|
| IADM-CC-01 | Every endpoint checks a permission key (not a role name) and fails closed with a clear 403 |
| IADM-CC-02 | Every mutation writes an audit entry through the shared audit capture |
| IADM-CC-03 | Tenant isolation: identity from the signed gateway header, tenant scope on every query, database session as the non-privileged role so row-level security applies |
| IADM-CC-04 | Each service is registered in the gateway with the signed identity header; the next free port is taken from the project's port registry |
| IADM-CC-05 | Allowed and denied tests for each role in the story's access list, plus one live DEV test per service |
| IADM-CC-06 | Database changes are new numbered migrations with rollback scripts; existing migrations are never edited |

---

## 6. Assumptions (change any of them)

1. Frontend admin screens come after the APIs are stable.
2. Existing handlers in `institute-admin-service` are moved, not copied, into the new IADM services; the gateway route keeps working during the move.
3. IADM-04 keeps using the institute-lifecycle manage key until a dedicated key family is created; IADM-05 gets new keys.
4. The two-person rule for appointing an Institution Admin and the break-glass path are DEFERRED, with the accepted risk recorded.
5. Stories marked EXISTS-VERIFY are confirmed against the code before work starts; if the code is missing, the status changes to NEW.
