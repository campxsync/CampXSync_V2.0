# CampXSync Wave Control — Authoritative Operating Rules

## Authority
This governs Java Backend Integration Waves 0–11. Use `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, `docs/project-control/PROGRESS.md` and `prompts/NEXT_ANTIGRAVITY_TASK.md` to establish the current task. Execute one wave at a time. The MVP workstream and `MASTER_EXECUTION_PROMPT.md` are separate; do not use that file as authority for Java backend waves.

## Preserve repository state
Preserve branch, HEAD, modified files and untracked files. Never reset, clean, restore, stash, switch branches, commit or push without explicit authorization. Do not overwrite completed work or repeat broad audits without reason. Inspect existing migrations/source before proposing new tables.

## Database and production safety
Production project references/databases are strictly off-limits. Before shared non-production persistence, verify G1 and the exact target. Business DB access must use a dedicated app login role without `BYPASSRLS`, with transaction-scoped tenant/user context. A privileged role cannot prove RLS isolation. Every migration, grant, RLS change, seed, provisioning step or other DB write requires explicit approval for that exact step after target verification. If a gate is open, continue safe code/design/unit tests that do not connect to shared DB. Never expose secrets/tokens/credentials.

## Tenant and identity
Trust only authenticated gateway identity and verified downstream context; reject spoofed tenant/user headers. Enforce tenant, role and department scope. Do not claim tenant isolation verified until the correct DB role/RLS path has been exercised. Reuse canonical domain owners and shared services; no duplicate sources of truth or credentials.

## Cross-cutting requirements
- Each wave verifies the strongest safe thin E2E tier available: frontend/UI → shared versioned Gateway → Java service → PostgreSQL. State the exact tier; missing full-path evidence remains pending.
- Migrate each wave's frontend consumers away from direct DB access when an equivalent API arrives; list exact remaining consumers and rationale.
- Reuse Wave 1 outbox/event envelope: correlation ID, tenant context, event time, entity IDs, schema version. Register owner/type/version/privacy classification. Test tenant context, idempotency, failure and retry. Test doubles do not prove live Intelligence Layer integration.
- PostgreSQL remains authoritative. Events do not replace transactions; command success is distinct from downstream delivery.
- Compilation/unit tests alone do not complete a wave.

## Gates
- **G1:** verified least-privilege app DB role without `BYPASSRLS`, transaction-scoped tenant/user context and correct non-production target. Required before shared DB writes/persistence and before claiming tenant isolation.
- **G2:** isolated PostgreSQL integration suite bootstrapped from existing migrations runs automatically in CI by end of Wave 5. No push without authorization.
- **G3:** JDK/framework support assessment and recommendation before Wave 11 exit. Java 8/framework upgrades require separate approval.

## Wave order
W0 baseline → W1 Shared Audit & Outbox → W2 Preferences → W3 Departments & Programs → W4 Student Domain & Roster → W5 Timetable → W6 Attendance → W7 Attendance Corrections → W8 Dashboards → W9 College Onboarding → W10 Frontend DB-access Retirement & Integrated E2E → W11 Production Readiness.

## Evidence and completion
Reports include exact files, commands/results, test counts/skips/failures, test tier, read-back where available, tenant/role/department denial evidence, G1–G3, event/Intelligence status, frontend consumers, latency where relevant, blockers and guardrail confirmation. State what was not tested. Update progress/report/next task from evidence. Stop after current task. No unauthorized DB operation or commit/push.
