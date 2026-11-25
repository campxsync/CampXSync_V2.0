# IADM-04 S0 — Inspection Report (US-001 … US-004)

**Slice:** S0 (read-only)  
**Date:** 2026-11-25  
**Sources:** Blueprint `CampXSync_IADM-04_Detailed_Blueprint_V1_3.md` (outside repo); backlog `docs/user-stories/IADM-04_User_Stories.csv`  
**Scope rule followed:** No code, database, or Git changes in this slice. This report file is the only deliverable.  
**Environment:** DEV Supabase `azulbaxszsqxymqmlwfe` only (not queried for live row counts in S0; schema taken from migrations and code).

---

## Executive summary

| Finding | Verdict |
|---|---|
| IADM-01 onboarding | Stores a **plan reference string** only; **no payment/invoice**; institute becomes **ACTIVE when the first Institution Admin (R03) is appointed** — **before** any subscription. Conflicts with PENDING-until-paid unless IADM-04 adds an entitlement gate. |
| Existing billing | Lives in the **ADM-01 monolith** (`institute-admin-service` port **8081**) under `/api/v1/admin/billing/*`, mostly **in-memory**. Reusable **`plat.*` tables** + Postgres repositories exist. **No** active IADM-04 service, BFF, or subscription desks. |
| Shared audit | **Can** give a durable, awaited write via same-transaction `audit.log_event` (`DurableAuditWriter`). **Not a S2 blocker.** Live “audit disabled → no financial success” headed demo is still open (IADM-04 US-038). |
| IADM-02 billing/trial/grace/time-zone keys | **None found.** Existing keys are academic/session/IAM-delegation only. Missing keys must be registered later (listed below as PROPOSED). |
| Gateway convention today | IADM-01 `:8099` `/api/v1/iadm/01`; IADM-02 `:8100` `/api/v1/iadm/02`; IADM-03 `:8101` `/api/v1/iadm/03`. IADM-04 service/port/prefix **absent** — **PROPOSED** `:8102` + `/api/v1/iadm/04` (owner confirm). |
| One-current-subscription | Blueprint **PROPOSED** — implement in S1 as specified; easy to relax; flagged again in contract baseline. |

**Next authorized step after this report:** Pre-step P (IADM-03 US-033 + US-018), then S1. Owner checkpoint only after S1 headed demo.

---

## Story status (S0)

| Story | API-ready | Screen-ready | Demo-verified | Status |
|---|---|---|---|---|
| IADM-04-US-001 Inspect IADM-01 onboarding | N/A | N/A | N/A (report) | **Done** — this report §1 |
| IADM-04-US-002 Inspect billing code/schema/gate | N/A | N/A | N/A (report) | **Done** — this report §2 |
| IADM-04-US-003 Inspect audit / IADM-02 / gateway / usage | N/A | N/A | N/A (report) | **Done** — this report §3 + §5 |
| IADM-04-US-004 Contract baseline | N/A | N/A | N/A (document) | **Done** — this report §6 |

No tests were run in S0 (inspection only). No migrations, policy keys, or services were added.

---

## 1. US-001 — IADM-01 onboarding inspection

### 1.1 Plan selection and storage

| Layer | Evidence |
|---|---|
| Wizard UI | `frontend/src/app/(dashboard)/platform/institutes/new/onboarding-wizard.tsx` — dropdown `PLAN_REFERENCES`: ENTERPRISE, GROWTH, STANDARD, TRIAL_30_DAYS, CUSTOM. Comment: “Link/reference only — IADM-04 owns real plan limits.” |
| BFF | `frontend/src/app/api/platform/institutes/route.ts` — forwards `planReference` / `plan`. |
| Gateway | `/api/v1/iadm/01` → `http://localhost:8099/api/v1/iadm/01` |
| Domain | `TenantLifecycleService.registerInstitute` writes `core.tenants.profile` JSON: `{"type":"...","planReference":"...","contact":"..."}` with status **ONBOARDING**. |

**Not present:** FK to a plan catalogue; subscription row; call into any billing service.

### 1.2 Payment / invoice step

Wizard steps are only: **Details → Review → First Institution Admin → Done**.  
IADM-01 Java has **zero** invoice/payment/subscription writes beyond the plan-reference comment.

