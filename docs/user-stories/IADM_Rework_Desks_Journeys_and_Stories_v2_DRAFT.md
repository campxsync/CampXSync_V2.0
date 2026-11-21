# IADM Rework — Desks, Journeys and User Stories (v2 DRAFT)

**Date:** 2026-11-21 · **Owner:** Niranjan V.G.
**Purpose:** Replace "API-complete" with "an admin can actually do the work". This document keeps the backend that already exists, defines the two admin desks that sit on top of it, and sets a stricter definition of done.

---

## 1. What went wrong (plain language)

1. **The stories were written as system behaviour, not as admin work.** Each story said what an API does ("As R03, I set role ceilings…") but none said what screen the admin opens, what they click, what they see afterwards, or what data is there on day one. The first draft also assumed screens would come later; that assumption hid the problem.
2. **"Done" meant "an endpoint and a test exist".** The reports mark stories completed while data sources are empty (service health has no rows), searches return nothing (audit search), counters look wrong (platform users = 0), and some screens only list things with no way to start an action (Identity & Access shows only empty lists).
3. **Only one of the two admin audiences has a desk.** The recording shows the Platform Operator desk (Super Admin R01 / System Admin R02). The Institution Admin (R03) desk, where an institute's own admin does their daily work, does not exist yet.
4. **Several "stories" are not user stories.** Entries such as "As any service, I resolve the effective setting" are technical contracts for CADM. They stay, but they are tracked separately and are never counted as admin work.

## 2. What the recording showed (CONFIRMED from the video frames)

| Screen | What an admin sees | Problem |
|---|---|---|
| Platform dashboard | 4 institutes, 4 active, 0 suspended; "Total platform users: 0"; 38 role templates; buttons "Onboard New Institute", "Customize Dashboard" | Users = 0 looks wrong while DEV has users; no pending-approval counters |
| Identity & Access | "0 pending quorum / 0 break-glass / 0 sessions" and three empty lists | No way to start a request; quorum text says "COLLEGE_ADMIN grant/revoke" while the approved rule is appointing or removing an Institution Admin (R03) |
| Ops & Audit Search | Service health: "No health rows"; self-check passes; audit search: "No audit events matched" | Nothing writes health rows; search finds nothing although many audited changes were made this week (possible mismatch between the audit table searched and the table the triggers write to — INFERRED, to verify) |
| Navigation | Dashboard, Institutes & Tenancies, Onboard Institute, Identity & Access, Ops & Audit Search, Account Provisioning, Platform Audit | Not opened in the recording; "Account Provisioning" (a Student Module job) and "Platform Audit" duplicate other areas |

Not seen: Institutes & Tenancies, Onboard Institute, Account Provisioning, Platform Audit pages; any Institution Admin (R03) screen.

## 3. New definition of done (per story, applies to every wave from now on)

A story is **Demo-verified** only when all of these are true:
1. **API ready:** endpoint, permission-key check, audit entry, tests (allowed and denied).
2. **Screen ready:** a screen where the admin can perform the action (button, form, validation message, success and error states) — not only view it.
3. **Data ready:** demo data exists so no screen is empty (see §7), and every number on screen comes from a real source.
4. **Persona demo:** a checklist step "log in as persona X, do A, see B" passes, and the denied persona sees the block.
5. **Audit visible:** the action appears in Audit search for the right role.
6. **Evidence:** the wave report links a demo checklist result (screens and steps), not just test counts.

Status words: **API-ready**, **Screen-ready**, **Demo-verified**. Only Demo-verified counts as done.

## 4. The two desks

| Desk | Who | Purpose |
|---|---|---|
| **Platform Operator Desk** | Super Admin (R01), System Admin (R02) | Run the platform: institutes, appointments, approvals, platform configuration and minimums, billing, usage, operations and audit |
| **Institution Admin Desk** | Institution Admin (R03) | Run one institute: colleges, staff roles, delegations and reviews, institute configuration, subscription view, privacy, security, audit view |
| Read-only views | Auditor (R28); College Admin (R04) | Auditor: read-only audit, config history, access changes, billing, privacy, security. College Admin: effective configuration (read-only) and request forms (built with CADM) |

## 5. A day in the life (journeys the screens must support)

