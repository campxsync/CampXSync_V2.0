# CampXSync Repository Agent Instructions

## Mandatory preflight
Before any task, read:
1. `.agent/rules/campxsync-wave-control.md`
2. `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`
3. `docs/project-control/PROGRESS.md`
4. `prompts/NEXT_ANTIGRAVITY_TASK.md`
5. The latest relevant report under `docs/reports/`

Follow the wave sequence and safety rules. Execute one bounded task at a time. Do not repeat completed work without a concrete reason, skip waves, or start the next task unless the current prompt explicitly authorizes it.

## Mandatory architecture
- The required transactional path is **UI → shared/versioned API Gateway → Java domain services → Supabase/PostgreSQL**.
- Verify authorization, committed persistence/read-back, and tenant isolation; unit tests alone are not end-to-end proof.
- Relevant Java service outputs must be shared with the CampXSync Intelligence Layer through documented, versioned contracts.
- Every wave report must cover both the transactional path and Intelligence Layer integration, including test evidence and gaps.
- Preserve tenant context, least privilege, data minimization, correlation, idempotency, and appropriate retry/failure handling.
- Reuse existing Shared Services/audit-outbox contracts when suitable. The Intelligence Layer must not bypass gateway authorization, Java business rules, tenant isolation, or PostgreSQL as the transactional source of truth.

## Safety and repository discipline
- Preserve current branch, working tree, edits, and untracked files.
- No reset, branch switch, stash, commit, push, cleanup, or overwriting existing work without explicit authorization.
- Never read root `.env.local` or `frontend/.env.local`.
- Never connect to or modify production Supabase ref `ekspahbsbwdpraaqfqui` or previous staging ref `kpwdvvcschankgggitnj`.
- The agreed development ref is `azulbaxszsqxymqmlwfe`; use only when necessary and authorized.
- No database writes, migrations, schema changes, role/grant changes, or RLS changes without explicit approval for that specific step.
- Never silently fall back to in-memory identity or mock authorization in production-like execution.
- Do not introduce unapproved framework upgrades or unrelated scope.
- If a task requires an approval-gated operation, stop and explain the exact blocker. Never bypass safety controls.

## Required task and wave closeout
For each task, report exact changed files, commands/results, skipped/not-run tests, evidence type (mock/in-memory, unit, DB integration, browser/E2E), tenant-isolation evidence, Intelligence Layer contract evidence, blockers, and safety compliance.

At each wave end:
1. Create `docs/reports/wave-N/COMPLETION_REPORT.md` using `docs/reports/WAVE_COMPLETION_REPORT_TEMPLATE.md`.
2. Update `docs/project-control/PROGRESS.md`.
3. Analyze the report and actual diffs/test logs.
4. Prepare `prompts/NEXT_ANTIGRAVITY_TASK.md` for the next task.
5. Stop; do not begin the next wave automatically.

Never claim a test or change is verified without actual evidence.
