# CampXSync — Wave Implementation Plan

**Project:** CampXSync V2.0 — Java Backend Integration  
**Target branch:** `feature/java-backend-integration`  
**Last updated:** 2026-10-20  
**Status:** Working plan reconstructed from the agreed wave sequence in current project context. Before changing order/scope, reconcile with the canonical Claude plan if it exists in the repository.

## Operating rules
- Read this plan and `docs/project-control/PROGRESS.md` before each task.
- Preserve branch, working tree, local edits, and untracked files. No reset, branch switch, commit, push, stash, or overwrite without explicit authorization.
- Never read root `.env.local` or `frontend/.env.local`.
- Never connect to or modify production Supabase ref `ekspahbsbwdpraaqfqui` or previous staging ref `kpwdvvcschankgggitnj`.
- Agreed dev project: `CampXSync-Architecture-Dev`, ref `azulbaxszsqxymqmlwfe`; use only if necessary and authorized.
- No DB writes, migrations, schema, role/grant, or RLS changes without explicit approval for that exact step.
- Fail closed on authentication/authorization ambiguity. Caller headers and JWT root `role` are not trusted CampXSync business identity.
- Do not upgrade frameworks or expand scope without approval.
- Unit tests do not replace E2E verification.
- Required eventual E2E: UI → shared/versioned API Gateway → Java domain services → Supabase/PostgreSQL, proving persisted data and tenant isolation. Test Java services' Shared Services contracts too.
- At each wave end, produce a completion report, update progress, and derive the next Antigravity prompt from evidence. Stop after the current task.

## Mandatory cross-cutting architecture: end-to-end data flow and Intelligence Layer

This requirement applies to **every wave and every relevant feature**, not only the final integration wave.

### Required operational path
`UI / Frontend → Shared, Versioned API Gateway → Java Domain Services → Supabase / PostgreSQL`

The flow must be implemented and verified as a real user-visible workflow. The Java domain services enforce business rules and use the approved persistence path; PostgreSQL remains the authoritative persisted source of truth. End-to-end tests must verify the request, authorization, service behavior, committed persistence, and read-back, including tenant isolation.

### Intelligence Layer integration
Outputs produced by Java domain services must also be made available to the CampXSync Intelligence Layer through explicit, versioned, documented contracts. This is a required parallel integration path across the platform, not a future optional enhancement.

- Define per-service output/event contracts, ownership, schema/versioning, correlation IDs, tenant context, event time, and relevant entity identifiers.
- Prefer the established Shared Services / audit-outbox mechanism for durable, retryable publication when asynchronous delivery is appropriate. Do not invent a second event mechanism if an existing contract fits.
- Distinguish command/transaction success from downstream intelligence delivery. Avoid losing events when a database transaction commits; use transaction-aware outbox semantics where the existing design supports them.
- Make delivery idempotent and observable; document retries, failures, schema evolution, and replay behavior.
- Pass only authorized, purpose-appropriate data. Enforce tenant boundaries, least privilege, data minimization, and auditability. Do not expose credentials, tokens, secrets, or unnecessary personal data to the Intelligence Layer.
- The Intelligence Layer must not bypass the gateway, Java domain authorization, tenant isolation, or PostgreSQL controls; it must not become an alternative source of truth for transactional domain state.
- Tests must verify that relevant Java service outputs are published/available to the Intelligence Layer contract, with tenant context preserved and duplicate/failure behavior covered.
- Where the Intelligence Layer is not yet implemented or reachable, define the contract and test publication using a controlled adapter/test double; record the integration as pending rather than claiming full end-to-end completion.

### Required evidence in every wave report
For each workflow in scope, state:
1. Whether UI → gateway → Java service → PostgreSQL is implemented and tested.
2. Whether committed data was read back from PostgreSQL and tenant isolation was tested.
3. What output the Java service produces and how it is shared with the Intelligence Layer.
4. Whether the Intelligence Layer contract was tested with a real endpoint or a test double.
5. Any delivery, privacy, authorization, or integration gap.

## Wave 0 — Security foundation and gateway trust boundary
**Goal:** Establish authentication and trusted identity context before business workflows.
**Scope:** Keep/harden existing Java API Gateway; retain Supabase Auth and ES256/JWKS validation; pin issuer to approved dev project and audience `authenticated`; remove public route discovery; resolve verified JWT `sub` to trusted current profile, tenant membership, status, roles/scopes; add stable `GET /api/v1/auth/me` with `Cache-Control: no-store`; strip/replace caller-supplied identity headers; harden downstream HMAC context and replay/timestamp handling; ensure downstream verification/fail-closed behavior; review transaction-local RLS context and DB least privilege without changing DB grants/RLS absent approval; explicitly restrict in-memory resolver to tests; add bounded auth/JWKS and request-size protections.
**Exit criteria:** No route inventory disclosure; JWT remains fail-closed; `/auth/me` binds to verified subject and trusted records; inactive/ambiguous identities denied; spoofed headers ineffective; no production fallback to in-memory; role/scopes follow verified model; gateway tests pass; DB-dependent unverified items are disclosed; report/checkpoint updated.
**Current:** Task 1 reported complete. Task 2 implemented per report; bounded security verification/correction remains.