Legacy monolith billing (`/api/v1/admin/billing/*` on `:8081`) is **not** wired into this wizard. In-memory `createSubscription` sets status **ACTIVE immediately** when used — not an onboarding path.

### 1.3 Activation timing

Institute activation = `core.tenants.status` **ONBOARDING → ACTIVE**.

**Trigger:** `POST .../institutes/{id}/first-admin/onboarding` → after successful first R03 appoint in `resumeFirstAdminOnboarding`:

```sql
UPDATE core.tenants SET status = 'ACTIVE', onboarded_at = coalesce(onboarded_at, now()), ...
WHERE id = ? AND upper(status) = 'ONBOARDING'
```

**No payment check.** College create and similar gates require institute ACTIVE only. Session/`resolve-role` blocks **SUSPENDED** tenants, not unpaid subscriptions.

### 1.4 Hand-off points for IADM-04

| Direction | Observed seam | Recommended smallest hook (PROPOSED for S1) |
|---|---|---|
| IADM-01 → IADM-04 | After successful `registerInstitute` (planReference known; institute still ONBOARDING) | Call IADM-04 attach-plan / attach-trial with idempotency key |
| IADM-04 must **not** | Flip `core.tenants.status` | Aligns with story US-010: “IADM-04 never activates the institute” |
| Consumers → IADM-04 | None today | Entitlement-check contract (US-016); fail closed |
| GET institute | Omits `planReference` / profile today | Optional tiny IADM-01 read fix later — **TBC**, not required for S0 |

### 1.5 Conflicts with access gate (D17 / D26)

| # | Conflict | Severity |
|---|---|---|
| C1 | Institute becomes ACTIVE with **no subscription** | High — dual gate not enforced |
| C2 | No PENDING subscription path in onboarding | High |
| C3 | Paid plan codes get full lifecycle once R03 appointed | High |
| C4 | Legacy `plat.subscriptions` CHECK has **no PENDING** (`TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED`) | Medium — schema evolution needed |
| C5 | Legacy in-memory billing creates ACTIVE immediately | Medium — do not reuse that transition |
| C6 | TRIAL_30_DAYS is a string label only — no trial clock | Medium |

**Aligned (intent only):** UI/Java comments already defer entitlements to IADM-04. Enforcement is missing.

**Owner note (D17 resolved by inspection):** Keep institute activation in IADM-01 (R03 appoint). Enforce commercial access in IADM-04 entitlement gate: **activated ∧ (TRIAL ∨ ACTIVE)**. Do **not** delay `core.tenants.status = ACTIVE` behind payment (would be a larger IADM-01 behaviour change).

---

## 2. US-002 — Existing billing inventory (REUSE / MOVE / NEW)

### 2.1 Ports and prefixes **as found today**

| Service | Port | Gateway prefix |
|---|---|---|
| API Gateway | 8080 | — |
| ADM-01 Institute Admin (legacy monolith) | **8081** | `/api/v1/admin` (+ billing aliases) |
| IADM-01 Tenant Lifecycle | **8099** | `/api/v1/iadm/01` |
| IADM-02 Platform Config | **8100** | `/api/v1/iadm/02` |
| IADM-03 Identity & Access | **8101** | `/api/v1/iadm/03` |
| IADM-04 Billing | **none** | **none** |

Billing aliases still pointing at monolith `:8081`: `/api/v1/admin/billing/*`, `/v1/billing-accounts`.

**PROPOSED for S1 (owner confirm):** new module `iadm-04-billing-entitlements-service`, port **8102**, gateway prefix **`/api/v1/iadm/04`**. Do not assume the rolled-back 8099–8106 plan blindly; 8099–8101 are already taken by IADM-01…03.

### 2.2 Classification table

