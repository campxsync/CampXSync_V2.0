# CampXSync — Java Backend Integration Wave Implementation Plan

**Target branch:** `feature/java-backend-integration`  
**Revision:** 2026-10-21

> **Merge note:** The original repository copy of this plan was not included with the uploaded proposal. This consolidated version uses the supplied Waves 4–11 proposal and recorded checkpoint. Before replacing the repository file, preserve any unique original Wave 0–3 wording, history, and operating rules that are absent here.

## 1. Governing principles

1. Execute one wave at a time and stop after the current task.
2. Preserve branch, HEAD, modified files and untracked files. No reset, clean, stash, branch switch, commit or push without explicit user authorization.
3. Production databases and project references are strictly off-limits.
4. Every migration, grant, RLS change, seed, provisioning step or other DB write requires verification of the exact non-production target and explicit approval for that specific step.
5. Compilation and unit tests are not proof of live DB, RLS or browser-to-database E2E behavior.
6. Preserve Waves 0–3 work. Wave 1 was accepted with explicit limitations; Waves 2–3 were reported complete, with live integrations still unverified where noted in their reports.
7. Reuse existing domain ownership and shared infrastructure; do not create duplicate sources of truth.
8. Governing control files: this plan, `docs/project-control/PROGRESS.md`, `prompts/NEXT_ANTIGRAVITY_TASK.md`, `.agent/campxsync-wave-control.md`, and `docs/reports/WAVE_COMPLETION_REPORT_TEMPLATE.md`.

## 2. Recorded checkpoint

- Branch: `feature/java-backend-integration`
- Last recorded committed Wave 0 baseline: `105122b6b5b17d3eadc70e7e8094008facb4285a`
- Waves 1–3 have uncommitted work; preserve it.
- Wave 1: Shared Audit & Outbox Foundation; reported accepted with limitations around live PostgreSQL/RLS verification and deferred background relay.
- Wave 2: Preferences; reported complete; live DB integration and real Intelligence Layer endpoint remain pending/unverified.
- Wave 3: Academic Departments and Programs; reported complete; live integration remains unverified.
- Next: Wave 4 — Student Domain Foundation & Roster.
- These are reported results, not independent re-verification of the current repository.

## 3. Gates

### G1 — Least-privilege DB role (before Wave 4 persistence)
Business DB access must use a dedicated application login role without `BYPASSRLS`, with transaction-scoped tenant/user context. A privileged role must not be used to claim tenant isolation.
- If closed, record exact evidence and verify it applies to the current non-production target.
- If open, persistence and DB writes are blocked; safe design, code without DB connections, unit tests and isolated tests may continue.
- Role/grant/RLS scripts require review and explicit approval before execution.
- Until verified, no wave may claim tenant isolation is verified.

### G2 — Isolated PostgreSQL tests in CI (by end of Wave 5)
The integration suite bootstrapped from existing migrations should run automatically on every push to the feature branch. Do not push without user authorization. The development Supabase project is for separately approved verification only.

### G3 — JDK/framework assessment (before Wave 11 exit)
Assess supported JDK targets, effort, compatibility risks and recommendation. Java 8 remains in place; no upgrade without separate approval.

## 4. Cross-cutting requirements, Waves 4–11

1. **Thin E2E every wave:** verify `UI/Frontend → shared versioned API Gateway → Java domain service → PostgreSQL` where the safe environment supports it. Include persisted read-back and cross-tenant denial. State test tier: mock, in-memory, isolated DB, approved staging DB or browser. If the full path is unavailable, test the strongest safe tier and mark missing evidence pending.
2. **Frontend cutover per wave:** migrate consumers off direct DB access as each API arrives, or record exact remaining consumers and why. Wave 10 retires eligible remaining access; it does not begin all migrations.
3. **Intelligence Layer:** reuse Wave 1 event/outbox envelope (correlation ID, tenant context, event time, entity IDs, schema version). Register event types, owner and privacy classification. Test tenant context, idempotency, failure and retry. A test double is not live integration; mark real integration pending.
4. **Events do not replace transactions:** PostgreSQL domain state is authoritative. Events use the transaction-aware outbox; command success is distinct from downstream delivery.
5. **No unapproved DB changes:** exact non-production target verification and explicit approval for every write step.
6. Test tenant, role and department scope as applicable. If G1 is open, do not claim RLS verification.
7. Do not expand scope silently. Preserve existing work and stop after the current wave.

## 5. Wave 4 — Student domain and roster

**Goal:** First-class student domain with stable identity/lifecycle and references for downstream modules.

**Preconditions:** Record G1 evidence; if open, no persistence/DB writes. Obtain written approval of the student model before implementing that model. Verify Wave 3 structures from existing evidence.

**Design checkpoint:** student/person linkage; institution-scoped admission/enrolment/roll identifiers; lifecycle states/transitions; relationships to program, department, batch, academic year/term; ownership boundaries across Student, IAM, Academics, Exams, Finance, LMS, Documents and Workflow.

**Scope:** student service sole writer of canonical student data; roster list/search with cursor pagination; enrol/transfer/withdraw only where approved and supported by existing schema; idempotent bulk import with row validation if within approved scope; role/department authorization; trusted identity; data minimization; versioned events through Wave 1 outbox.

Reuse `stu.students`, `core.persons`, `stu.guardianships`, IAM links/role assignments, shared audit/outbox, document and workflow capabilities where current code/schema supports them. Do not duplicate identity, credentials, guardians, document registry or workflow. Verify existing migrations before proposing new tables; do not assume admissions or term-registration tables exist.

