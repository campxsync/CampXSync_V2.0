# CampXSync — Wave Implementation Plan (Waves 4–11, revised)

**Project:** CampXSync V2.0 — Java Backend Integration
**Target branch:** `feature/java-backend-integration`
**Last updated:** 2026-10-21
**Applies to:** Wave 4 onward. Waves 0–3 and the existing Operating Rules are unchanged. Paste this over the old Wave 4–10 sections of `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` and replace the cross-cutting "Required evidence" block and "Definition of done" with the versions below.

## What changed and why

| Old | New | Reason |
|---|---|---|
| Wave 4 Student | Wave 4 Student | Unchanged order; now blocked on Gate G1 |
| Wave 5 Timetable | Wave 5 Timetable | Unchanged; now also publishes stable slot IDs for attendance |
| Wave 6 Dashboards | **Wave 8** Dashboards | Dashboards aggregate attendance, so they come after attendance and corrections |
| Wave 7 Attendance | **Wave 6** Attendance | Moved up |
| Wave 8 Corrections | **Wave 7** Corrections | Moved up |
| Wave 9 Onboarding | Wave 9 Onboarding | Unchanged order |
| Wave 10 Retirement and E2E | Wave 10 Retirement and E2E | Now a regression and retirement wave, since thin E2E runs in every wave |
| (none) | **Wave 11** Production readiness gate | New; JDK assessment, CI, observability, rollback, latency |

## Gates (must be closed before the wave that depends on them)

- **G1 — Least-privilege DB role (before any Wave 4 persistence).** Business DB access must use a dedicated app login role without BYPASSRLS, with transaction-scoped tenant/user context. A privileged system role may exist but must be narrowly scoped. Until G1 is closed and verified, **no wave may claim tenant isolation is verified**, because a `postgres` connection bypasses RLS. Role/grant scripts need his review and explicit approval before any execution. If G1 is already closed, Wave 4 starts by recording the evidence; if not, G1 is Wave 4 Task 0.
- **G2 — Automated tests in CI (by end of Wave 5).** The isolated-PostgreSQL integration suite (bootstrapped from existing migrations) runs automatically on every push to the feature branch. The dev Supabase project is used only for approved verification steps.
- **G3 — JDK/framework upgrade assessment (decision before Wave 11 exit).** Java 8 remains in place; no framework upgrade without approval. Assessment only: supported-JDK target, effort, risks, recommendation.

## Replacement cross-cutting rules for Waves 4–11

These replace the "E2E later" ambiguity and keep the Intelligence Layer requirement lighter per wave.

1. **Thin E2E in every wave.** Each wave's workflow is verified through `UI/Frontend → shared versioned API Gateway → Java domain service → PostgreSQL` with committed read-back and a cross-tenant denial, not left to Wave 10. Test type (mock / in-memory / DB / browser) is always stated. Compilation and unit tests are not E2E proof.
2. **Frontend cutover per wave.** Each wave that introduces an API also migrates its own frontend consumers off direct DB access, or records exactly which `lib/db.ts` consumers remain and why. Wave 10 retires what is left; it does not start the migration.
3. **Intelligence Layer: register, don't reinvent.** Use the event envelope and outbox contract from Wave 1 (correlation ID, tenant context, event time, entity IDs, schema version). Each wave only registers its own event types, owner, schema version and privacy classification, and tests: publication, tenant context preserved, duplicate delivery (idempotency), failure and retry. Use a controlled test double where the layer is unreachable and record the integration as **pending**, never as complete.
4. **Events never replace transactions.** Domain state is authoritative in PostgreSQL. Events are written via the transaction-aware outbox, and command success is reported separately from downstream delivery.
5. **No unapproved DB changes.** Every migration, grant, RLS change, seed or provisioning step needs explicit approval for that exact step. Preserve working tree, branch and untracked files as per Operating Rules.