| Capability | Location | Class | Reason |
|---|---|---|---|
| HTTP billing routes (plans, subscriptions, invoices, transactions, usage) | `InstituteAdminController` `/api/v1/admin/billing/*` | **MOVE** | Own commercial surface; retire with 410 Gone **after** new route demo-verified + owner agreement (propose in final report; **do not execute in S0–S4 without agreement**) |
| In-memory domain billing methods | `InstituteAdminDomainService` | **MOVE** (rewrite) | Not Postgres-backed on the live HTTP path |
| Model shapes | `InstituteModels` CommercialPlan / Subscription / Invoice / … | **REUSE** (shape) | Dual: API vs repo models |
| Postgres repos | `PostgresSubscriptionPlanRepository`, `…Subscription…`, `…Invoice…`, `…PlanEntitlement…`, `…BillingGatewayTransaction…`, `…UsageMetric…` | **MOVE** into IADM-04 | Real JDBC against `plat.*` |
| Coarse billing permission gate | `checkBillingPermission` / `adm01.manage` + role allow-list including R02 plan write | **MOVE** (replace) | Contradicts IADM-04 R02 operational-only rule |
| `plat.subscription_plans` / `plan_entitlements` / `subscriptions` / `invoices` / `invoice_lines` / `gateway_transactions` / `usage_metrics` | `Scripts_V3.0/20_tables.sql` | **REUSE** + evolve | Gaps: no PENDING/GRACE/EXPIRED; price is `numeric` not minor units; no versioning/deprecate; FORCE RLS **TBC** on DEV |
| Baseline RLS | `Scripts_V3.0/60_rls.sql` — ENABLE; platform admin ALL on plans | **REUSE** + likely **NEW** FORCE | Live FORCE status **TBC** |
| IADM-01 `planReference` hook | `TenantLifecycleService` + wizard | **REUSE** | Smallest onboarding attach seam |
| Permission keys `plat.read` / `plat.write` | RLS + rbac CSV | **REUSE** until IADM-04 keys land | |
| Wave D `iadm04.*` keys + `plat.check_entitlement` | `docs/iadm/rollback/migrations/…` only | **NEW** when rebuilding | Rolled back; do not assume present on DEV |
| IADM-04 service / `/api/v1/iadm/04` / desks / BFF | Absent | **NEW** | |
| Dual access gate, offline SoD evidence, payment simulator + signed callback, plan-change requests, schedulers | Absent | **NEW** | |
| Platform / Institution subscription desks | Absent (`nav-config` has no billing) | **NEW** | |

### 2.3 Proposed retire plan (not executed)

Same pattern as IADM-02 configuration cutover (`gone://iadm02-cutover-…` → HTTP 410):

1. Ship IADM-04 under `/api/v1/iadm/04` → **PROPOSED** `:8102`.
2. Point BFF/UI at new prefix; demo-verify.
3. Owner agrees → mark monolith billing prefixes `gone://iadm04-cutover-billing`.
4. Keep `plat.*` tables; move write ownership to IADM-04 (do not copy tables).

### 2.4 Rolled-back remnants

- Deleted module listed in `docs/iadm/rollback/files_to_delete.txt` (`iadm-04-billing-entitlements-service/**`) — **not** in working tree.
- Wave D SQL under `docs/iadm/rollback/migrations/` — historical; rollback intended to drop unused keys/function.
- Live orphan rows of `iadm04.*` on DEV: **TBC** (not queried in S0).

---

## 3. US-003 — Shared audit, IADM-02 keys, gateway, usage

### 3.1 Shared audit — durable awaited write?

| Question | Evidence | Answer |
|---|---|---|
| Mechanism | IADM-03 `DurableAuditWriter.append` → `SELECT audit.log_event(...)` on the **same JDBC connection/transaction**; returns event UUID; throws `IADM03_AUDIT_UNAVAILABLE` (503) if connection null, no row, or SQL error | **Yes — durable within the committed transaction** |
| Fail-closed coded | `DurableAuditFailClosedTest` (null connection → 503); IADM-01/02 use same `audit.log_event` pattern | **Yes (unit)** |
| Await before external call | Pattern exists: persist domain + audit in TX, commit, then external side-effect (e.g. Auth invite outside TX in profiles). For payments: **must** commit invoice/intent + audit **before** provider adapter call | **Supported by design; wire explicitly in S3 US-026** |
| Headed audit-disabled demo | IADM-03 US-004 unit only. IADM-02 `US024_OUTAGE.json` demonstrates **config-service** outage fail-closed, **not** audit store disabled. IADM-04 CSV US-038 still owns the financial audit-outage film | **Not yet demonstrated for financial path** |

**S2 gate (kickoff):** Shared audit **can** give a durable awaited write → **S2 is not blocked** by audit. Still fail closed if audit unavailable when building invoices/payments.