| # | Journey | Steps |
|---|---|---|
| J1 | Onboard an institute | Platform Operator registers institute → chooses plan → starts appointment of Institution Admin → second platform admin approves → Institution Admin receives access and logs in |
| J2 | First day of an Institution Admin | Opens home → sees institute status, plan, pending items → registers first college → appoints first College Admin → completes handover |
| J3 | Staff and access | Institution Admin assigns a role (only roles the grant table allows) → self-grant blocked with a clear message → sets role ceilings → approves a College Admin's request |
| J4 | Access hygiene | Creates a time-boxed delegation → starts an access review → keeps or revokes each holder → completion recorded |
| J5 | Configuration | Platform Operator defines a setting and locks it → Institution Admin sets an institute value → delegates one setting to a college → a change is rolled back from history |
| J6 | Money | Platform Operator publishes a plan, assigns a subscription, issues an invoice → Institution Admin views plan, usage vs limits, requests a plan change → Platform Operator decides |
| J7 | Privacy and security | Institution Admin sets retention (floor shown), publishes a privacy notice version, handles a data-subject request, places a legal hold, records a security finding |
| J8 | Oversight | Platform Operator watches service health and approvals; Auditor searches audit and exports evidence; break-glass is requested, approved and closed |

## 6. Desk stories

"Uses" lists the backend stories already built. Status column starts at **API-ready** where the backend exists.

### 6.1 Platform Operator Desk (R01 / R02)

| ID | What the admin does on screen | Uses | Done when (demo) |
|---|---|---|---|
| IADM-PD-01 | Dashboard: institutes by status, colleges, users, pending approvals (appointment quorum, break-glass, lifecycle requests, plan-change requests), recent activity | IADM-01, 03, 04, 08 | Numbers match the database; clicking a counter opens the list; no zero that is actually a bug |
| IADM-PD-02 | Institutes & Tenancies: searchable, filterable list (status, plan); open an institute | IADM-01-US-005 | Filter by status works; row opens detail |
| IADM-PD-03 | Institute detail with tabs: Profile, Colleges, Administrators, Subscription, Posture, Audit trail; actions: suspend, reactivate (reason required) | IADM-01-US-005/006/008, 04, 07 | Suspending blocks the institute's login; reactivating restores; both visible in audit |
| IADM-PD-04 | Onboard Institute wizard: details → plan → appoint Institution Admin (choose existing login or invite by email) → review → confirm | IADM-01-US-001/002 | New institute appears in the list; appointment is pending until quorum if required |
| IADM-PD-05 | Lifecycle requests inbox: see an institute's request to suspend, decommission or export; approve or reject with a reason | IADM-01-US-007 | Request created by Institution Admin shows here; decision visible to both |
| IADM-PD-06 | Appoint or remove an Institution Admin with two-person approval: start request, second platform admin approves, history | IADM-03-US-009 | Same person cannot approve; label says "Institution Admin (R03)", not College Admin; outcome appears in audit |
| IADM-PD-07 | Break-glass: start a request, approve, see active sessions with expiry, close | IADM-03-US-008 | Full cycle works from the screen with reason and expiry |
| IADM-PD-08 | Platform configuration: catalogue list and edit (type, default, allowed scopes, lock, delegable), version history, rollback | IADM-02-US-001/002/007 | Create → lock → see institute blocked → rollback restores value |
| IADM-PD-09 | Platform minimums: security and retention floors | IADM-07-US-004, IADM-05-US-001 | Institute policy below the floor is rejected with a clear message |
| IADM-PD-10 | Billing: plans (create, publish, retire), subscriptions (assign, change state), invoices (issue, reconcile), plan-change requests inbox | IADM-04 | Full J6 works on screen |
| IADM-PD-11 | Usage analytics: by institute and metric, export | IADM-06-US-001/003 | Numbers come from real metering rows |
| IADM-PD-12 | Operations: live service health (real heartbeat rows), audit self-check, audit search with actor, action, institute and date filters, result table | IADM-08 | Health shows every service; searching a known action finds it |
| IADM-PD-13 | Governance rollup: open security findings and data-subject requests across institutes | IADM-05, 07 | Counts match the institute desks |
| IADM-PD-14 | Navigation clean-up: merge "Platform Audit" into Ops & Audit; decide whether "Account Provisioning" belongs here | — | No duplicate menu items; decision recorded |

### 6.2 Institution Admin Desk (R03)