**Tests:** duplicate identifiers within/across tenants; invalid lifecycle transitions; out-of-tenant/out-of-scope references; import idempotency if implemented; persisted read-back where approved DB is available; cross-tenant and role denial, with test tier stated.

**Exit:** approved model/ownership; roster/lifecycle implemented within approved scope; stable student/batch references documented; events registered/tested at available tier; report and checkpoint updated; unverified integration remains pending.

## 6. Wave 5 — Timetable

Document conflict rules before implementation: faculty, batch, and room double-booking if rooms are in scope. Persist entries with validated tenant/program/batch/faculty/course references; define effective dates/versioning and IST convention. Enforce conflicts atomically, using service-level locking or separately approved DB constraints. Represent faculty assignment/substitution; publish stable slot IDs and slot-changed event.

**Tests:** every conflict rule including concurrency; invalid references; department scope; tenant separation; read-back at approved test tier.  
**Exit:** gateway-to-DB path and conflict rules verified at available tier; slot contract documented; G2 closed; report/checkpoint updated.

## 7. Wave 6 — Attendance

Sessions derive from timetable slots. Define marking window, assigned faculty/substitute authority, attendance statuses, roster snapshot and behavior when student lifecycle changes mid-term. Implement idempotent single/bulk marking, reject duplicates/invalid roster; HoD/admin oversight with scope and cursor pagination. Register session-opened and attendance-marked events.

**Tests:** non-roster students, duplicates, outside-window/assignment marking, department scope, read-back, cross-tenant/unauthorized denial.  
**Exit:** marking/oversight verified at strongest available tier; evidence and pending gaps recorded.

## 8. Wave 7 — Attendance corrections

Implement request → approval → apply; requester cannot self-approve; agree correction time limit. Preserve append-only history (original/corrected values, actor, reason, timestamps); concurrency/idempotency; shared outbox correction event.

**Tests:** authority matrix, self-approval denial, concurrent apply, replay, history integrity, tenant isolation, read-back of original and corrected values.  
**Exit:** correction and audit trail verified at available tier; report/checkpoint updated.

## 9. Wave 8 — Dashboards

Document metric definitions first: present counts, denominators, corrections, withdrawn students and date ranges. Define aggregates per role (platform admin, college admin, HoD, faculty; student view only if approved). Serve via gateway APIs, not ad hoc frontend queries. Any materialized view/index/DB object requires separate approval. Agree latency budget and measure p50/p95. Dashboards are not a second source of truth.

**Tests:** reconcile values to fixtures including corrections; role/tenant leakage tests; latency measurements; at least two tenants in a safe test environment.  
**Exit:** reconciled counts, scope tests and latency evidence or recorded deviations.

## 10. Wave 9 — College onboarding

Inspect provisioning contract; implement idempotent onboarding and initial admin assignment; platform-admin-only authority; prevent tenant/role escalation. Supabase Auth privileged credentials must be narrowly scoped and server-side only. Define compensation/rollback; register tenant-provisioned event. Writes only in approved non-production environment with exact-step approval.

**Tests:** repeated onboarding, partial-failure recovery, escalation attempts, third test tenant and isolation tests where safe.  
**Exit:** repeatable provisioning, role/isolation evidence, report/checkpoint updated.

## 11. Wave 10 — Frontend DB-access retirement and integrated E2E

Inventory remaining `lib/db.ts` and other direct DB consumers; migrate only where equivalent APIs exist. Retire direct access after consumers/tests verified. Remove/hard-disable dev impersonation/test sessions from deployable builds; test gateway rejection in production configuration without connecting to production. Run browser regression by role across student, timetable, attendance, corrections, dashboards and onboarding, including request/response, read-back, cross-tenant denial, role authorization and signed context. Verify event contracts against real Intelligence Layer endpoint if reachable, otherwise test double and pending status.

**Exit:** no unintended direct frontend domain-data access; E2E evidence and remaining gaps documented.

## 12. Wave 11 — Production readiness gate

Assessment/preparation only; production connection/cutover needs separate explicit authorization. Assess JDK/framework; CI test tiers; secrets/key rotation, rate limits/request bounds; structured logs/correlation IDs, metrics, health, outbox lag/failure alerts and replay; performance against Wave 8 budget; backup/rollback/cutover plans and runbooks. Document any migration path from separate MVP environment as a plan only. Produce go/no-go checklist with pending Intelligence Layer work.

**Exit:** reviewed go/no-go report, explicit open risks/pending integrations and rollback plan. No cutover implied.

## 13. Required evidence in every report

For each workflow: E2E path and exact test tier; DB read-back if available; tenant isolation and G1 status; event types/owner/schema/privacy and delivery tier; Intelligence Layer duplicate/failure/retry tests and status; frontend consumers migrated/remaining; exact files, commands, test counts/skips/failures; G1–G3 status; latency where relevant; blockers and guardrail confirmation. State what was not tested.

## 14. Definition of done

Approved scope implemented; actual tests/results recorded; claims match evidence; tenant/authorization evidence without overclaiming RLS; `PROGRESS.md` updated; `docs/reports/wave-N/COMPLETION_REPORT.md` updated; `NEXT_ANTIGRAVITY_TASK.md` derived from evidence; no unauthorized DB action or commit/push; stop after current task.

## 15. Immediate next task

Wave 4. Check current state and G1 evidence without repeating broad audits. Continue safe design/code/unit tests if G1 is open, but no persistence requiring a DB connection or DB writes until target and approval requirements are satisfied. Obtain written student-model approval before implementing that model.