> Numbering note: Kickoff cites “IADM-03 story US-022” for audit-failure. In the built IADM-03 backlog, **durable audit fail-closed is US-004**; **US-022 in S2 reports is idempotency**. Reported against the actual US-004 evidence.

### 3.2 IADM-02 policy keys (trial / grace / renewal / billing time zone)

**Keys found in IADM-02 catalogue / seeds (no billing family):**

| Key | Purpose |
|---|---|
| `attendance.min_pct` | Academic |
| `exam.grace_marks_max` | Exam grace marks (not subscription grace) |
| `fees.late_fee_grace_days` | Student late-fee grace (FIN-adjacent; not IADM-04) |
| `security.session_timeout_min` | Session |
| `iam.delegation.max_validity_days` | IAM delegation max days (confirmed exposed on consumer read) |

**Missing for IADM-04 (do not invent as verified — register in S1+ via IADM-02 mechanism; DEMO values only in seed):**

| PROPOSED key | Purpose |
|---|---|
| `billing.timezone` | Billing time zone / expiry-day boundary (D20) |
| `billing.trial.eligibility` | Trial eligibility rule (D18) |
| `billing.trial.duration_days` | Trial length |
| `billing.trial.conversion_mode` | When conversion invoice is raised |
| `billing.grace.days` | Subscription grace after expiry/renewal miss (D6) |
| `billing.renewal.reminder_days` | Reminder lead time |
| `billing.renewal.auto_invoice` | Whether renewal auto-raises invoice |

Exact names remain **PROPOSED** until registered; if unconfigured, expiry jobs **must not run** (fail closed).

### 3.3 Gateway / claims

See §5 (IADM-03 questions a–b) and §2.1.

### 3.4 Usage-producing sources (candidates)

| Source | Status | Note |
|---|---|---|
| Monolith `POST/GET /api/v1/admin/usage` + `plat.usage_metrics` | Exists | Prefer MOVE into IADM-04 ingest |
| IADM-06 Analytics & Usage | **Not authorized / not built** | Do not touch |
| STM / academic services | Possible future meters | **TBC** — no IADM-04 ingest contract yet |
| Student fee payment UI | Out of scope | FIN / STM — keep out |

---

## 4. IADM-03 questions required by kickoff (evidence)

### (a) How are permission keys registered and assigned today? How will IADM-04 keys be added?

**Register (catalogue):** numbered DEV migration inserts into `iam.permissions`  
Evidence: `database/migrations/20261124100000_iadm03_foundation.sql` (owner-approved S0 D3 `iam.*` keys).

**Assign to roles:**

1. `iam.role_template_permissions` (role_code × permission_code) for SYSTEM_ADMIN / INSTITUTION_ADMIN / COLLEGE_ADMIN / AUDITOR.
2. Propagate into live `iam.role_permissions` for matching `iam.roles`.
3. Super Admin (R01) enforced via `plat.platform_admins` + Java/`iam.is_platform_admin` (not only template rows).
4. Offline matrix for unit tests: `AccessPermissionMatrix` (permission keys only; no role-name endpoint auth).

**IADM-04 should add keys the same way:** migration → `iam.permissions` + template grants + `AccessPermissionMatrix`-style matrix in the new service + Java `checkPermission` / `iam.has_permission`. Key strings are **PROPOSED** in §6.4 until owner confirms names.

**Pre-step note:** Auditor (R28) evidence-read keys from D3 **already exist** (`iam.audit.view`, catalogue/profile/subject view). Close-out still marked US-033 `BLOCKED_BY_D3` — treat as **desk/wiring gap**, not missing key names, unless owner supplies additional keys.

### (b) Token claims and refresh; delay after revoke/change

| Layer | Behaviour | Evidence |
|---|---|---|
| JWT | Gateway validates signature/`sub`/`exp`; does **not** trust client `X-User-*` | `SupabaseJwtValidator` / S0 verification report |
| Authoritative identity | After JWT OK, `JdbcIdentityResolver` reads `plat.platform_admins` **or** `iam.user_profiles` + assignments | Gateway |
| Permission check | Service TX: `UserSecurityContext.checkPermission` → `iam.has_permission(?)` | Immediate for new DB sessions |
| Gateway identity cache | TTL **5 minutes** (`CACHE_TTL_MS = 5 * 60 * 1000L`) | `JdbcIdentityResolver.java` |
| Invalidate after grant/revoke | `invalidate(userId)` / `invalidateAll()` **exist**; **no callers** outside the class itself (repo-wide grep) | Residual gateway header lag **0–300 s** |
| JWT role metadata | Not rewritten by IAM grant code found | UI routing may stay stale until token refresh |