## Wave 1 — Shared audit and outbox foundation
**Goal:** Consistent, tenant-aware audit and reliable outbox contracts.
**Scope:** Inspect existing common/security libraries, audit schema and `sys.outbox_events`; reuse contracts; define transaction-aware outbox writes, correlation/event metadata, idempotency, retries and failure behavior; prevent sensitive-data leakage; test tenant attribution and transaction semantics. No unapproved DB changes.
**Exit criteria:** Contracts and ownership documented; transactional behavior, idempotency/failure behavior tested; no unnecessary duplicate implementations; report/checkpoint updated.

## Wave 2 — Preferences
**Goal:** Implement a bounded preferences slice through trusted gateway identity.
**Scope:** Inspect existing schema/API/UI consumers; define preference contract and ownership; use trusted identity, not caller-supplied user/tenant; persist through approved Java path and RLS; test ownership, tenant isolation and persistence.
**Exit criteria:** Contract documented, ownership and isolation verified, UI/API path tested, report/checkpoint updated.

## Wave 3 — Academic departments and programs
**Goal:** Establish structures required by student, timetable and attendance workflows.
**Scope:** Implement/verify department and program workflows; enforce tenant and department scopes, referential integrity and persistence; avoid direct frontend DB access and duplicate implementations.
**Exit criteria:** Agreed workflow verified end-to-end; scope boundaries tested; dependencies documented.

## Wave 4 — Student domain and roster
**Goal:** Make student identity/lifecycle a first-class domain.
**Scope:** Define student/person linkage, identifiers, admission/lifecycle status, program/department/batch relationships and ownership; establish student-service persistence/contracts before dependent workflows; implement roster path; test duplicate IDs, lifecycle, references and tenant isolation.
**Exit criteria:** Model/ownership documented; roster/lifecycle persisted and tested; stable references for downstream modules.

## Wave 5 — Timetable
**Goal:** Timetable workflow built on verified academic structures.
**Scope:** Persist timetable and validate tenant, program, batch, faculty and subject references; enforce agreed conflict rules; test authorization and tenant separation.
**Exit criteria:** Gateway-to-service-to-DB workflow and conflict rules verified; report/checkpoint updated.

## Wave 6 — Dashboards
**Goal:** Role-specific read models based on authoritative persisted data.
**Scope:** Define role-specific aggregates; prevent cross-tenant/out-of-scope leakage; use APIs instead of ad hoc direct frontend queries; reconcile dashboard values to fixture records.
**Exit criteria:** Counts reconcile to stored data; role/tenant boundaries tested; report/checkpoint updated.

## Wave 7 — Attendance
**Goal:** Secure attendance marking and oversight.
**Scope:** Implement session/record persistence and faculty marking; enforce class/timetable/department scope; test invalid roster membership, duplicates, authorization, read-back and tenant isolation.
**Exit criteria:** End-to-end marking/oversight verified; persisted read-back proven; unauthorized/cross-tenant attempts denied.

## Wave 8 — Attendance corrections
**Goal:** Controlled amendments with traceability.
**Scope:** Define request/approval/apply authority; preserve original/corrected values, actor, reason and time; add concurrency/idempotency controls where needed; test authorization and tenant isolation.
**Exit criteria:** Correction flow and audit trail verified; unauthorized changes denied.

## Wave 9 — College onboarding
**Goal:** Safe and repeatable tenant onboarding.
**Scope:** Inspect existing provisioning contract; implement idempotent onboarding and initial admin assignment; prevent tenant/role escalation; test in approved non-production environment. Any DB write/provisioning needs explicit approval.
**Exit criteria:** Repeatable/idempotent onboarding; initial role and isolation verified; report/checkpoint updated.

## Wave 10 — Frontend DB-access retirement and integrated E2E
**Goal:** Migrate domain access to gateway/Java services and prove integrated workflows.
**Scope:** Inventory `lib/db.ts` and other direct DB consumers; migrate only when equivalent APIs exist; retire direct access after all consumers and tests are verified; test UI → gateway → Java services → PostgreSQL, persisted read-back, tenant isolation, role authorization, signed downstream context, Shared Services contracts, and Java service output publication to the Intelligence Layer.
**Exit criteria:** No unintended direct frontend domain-data access; E2E evidence includes request/response/read-back and cross-tenant denial; gaps and rollback considerations documented.

## Definition of done for every wave
Report exact files, behavior, commands/test counts, test type (mock/in-memory/DB/browser), skipped tests, tenant-isolation evidence, blockers, guardrail confirmation and next task. Compilation alone is not E2E proof.

## Control files
- `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` — this plan.
- `docs/project-control/PROGRESS.md` — current checkpoint.
- `docs/reports/wave-N/COMPLETION_REPORT.md` — each wave's report.
- `prompts/NEXT_ANTIGRAVITY_TASK.md` — current bounded execution prompt.