## Wave 4 — Student domain and roster
**Goal:** Make the student a first-class core module with stable identity and lifecycle that all downstream modules reference.
**Preconditions:** Gate G1 closed or run as Task 0. Student design (from the separate design thread) approved in writing before any implementation. Wave 3 department/program structures verified.
**Scope:**
- Document and approve the model first: student/person linkage, institution-scoped identifiers (admission/enrolment/roll numbers), lifecycle states and allowed transitions (e.g. admitted → active → on hold → graduated/withdrawn), and relationships to program, department and batch. Confirm the academic-year/term/batch model that timetable and attendance will use; if Wave 3 did not define it, define it here.
- Student service is the sole writer of student data; other modules hold references only.
- Roster paths: list/search with cursor pagination, enrol/transfer/withdraw within batch, and idempotent bulk import with a per-row validation report.
- Authorization by role and department scope; trusted identity only; data minimization for personal data in responses and events.
- Register events (e.g. student created, status changed, enrolled/transferred) with owner, versioning and privacy classification.
**Tests:** duplicate identifiers within and across tenants, invalid lifecycle transitions, references to out-of-tenant or out-of-scope programs/departments, bulk-import idempotency, persisted read-back, cross-tenant denial, role denial.
**Exit criteria:** Model and ownership documented and approved; roster and lifecycle persisted and tested through the gateway; stable student/batch references published for timetable and attendance; event contracts registered and tested; report and checkpoint updated.

## Wave 5 — Timetable
**Goal:** Timetable built on verified academic structures, producing stable slots that attendance sessions derive from.
**Scope:**
- Agree and document the conflict rules before implementation: faculty double-booking, batch double-booking, and room double-booking if rooms are in scope.
- Persist timetable entries with validated tenant, program, batch, faculty and subject/course references; define effective dates/versioning for mid-term changes; handle time in a single stated timezone convention (IST).
- Enforce conflict rules atomically under concurrent writes (service-level locking, or DB constraints only if separately approved).
- Faculty-to-slot assignment and substitution representation (needed by Wave 6).
- Publish stable slot identifiers and a slot-changed event for attendance.
**Tests:** each conflict rule including concurrent attempts, invalid references, department-scope authorization, tenant separation, persisted read-back.
**Exit criteria:** Gateway → Java → PostgreSQL workflow and conflict rules verified; slot contract documented; Gate G2 closed; report and checkpoint updated.

## Wave 6 — Attendance
**Goal:** Secure attendance session creation, marking and oversight.
**Scope:**
- Session model derived from timetable slots; define marking window and who may open/mark (assigned faculty or recorded substitute only).
- Agreed attendance status set and a roster snapshot at session time, with a defined rule for students whose lifecycle status changes mid-term.
- Idempotent single and bulk marking; duplicate and invalid-roster rejection.
- Oversight read paths for HoD and college admin with department/tenant scope and cursor pagination.
- Register events (session opened, attendance marked) with privacy classification.
**Tests:** students not on the roster, duplicate submissions, marking outside window or outside assignment, department-scope oversight, read-back, cross-tenant and unauthorized attempts denied.
**Exit criteria:** End-to-end marking and oversight verified via gateway; persisted read-back proven; unauthorized and cross-tenant attempts denied; event contracts tested; report and checkpoint updated.

## Wave 7 — Attendance corrections
**Goal:** Controlled, fully traceable amendments.
**Scope:**
- Request → approval → apply flow with defined authority per role; requester cannot approve their own request; time limit for corrections is an agreed rule.
- Append-only history: preserve original value, corrected value, actor, reason and timestamps; never overwrite originals.
- Concurrency and idempotency controls on apply; audit through the shared outbox.
- Register a correction event so downstream aggregates can recompute.
**Tests:** authority matrix, self-approval denied, concurrent apply, replay of the same apply, history integrity, tenant isolation, read-back of both original and corrected values.
**Exit criteria:** Correction flow and audit trail verified end-to-end; unauthorized changes denied; report and checkpoint updated.

## Wave 8 — Dashboards
**Goal:** Role-specific read models over authoritative persisted data, built after student, timetable, attendance and corrections exist.
**Scope:**
- Write a metric definitions document first: what counts as present, denominators, handling of corrected records, withdrawn students, and date ranges.
- Define aggregates per role (platform admin, college admin, HoD, faculty, and student view only if approved in scope).
- Served through gateway APIs from Java services, never ad hoc frontend queries. Any materialized view, index or other DB object needs separate approval.
- Agree a latency budget (measured p50/p95 per role) given earlier login-latency problems, and record measurements against it.
- Dashboards consume data only; they must not become a second source of truth.
**Tests:** every dashboard value reconciles to fixture records (including post-correction), cross-tenant and out-of-scope leakage tests per role, latency measurements, tenant isolation across at least two tenants.
**Exit criteria:** Counts reconcile to stored data; role and tenant boundaries tested; latency within the agreed budget or deviations recorded; report and checkpoint updated.