**Documented SLA (IADM-03 D13, owner-approved):** ≤ **5 minutes** gateway header lag unless invalidate is wired. DB permission checks are immediate. For IADM-04 entitlement consumers: follow the same pattern (not a slow per-request deep path); **measure** subscription-state reflection delay in S1 and open **D15** if PENDING does not mean zero access immediately enough.

### (c) Second Super Admin (R01) demo account

| Evidence | Result |
|---|---|
| IADM-02 D5 | Two separate Super Admin test users required for draft≠approve |
| Seed SQL | `docs/iadm/iadm-02/evidence/SEED_PLATFORM_ADMINS.sql` inserts second SUPER_ADMIN row (IADM-02 Super Admin B) |
| Demo persona | `platformadmin2.campx@gmail.com` used in IADM-02/IADM-03 headed demos (R01-B approve) |
| IADM-03 guards | Catalogue-change / access-request / config decide paths deny self-approve; require distinct actor |

**Verdict:** Yes — second R01 can be created with existing seed/persona convention; guards **allow and require** two for SoD workflows. Map to blueprint fixtures `USR-R01A` / `USR-R01B` in S1 demo pack (emails via `.env.audit` / seed JSON — never print secrets).

### (d) Approval pattern (request → approve) for plan-change reuse?

| Pattern | Exists? | Evidence |
|---|---|---|
| Access request submit → decide | **Yes** | `Iadm03Controller` `POST /access-requests`, `POST /access-requests/{id}/decide`; `AccessRequestGovernanceService`; College Admin (R04) may **submit** only (`iam.request.submit`) |
| R03 grant-change decide with self-approve deny | **Yes** | `GrantGovernanceService.decideR03GrantChange` + `iam.grant.r03.decide` |
| IADM-02 draft → decide → publish (different R01) | **Yes** | Config drafts |
| Catalogue mapping propose → approve (different R01) | **Yes** | S3b headed evidence |

**Reuse for plan-change:** **Semantic reuse** of request→approve + self-approve deny + distinct R01. **Do not** overload `iam.access_requests` with billing plan changes (wrong domain). **PROPOSED:** IADM-04-owned plan-change request table/API with the same governance shape (requester Institution Admin; approver Super Admin; apply only after approval).

### (e) Shared audit awaited before external call; audit-failure demonstrated?

Answered in §3.1: **mechanism yes**; **unit fail-closed yes**; **headed financial audit-outage not yet filmed**. Not an S2 design blocker.

---

## 5. Pre-step P readiness (IADM-03 US-033 / US-018) — inspection only

| Story | Approved D3 keys found? | Gap |
|---|---|---|
| US-033 Auditor (R28) read-only desk | **Yes** — `iam.audit.view`, `iam.catalog.roles.view`, `iam.catalog.permissions.view`, `iam.access.subject.view`, `iam.profile.view` | Build read-only desk + headed mutation denial; do **not** invent extra keys |
| US-018 System Admin (R02) limited to platform technical identities | **Yes** — `iam.identity.technical.assign` (+ read keys). S2 marked partially completed (API path; weak headed film) | List remaining allow/deny cases; wire + allowed/denied tests; headed evidence |

If during pre-step any required key is missing from DEV catalogue, **stop inventing**, record the gap, skip that item, continue to IADM-04 S0→S1 (kickoff: not a blocker). S0 finds the approved names present in migration + matrix.

**Do not** flip IADM-03 US-040 or claim “RBAC v3.0 Compliant”.

---

## 6. US-004 — Contract baseline (for owner review)

### 6.1 Subscription state machine (PROPOSED transitions)

States: **TRIAL**, **PENDING**, **ACTIVE**, **GRACE** (policy), **EXPIRED**, **SUSPENDED**, **CANCELLED**.  
Legacy DB also has **PAST_DUE** — map or replace carefully in migration (**TBC**).