| ID | What the admin does on screen | Uses | Done when (demo) |
|---|---|---|---|
| IADM-ID-01 | Home: institute status, plan and renewal, number of colleges and role holders, pending items (requests, reviews due, data-subject requests, findings) | IADM-01, 03, 04, 05, 07 | Counters open the right lists |
| IADM-ID-02 | Institute profile: view and edit allowed fields; request suspension, decommission or export | IADM-01-US-005/007 | Request reaches the Platform Operator inbox |
| IADM-ID-03 | Colleges: list with status and handover state; register college; appoint first College Admin; complete handover | IADM-01-US-003/004 | After handover the Institution Admin can no longer manage that college's users |
| IADM-ID-04 | Staff and roles: list role holders; assign or remove roles the grant table allows; clear message when blocked | IADM-03-US-001/003 | Self-grant and above-own grants are refused with a readable reason |
| IADM-ID-05 | Role ceilings: set per college what a College Admin may assign | IADM-03-US-002 | A College Admin request above the ceiling is refused |
| IADM-ID-06 | Requests inbox: approve or reject College Admin requests; approval applies the change | IADM-03-US-004 | Approving actually changes the role or ceiling |
| IADM-ID-07 | Delegations: create (role, scope, from, to), list, revoke | IADM-03-US-005 | Expired delegation stops working |
| IADM-ID-08 | Access reviews: start, list holders, keep or revoke each, complete | IADM-03-US-006 | Revoke takes effect immediately |
| IADM-ID-09 | Configuration: settings with source and lock indicators; set institute values (allowed settings); delegate to colleges; history and rollback | IADM-02 | Locked setting cannot be changed; delegated setting appears for the college |
| IADM-ID-10 | Subscription: plan, entitlements vs usage, invoices; request plan change | IADM-04-US-003/004 | Request appears in the Platform Operator inbox |
| IADM-ID-11 | Data governance: retention rules (floor shown), privacy notice versions, data-subject request queue, legal holds | IADM-05 | Legal hold blocks erasure; request moves through statuses |
| IADM-ID-12 | Security: institute policies (floor shown), posture, findings (create, assign, close) | IADM-07 | Policy below the floor refused; finding lifecycle works |
| IADM-ID-13 | Usage: aggregates by college | IADM-06-US-002 | Real by-college numbers |
| IADM-ID-14 | Audit: search own institute's audit trail | IADM-08-US-001 | Own institute only; actions from this desk appear |
| IADM-ID-15 | Break-glass request from this desk | IADM-03-US-008 | Request visible to Platform Operator |

### 6.3 Auditor view (R28)
IADM-AU-01 — one read-only area listing audit, configuration history, access changes, billing, privacy and security records; every write control absent and every write call refused.

## 7. Demo data and personas (so no screen is empty)

- One demo institute with two colleges, one Institution Admin, two College Admins, a Principal, a Dean, a Registrar and a few staff.
- Pending items: one lifecycle request, one plan-change request, one access request, one review due, one open data-subject request, one legal hold, two security findings.
- Metering rows for usage, heartbeat rows for every IADM service, and recent audited actions from each persona.
- Personas: Platform Operator (R01), second Platform Operator (R02) for quorum, Institution Admin (R03), College Admin (R04), Auditor (R28), Student (R25) for denial checks. No credentials in repository files.

## 8. Defects seen in the recording (fix first)

| # | Defect | Check |
|---|---|---|
| D1 | "Total platform users" shows 0 | Query source; match with real count |
| D2 | Service health empty | Real heartbeat writer for IADM-01…08 and gateway |
| D3 | Audit search finds nothing | Confirm the table searched is the one the audit triggers fill; search a known change |
| D4 | Quorum text says COLLEGE_ADMIN | Confirm which role the quorum actually grants and fix the code or the label |
| D5 | Lists with no way to start an action | Add create/start controls (PD-06, PD-07) |
| D6 | Duplicate/unclear navigation | PD-14 |
| D7 | Counts of "completed" stories are not true | Re-baseline all statuses with the three-level model |

## 9. Corrections to earlier backend stories

- IADM-08-US-004 "view service health" is API-ready only until health rows are written.
- IADM-05-US-005 "instruct domain services" is not done while execute only changes a status.
- IADM-06-US-004 "metering feed" needs real producers, not only an ingest endpoint.
- System-actor stories (IADM-02-US-005, IADM-03-US-002 lookup, IADM-04-US-006, IADM-08-US-001 as a service call) are reclassified as contracts for CADM (IADM-C-01…C-05); they remain proven and are not counted as admin work.
- Added missing capabilities: college list/edit/deactivate request, institute profile edit, institute-staff invitation (decision needed: who creates the login for staff the Institution Admin assigns a role to), platform dashboard counters.

## 10. Order of work (vertical slices; each slice = database + API + screen + demo data + demo checklist)

| Slice | Stories | Journey |
|---|---|---|
| 1 | PD-01…PD-06, defects D1, D4, D5 | J1 (onboard an institute and appoint its Institution Admin) |
| 2 | ID-01…ID-06 | J2, J3 (the Institution Admin's first day, staff and roles) |
| 3 | PD-08, PD-10, PD-11, ID-09, ID-10, ID-13 | J5, J6 |
| 4 | PD-09, PD-12, PD-13, PD-14, ID-11, ID-12, ID-14, AU-01 | J7, J8 |
| 5 | PD-07, ID-07, ID-08, ID-15 | J4 and break-glass |

The owner confirms the pattern after Slice 1 (one stop), then slices 2–5 continue without stopping.