## Wave 9 — College onboarding
**Goal:** Safe, repeatable tenant onboarding.
**Scope:**
- Inspect the existing provisioning contract; implement idempotent onboarding (re-running does not duplicate tenants, roles or admins) and initial admin assignment.
- Restrict to platform-admin authority; prevent tenant and role escalation.
- Initial admin creation through Supabase Auth must use narrowly scoped privileged credentials held only server-side, never exposed to the frontend.
- Define compensation or rollback behavior for partial failures.
- Register tenant-provisioned event with the standard envelope.
- All DB writes and provisioning in the approved non-production environment only, each with explicit approval.
**Tests:** repeated onboarding, partial-failure recovery, escalation attempts, and a third tenant onboarded then run through the thin E2E suites to prove isolation across three tenants.
**Exit criteria:** Repeatable and idempotent onboarding; initial role and isolation verified; report and checkpoint updated.

## Wave 10 — Frontend DB-access retirement and integrated E2E regression
**Goal:** Remove remaining direct DB access from the frontend and prove all integrated workflows together.
**Scope:**
- Inventory all remaining `lib/db.ts` and other direct DB consumers carried forward from earlier waves; migrate only where equivalent APIs exist; retire direct access only after all consumers and tests are verified.
- Remove or hard-disable dev impersonation and test sessions from deployable builds; confirm the gateway rejects them in production configuration.
- Run the full browser-level regression per role across students, timetable, attendance, corrections, dashboards and onboarding: request/response, persisted read-back, cross-tenant denial, role authorization, signed downstream context.
- Verify Shared Services contracts and publication of all registered event types to the Intelligence Layer, via a real endpoint if reachable or a test double, with pending items recorded.
**Exit criteria:** No unintended direct frontend domain-data access; E2E evidence includes request/response/read-back and cross-tenant denial; gaps and rollback considerations documented.

## Wave 11 — Production readiness gate
**Goal:** Decide whether the platform is ready for a production release. This wave is assessment and preparation only; any production connection or cutover needs a separate explicit authorization.
**Scope:**
- Gate G3: JDK/framework upgrade assessment and recommendation.
- CI/CD running every test tier; documented secret handling, key rotation for JWKS pinning and HMAC downstream signing, rate limiting and request bounds review.
- Observability: structured logs with correlation IDs, metrics, health endpoints, outbox lag and failure alerting, replay procedure.
- Performance results against the Wave 8 latency budget under realistic data volume.
- Backup, rollback and cutover plan; runbooks; any migration path from the separate MVP environment documented as a plan only, never touching that environment without approval.
- Final go/no-go checklist listing every pending item (including Intelligence Layer items not yet verified against a real endpoint).
**Exit criteria:** Go/no-go report approved; open risks and pending integrations explicitly listed; rollback plan documented.

## Required evidence in every wave report (replaces the earlier block)
For each workflow in scope, state:
1. Thin E2E path (UI → gateway → Java → PostgreSQL) implemented and tested, with test type.
2. Committed data read back from PostgreSQL, tenant isolation tested, and Gate G1 status.
3. Events registered (types, envelope/schema version, owner) and how they reach the Intelligence Layer.
4. Intelligence Layer contract tested against a real endpoint or a test double; duplicate and failure behavior covered; status complete or pending.
5. Frontend consumers migrated or remaining, plus any delivery, privacy, authorization or integration gap.

## Definition of done for every wave (replaces the earlier block)
Report exact files changed, behavior added, commands and test counts, test type (mock/in-memory/DB/browser), skipped tests, tenant-isolation evidence, gate status (G1–G3), latency numbers where relevant, blockers, guardrail confirmation and next task. Update `docs/project-control/PROGRESS.md`, write `docs/reports/wave-N/COMPLETION_REPORT.md`, derive `prompts/NEXT_ANTIGRAVITY_TASK.md` from the evidence, and stop after the current task.