| From | To | Trigger |
|---|---|---|
| (none) | TRIAL | Eligible onboarding attach (no payment) |
| (none) | PENDING | Non-trial attach / invoice intent |
| TRIAL | PENDING | Trial conversion → raise invoice (same subscription) |
| PENDING | ACTIVE | Verified online payment **or** approved offline reconciliation |
| ACTIVE | GRACE / EXPIRED / SUSPENDED / CANCELLED | Policy jobs / Super Admin actions under IADM-02 policy |
| GRACE | ACTIVE / EXPIRED / SUSPENDED | Policy |
| Any current | (reject) | Second TRIAL/PENDING/ACTIVE attach while one exists — **PROPOSED one-current rule (D19)** |

**Payment initiation alone never activates.**

### 6.2 Dual access gate (D26) — binding

Grant entitlement **only if**:

1. IADM-01 institute status is **activated** (`core.tenants.status = ACTIVE`), **and**
2. Subscription state is **TRIAL** or **ACTIVE**.

**PENDING → always zero entitlements / no access**, even if institute is ACTIVE.  
Unknown / stale / unavailable → **deny** (fail closed).  
IADM-04 **never** activates or deactivates an institute.

### 6.3 Entitlement-check contract (PROPOSED shape)

| Item | PROPOSED |
|---|---|
| Caller | Authenticated service identity (not College Admin human billing login) |
| Request | `instituteId` (from trusted claims/context — **never** client-trusted alone), `featureKey` / entitlement key, optional `asOf` |
| Response | `{ allowed: boolean, reasonCode, subscriptionState?, limits? }` — no billing admin payload |
| Cache | Follow IADM-03 pattern: authoritative DB check; optional short cache; measure revoke/state-change lag in S1 (D15) |
| PENDING | `allowed=false`, no partial/cached entitlements |

Exact path under `/api/v1/iadm/04/...` — **TBC** at S1 build.

### 6.4 Proposed permission-key map (PROPOSED — not verified live)

| Capability | Actor | PROPOSED key |
|---|---|---|
| View plan catalogue | Super Admin; System Admin view | `iadm04.plans.view` |
| Manage plans/pricing | Super Admin only | `iadm04.plans.manage` |
| Map plan entitlements | Super Admin | `iadm04.entitlements.map` |
| View all subscriptions | Super Admin; System Admin operational view | `iadm04.subscriptions.view` |
| View own subscription | Institution Admin | `iadm04.subscriptions.view.own` |
| Request plan change | Institution Admin | `iadm04.plan_change.request` |
| Approve plan change | Super Admin | `iadm04.plan_change.decide` |
| Attach at onboarding | Service identity | `iadm04.subscriptions.attach` |
| Entitlement check | Service identity | `iadm04.entitlements.check` |
| Invoice generate/void | Super Admin | `iadm04.invoices.manage` |
| Invoice read own | Institution Admin | `iadm04.invoices.view.own` |
| Offline evidence record | Super Admin A | `iadm04.payments.offline.record` |
| Offline reconcile | Super Admin B (≠ A) | `iadm04.payments.offline.reconcile` |
| Online payment initiate | Super Admin | `iadm04.payments.online.initiate` |
| Provider callback | Machine identity | `iadm04.payments.callback` |
| Retry failed callback | System Admin | `iadm04.payments.callback.retry` |
| Usage ingest | Service identity | `iadm04.usage.record` |
| Reports | Super Admin / own / Auditor | `iadm04.reports.view` |
| Auditor billing evidence | Auditor | `iadm04.audit.view` |
| Scheduled jobs | Service identity | `iadm04.jobs.run` |
| College Admin any IADM-04 | — | **Denied** (no keys) |

### 6.5 Money / invoice rules (binding where decided)

- Amounts = **integer minor units** + ISO 4217 currency.
- No tax/GST, no invoice-numbering scheme, no proration, no refunds/credits/chargebacks, no overage charges, no throttling (open decisions).
- Stable **internal** invoice reference only.
- Pricing snapshot stored with invoice.
- Student fees stay in FIN; no invoice-summary sync to FIN.

### 6.6 Offline payment SoD (DECIDED)

- Account A records immutable evidence; account B (different Super Admin) reconciles.
- Same account denied; if only one Super Admin exists → **block**; never substitute another role.

### 6.7 Payment provider

- Adapter interface + **DEV simulator only** in S3.
- No real provider, credentials, or external network calls.
- Webhook: separate machine ingress; signature, time window, replay, duplicate protection.

### 6.8 IADM-01 contract changes needed (smallest possible)

1. After `registerInstitute` success: call IADM-04 attach (trial or PENDING) with idempotency key — **or** BFF orchestrates the second call in the same wizard step.
2. Do **not** change when institute becomes ACTIVE (keep R03 appoint).
3. Optionally surface `planReference` on GET institute (nice-to-have; **TBC**).
4. Later consumers (college create, desks) should consult entitlement-check where product requires — **not** all at once in S1.

### 6.9 Flags for owner

| ID | Item | Status |
|---|---|---|
| D19 | One current subscription across TRIAL/PENDING/ACTIVE | **PROPOSED** — implement in S1; easy to change |
| D12 | Port **8102** + prefix `/api/v1/iadm/04` | **PROPOSED** |
| D20 / D6 / D18 | Billing time zone, grace, trial values | Keys missing; register via IADM-02; fail closed if absent |
| D15 | Entitlement/subscription cache lag vs “PENDING = zero access immediately” | Measure in S1 |
| D3 (IADM-04) | Payment provider | Simulator only until chosen |
| D11 | Evolve `plat.*` vs new schema objects | Prefer REUSE `plat.*` + migrations for PENDING/minor-units/history tables as needed |
| D17 | PENDING-until-paid vs IADM-01 activation | **Inspection complete** — recommend dual gate, not delay tenant ACTIVE |

---

## 7. Assumptions, deviations, open items

### Assumptions (safe defaults inside rules)

- Prefer evolving **`plat.*`** over inventing a parallel `billing` schema name.
- Institute ACTIVE stays IADM-01-owned; commercial access is the entitlement gate.
- Plan-change uses a **new** IADM-04 request entity that **mirrors** IADM-03 request→approve semantics.
- IADM-04 listen port **8102** unless owner assigns otherwise.

### Deviations from blueprint wording

- Blueprint Part 14 story IDs (US-001 plan catalogue …) differ from CSV story IDs (US-001 inspection …). **CSV + kickoff backlog win** for implementation order.
- Kickoff “IADM-03 US-022” audit-failure maps to built **US-004** evidence.

### Open items (continue; do not stop)

- Live DEV FORCE RLS / orphan `iadm04.*` rows — verify at first migration apply.
- Exact entitlement cache SLA (D15) — measure in S1.
- Whether GET institute should return `planReference` — TBC.
- Final key string approval for `iadm04.*` — PROPOSED until owner nods (or accept PROPOSED names at S1 start per kickoff “safest option”).

### Real blockers

**None for S0 completion.** Shared audit supports durable awaited writes → financial S2 may proceed after S1 owner checkpoint.

---

## 8. Files inspected (non-exhaustive index)

- Blueprint: `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-04_Detailed_Blueprint_V1_3.md`
- CSV: `docs/user-stories/IADM-04_User_Stories.csv`
- IADM-01: `TenantLifecycleService.java`, onboarding wizard, gateway routes
- Billing: `InstituteAdminController.java`, `InstituteAdminDomainService.java`, `plat.*` in `Scripts_V3.0/20_tables.sql` + `60_rls.sql`, Postgres billing repositories
- Audit: `DurableAuditWriter.java`, `DurableAuditFailClosedTest.java`
- IADM-03: `AccessPermissionMatrix.java`, `S0_DECISION_PACK.md`, foundation migration, `Iadm03Controller` access-requests, `JdbcIdentityResolver.java`
- IADM-02: setting definitions / seeds, `SEED_PLATFORM_ADMINS.sql`, US-024 outage evidence
- Rollback: `docs/iadm/rollback/**`
- Stack: `scripts/dev/start_stack.ps1`

---

## 9. Slice close

| Item | Value |
|---|---|
| Code / DB / Git changes | **None** (report only) |
| Policy keys added | **None** |
| Tests run | **None** (inspection) |
| Commit | **None** (S0 forbids Git changes) |
| Ready for | Pre-step P → S1 (stop for owner headed review after S1 only) |

**End of IADM-04 S0 inspection report.**
